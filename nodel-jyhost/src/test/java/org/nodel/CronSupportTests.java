package org.nodel;

import com.microsoft.playwright.APIResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration coverage for the managed CRON scheduling support (issue #411):
 * the Jython toolkit API ('Cron' class and 'cron_*' helpers), the per-node
 * 'REST/cron' endpoint, live scheduling through the shared timer infrastructure,
 * and schedule re-registration across a node restart.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CronSupportTests extends TestBase {

    private static final String TOOLKIT_NODE = "Cron Toolkit Test";

    private static final String FIRE_NODE = "Cron Fire Test";

    /**
     * Logs one line per toolkit surface so each can be asserted independently.
     */
    private static final String TOOLKIT_SCRIPT =
        "held = Cron(lambda: console.info('held cron fired'), '30 8 * * MON-FRI', timezone='Australia/Melbourne', stopped=True)\n" +
        "\n" +
        "def main():\n" +
        "    console.info('Cron toolkit test started')\n" +
        "    console.info('is-valid: %s' % cron_is_valid('30 8 * * MON-FRI'))\n" +
        "    console.info('validate-ok-is-none: %s' % (cron_validate('*/5 * * * *') is None))\n" +
        "    console.info('validate-bad: %s' % cron_validate('61 * * * *'))\n" +
        "    console.info('describe: %s' % cron_describe('30 8 * * MON-FRI'))\n" +
        "    console.info('next-in-future: %s' % (cron_next('* * * * *').isAfterNow()))\n" +
        "    console.info('previous-in-past: %s' % (cron_previous('* * * * *').isBeforeNow()))\n" +
        "    console.info('held-description: %s' % held.getDescription())\n" +
        "    console.info('held-timezone: %s' % held.getTimezone())\n" +
        "    console.info('held-stopped: %s' % held.isStopped())\n" +
        "    held.start()\n" +
        "    console.info('held-started: %s' % held.isStarted())\n" +
        "    console.info('held-next-is-set: %s' % (held.getNextExecution() is not None))\n" +
        "    held.setExpression('0 22 * * SUN')\n" +
        "    console.info('held-reconfigured: %s' % held.getExpression())\n" +
        "    try:\n" +
        "        held.setExpression('not a cron')\n" +
        "        console.info('held-bad-reconfigure: accepted')\n" +
        "    except:\n" +
        "        console.info('held-bad-reconfigure: rejected, kept %s' % held.getExpression())\n" +
        "    try:\n" +
        "        Cron(lambda: None, '61 * * * *')\n" +
        "        console.info('bad-expression: accepted')\n" +
        "    except:\n" +
        "        console.info('bad-expression: rejected')\n";

    /**
     * An every-minute schedule proves a real fire end-to-end (worst case ~60 s wait).
     */
    private static final String FIRE_SCRIPT =
        "cron = Cron(lambda: console.info('CRON-FIRED next=%s' % cron.getNextExecution()), '* * * * *')\n" +
        "\n" +
        "def main():\n" +
        "    console.info('Cron fire test started')\n";

    @BeforeAll
    public static void setup() {
        initBrowser();
        assertTrue(createTestNode(TOOLKIT_NODE, TOOLKIT_SCRIPT), "toolkit test node should initialise");
        assertTrue(createTestNode(FIRE_NODE, FIRE_SCRIPT), "fire test node should initialise");
    }

    @AfterAll
    public static void teardown() {
        deleteTestNode(TOOLKIT_NODE);
        deleteTestNode(FIRE_NODE);
        closeBrowser();
    }

    // Jython toolkit API

    @Test
    @Order(1)
    public void testCronHelpersFromRecipe() {
        assertTrue(waitForConsoleContains(TOOLKIT_NODE, "is-valid: True", 10000),
                "cron_is_valid should accept a five-field expression");
        assertTrue(waitForConsoleContains(TOOLKIT_NODE, "validate-ok-is-none: True", 10000),
                "cron_validate should return None for a valid expression");
        assertTrue(waitForConsoleContains(TOOLKIT_NODE, "validate-bad: Invalid CRON expression", 10000),
                "cron_validate should return the failure reason");
        assertTrue(waitForConsoleContains(TOOLKIT_NODE, "describe: at 08:30 every day between Monday and Friday", 10000),
                "cron_describe should produce a human-readable description");
        assertTrue(waitForConsoleContains(TOOLKIT_NODE, "next-in-future: True", 10000),
                "cron_next should be in the future");
        assertTrue(waitForConsoleContains(TOOLKIT_NODE, "previous-in-past: True", 10000),
                "cron_previous should be in the past");
    }

    @Test
    @Order(2)
    public void testCronClassLifecycleFromRecipe() {
        assertTrue(waitForConsoleContains(TOOLKIT_NODE, "held-description: at 08:30 every day between Monday and Friday", 10000),
                "Cron.getDescription should describe the schedule");
        assertTrue(waitForConsoleContains(TOOLKIT_NODE, "held-timezone: Australia/Melbourne", 10000),
                "Cron.getTimezone should reflect the configured IANA timezone");
        assertTrue(waitForConsoleContains(TOOLKIT_NODE, "held-stopped: True", 10000),
                "stopped=True should hold the schedule");
        assertTrue(waitForConsoleContains(TOOLKIT_NODE, "held-started: True", 10000),
                "Cron.start should start a held schedule");
        assertTrue(waitForConsoleContains(TOOLKIT_NODE, "held-next-is-set: True", 10000),
                "a started schedule should expose its next execution");
        assertTrue(waitForConsoleContains(TOOLKIT_NODE, "held-reconfigured: 0 22 * * SUN", 10000),
                "Cron.setExpression should reconfigure in place");
        assertTrue(waitForConsoleContains(TOOLKIT_NODE, "held-bad-reconfigure: rejected, kept 0 22 * * SUN", 10000),
                "an invalid reconfiguration should raise and keep the previous expression");
        assertTrue(waitForConsoleContains(TOOLKIT_NODE, "bad-expression: rejected", 10000),
                "constructing with an invalid expression should raise immediately");
    }

    // per-node REST endpoint (used by the web UI and custom dashboards)

    @Test
    @Order(3)
    public void testCronRestEndpointValid() {
        APIResponse response = apiGet("/nodes/" + encode(TOOLKIT_NODE)
                + "/cron?expression=" + encode("0 9 * * 1-5") + "&timezone=Australia/Melbourne");
        assertEquals(200, response.status(), "REST/cron should respond");

        String body = response.text().replace(" ", "");
        assertTrue(body.contains("\"valid\":true"), "expression should validate: " + response.text());
        assertTrue(response.text().contains("at 09:00 every day between Monday and Friday"),
                "a description should be included: " + response.text());
        assertTrue(body.contains("\"timeZone\":\"Australia/Melbourne\""),
                "the effective timezone should be echoed: " + response.text());
        assertTrue(body.contains("\"next\":"), "the next execution should be included: " + response.text());
        assertTrue(body.contains("\"previous\":"), "the previous execution should be included: " + response.text());
        assertTrue(body.contains("\"upcoming\":"), "upcoming executions should be included: " + response.text());
    }

    @Test
    @Order(4)
    public void testCronRestEndpointInvalid() {
        APIResponse response = apiGet("/nodes/" + encode(TOOLKIT_NODE)
                + "/cron?expression=" + encode("61 * * * *"));
        assertEquals(200, response.status(), "REST/cron should respond even for invalid input");

        String body = response.text().replace(" ", "");
        assertTrue(body.contains("\"valid\":false"), "expression should be rejected: " + response.text());
        assertTrue(body.contains("\"error\":"), "the failure reason should be included: " + response.text());
    }

    @Test
    @Order(5)
    public void testCronRestEndpointSundayForms() {
        // Sunday as 0, 7 or a name resolves to the same schedule
        String nextFor0 = null;
        for (String dow : new String[] { "0", "7", "SUN" }) {
            APIResponse response = apiGet("/nodes/" + encode(TOOLKIT_NODE)
                    + "/cron?expression=" + encode("0 22 * * " + dow) + "&timezone=Australia/Melbourne");
            assertEquals(200, response.status());
            String body = response.text().replace(" ", "");
            assertTrue(body.contains("\"valid\":true"), "Sunday as '" + dow + "' should validate");

            String next = body.replaceAll(".*\"next\":\"([^\"]+)\".*", "$1");
            if (nextFor0 == null)
                nextFor0 = next;
            else
                assertEquals(nextFor0, next, "Sunday as '" + dow + "' should schedule identically");
        }
    }

    // live scheduling through the shared timer infrastructure

    @Test
    @Order(6)
    public void testCronFiresOnTheMinute() {
        // an every-minute schedule must fire within ~60 s (plus margin for a busy host)
        assertTrue(waitForConsoleContains(FIRE_NODE, "CRON-FIRED", 75000),
                "a started '* * * * *' schedule should fire at the next minute boundary");
    }

    @Test
    @Order(7)
    public void testCronSurvivesNodeRestart() {
        // restarting the node tears the toolkit down and re-runs the recipe: the schedule
        // must be re-registered and look forward only. The console survives a script
        // restart, so count fires rather than just checking presence.
        int firesBeforeRestart = countInConsole(FIRE_NODE, "CRON-FIRED");
        int startsBeforeRestart = countInConsole(FIRE_NODE, "Cron fire test started");

        APIResponse restart = apiPost("/nodes/" + encode(FIRE_NODE) + "/restart");
        assertEquals(200, restart.status(), "node restart should be accepted");

        assertTrue(waitForNodeResponsive(FIRE_NODE, 30000), "node should come back after restart");

        String rerun = pollUntil(() -> countInConsole(FIRE_NODE, "Cron fire test started") > startsBeforeRestart,
                30000, 500, "the recipe to re-run after restart");
        assertTrue(rerun == null, String.valueOf(rerun));

        String refire = pollUntil(() -> countInConsole(FIRE_NODE, "CRON-FIRED") > firesBeforeRestart,
                75000, 500, "the re-registered schedule to fire after restart");
        assertTrue(refire == null, String.valueOf(refire));
    }

    /**
     * Occurrences of a substring across the node's recent console output.
     */
    private static int countInConsole(String nodeName, String text) {
        APIResponse response = apiGet("/nodes/" + encode(nodeName) + "/console?from=0&max=1000");
        if (response.status() != 200)
            return 0;

        String body = response.text();
        int count = 0;
        for (int at = body.indexOf(text); at >= 0; at = body.indexOf(text, at + text.length()))
            count++;

        return count;
    }

}
