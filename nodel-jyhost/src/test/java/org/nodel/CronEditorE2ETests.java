package org.nodel;

import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Real-browser coverage for the restored 'format: "cron"' editor (issues #411 / #196):
 * a parameter declared with the cron format renders as a text input with live,
 * host-backed validation and schedule feedback — both in the technical node UI and
 * on a custom dashboard (the shared path Frontend/Mk2-style nodes consume).
 */
@Tag("e2e")
public class CronEditorE2ETests extends TestBase {

    private static final String TECH_NODE = "Cron Editor Test";

    private static final String DASH_NODE = "Cron Dashboard Test";

    /**
     * (the schema shape the retired scheduler recipes used)
     */
    private static final String CRON_PARAM_SCRIPT =
        "param_schedule = Parameter({'title': 'Schedule', 'schema': {'type': 'string', 'format': 'cron',\n" +
        "    'title': 'Cron', 'desc': 'Format: <minute> <hour> <day> <month> <day of week>'}})\n" +
        "\n" +
        "def main():\n" +
        "    console.info('Cron editor test started')\n";

    /**
     * A minimal Frontend/Mk2-style dashboard: node-local content that pulls the shared
     * v1 assets and embeds the schema-driven parameters form.
     */
    private static final String DASHBOARD_XML =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
        "<?xml-stylesheet type=\"text/xsl\" href=\"v1/index.xsl\"?>\n" +
        "<pages title='Cron Dashboard Test' xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' xsi:noNamespaceSchemaLocation='v1/index.xsd'>\n" +
        "  <page title='Setup'>\n" +
        "    <row>\n" +
        "      <column sm='6'>\n" +
        "        <title>Parameters</title>\n" +
        "        <nodel type='params'/>\n" +
        "      </column>\n" +
        "    </row>\n" +
        "  </page>\n" +
        "</pages>\n";

    @BeforeAll
    public static void setup() {
        initBrowser();

        assumeTrue(createTestNode(TECH_NODE, CRON_PARAM_SCRIPT), "technical-UI test node must initialise");
        assumeTrue(createTestNode(DASH_NODE, CRON_PARAM_SCRIPT), "dashboard test node must initialise");

        APIResponse response = uploadNodeFile(DASH_NODE, "content/index.xml", DASHBOARD_XML);
        assertEquals(200, response.status(), "content/index.xml upload should return 200");
    }

    @AfterAll
    public static void teardown() {
        deleteTestNode(TECH_NODE);
        deleteTestNode(DASH_NODE);
        closeBrowser();
    }

    @Test
    public void testCronEditorInTechnicalUi() {
        recreatePage();
        navigateToNode(TECH_NODE);

        // the parameters form lives on the 'Config' page. The page-switch handler is
        // wired only once node data has loaded, so keep clicking until it takes effect.
        Locator configNav = page.locator("[data-nav=Config]").first();
        String failure = pollUntil(() -> {
            configNav.click();
            return page.locator("input.nodel-cron").first().isVisible();
        }, 30000, 500, "the 'Config' page to present the cron editor");
        assertTrue(failure == null, String.valueOf(failure));

        exerciseCronEditor();
    }

    @Test
    public void testSavedExpressionFeedbackShowsOnLoad() {
        // a saved expression must show its feedback (or its error) as soon as the form
        // is populated, without waiting for the user to touch the field
        APIResponse save = apiPost("/nodes/" + encode(TECH_NODE) + "/params/save",
                "{\"schedule\":\"0 17 * * FRI\"}");
        assertEquals(200, save.status(), "params/save should be accepted");

        recreatePage();
        navigateToNode(TECH_NODE);

        Locator configNav = page.locator("[data-nav=Config]").first();
        String failure = pollUntil(() -> {
            configNav.click();
            return page.locator("input.nodel-cron").first().isVisible();
        }, 30000, 500, "the 'Config' page to present the cron editor");
        assertTrue(failure == null, String.valueOf(failure));

        Locator feedback = page.locator(".nodel-cron-feedback").first();
        failure = pollUntil(() -> feedback.textContent().contains("at 17:00 at Friday day"),
                10000, 250, "load-time feedback for the saved expression");
        assertTrue(failure == null, String.valueOf(failure));
    }

    @Test
    public void testCronEditorOnCustomDashboard() {
        // the Frontend/Mk2-style path: node-local index.xml rendered through the shared
        // v1 pipeline; the same editor and host-backed feedback with no per-node code
        recreatePage();
        navigateToNode(DASH_NODE);

        exerciseCronEditor();
    }

    /**
     * Types valid and invalid expressions and asserts the live feedback both ways.
     */
    private void exerciseCronEditor() {
        waitForElement("input.nodel-cron");
        Locator input = page.locator("input.nodel-cron").first();
        Locator group = input.locator("xpath=ancestor::div[contains(@class,'form-group')][1]");
        Locator feedback = group.locator(".nodel-cron-feedback").first();

        // a valid expression shows the description and the next execution
        input.fill("0 9 * * 1-5");
        String failure = pollUntil(
                () -> feedback.textContent().contains("at 09:00 every day between Monday and Friday"),
                10000, 250, "valid-expression feedback");
        assertTrue(failure == null, String.valueOf(failure));
        assertTrue(feedback.textContent().contains("next:"), "the next execution should be shown");
        assertTrue(!group.getAttribute("class").contains("has-error"), "no error styling for a valid expression");

        // an invalid expression shows the reason with error styling
        input.fill("61 * * * *");
        failure = pollUntil(
                () -> feedback.textContent().contains("not in range")
                        && group.getAttribute("class").contains("has-error"),
                10000, 250, "invalid-expression feedback");
        assertTrue(failure == null, String.valueOf(failure));

        // clearing the field clears the feedback
        input.fill("");
        failure = pollUntil(
                () -> feedback.textContent().trim().isEmpty()
                        && !group.getAttribute("class").contains("has-error"),
                10000, 250, "feedback to clear");
        assertTrue(failure == null, String.valueOf(failure));
    }

}
