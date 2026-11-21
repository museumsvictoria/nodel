package org.nodel;

import com.microsoft.playwright.APIResponse;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for remote events/actions created at module level (the
 * '@remote_event' decorator or bare 'create_remote_event'/'create_remote_action'
 * calls), which run during the FIRST execution of a freshly discovered node's
 * script — before the node's config has been applied. This used to throw a
 * NullPointerException in BaseNode ('_config.remoteBindingValues' still null)
 * and abort the script load, taking everything declared after the creation
 * call with it. Only the first load was affected: a script re-save loads into
 * an instance whose config has been applied by then, so the failure appeared
 * only after a (node)host restart.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ModuleLevelRemoteBindingTests extends TestBase {

    private static final String TEST_NODE = "Module Level Remote Binding Test";

    // the 'started' console marker precedes the remote creations so node
    // discovery succeeds even if the load aborts there; 'MODULE_LEVEL_OK' only
    // appears if the whole script (including 'main') survived them
    private static final String SCRIPT =
        "console.info('Module-level binding node started')\n\n" +
        "def PduPower(arg):\n" +
        "    console.info('Power: %s' % arg)\n\n" +
        "create_remote_event('PduPower', PduPower, {'title': 'PDU power', 'group': 'Power'},\n" +
        "    suggestedNode='Some PDU', suggestedEvent='Output1')\n\n" +
        "create_remote_action('PduReboot', {'title': 'PDU reboot', 'group': 'Power'},\n" +
        "    suggestedNode='Some PDU', suggestedAction='Reboot')\n\n" +
        "@remote_event({'title': 'PDU power 2', 'group': 'Power'}, suggestedNode='Some PDU', suggestedEvent='Output2')\n" +
        "def PduPower2(arg):\n" +
        "    console.info('Power2: %s' % arg)\n\n" +
        "def main():\n" +
        "    console.info('MODULE_LEVEL_OK')\n";

    @BeforeAll
    public static void setup() {
        initBrowser();
        // assertTrue, not assumeTrue: failing to initialise on the FIRST load is
        // the regression itself (the NPE floods the console and discovery times
        // out), so it must fail the tests rather than skip them
        assertTrue(createTestNode(TEST_NODE, SCRIPT),
            "Test node must be created and reach a loaded state on its first load");
    }

    @AfterAll
    public static void teardown() {
        deleteTestNode(TEST_NODE);
        closeBrowser();
    }

    @Test
    @Order(1)
    public void testScriptLoadsThroughToMain() {
        assertTrue(waitForConsoleContains(TEST_NODE, "MODULE_LEVEL_OK", 10000),
            "'main' should run, proving the first script load survived the module-level remote creations");
    }

    @Test
    @Order(2)
    public void testNoLoadErrorsInConsole() {
        APIResponse response = apiGet("/nodes/" + encode(TEST_NODE) + "/console?from=0&max=100");
        assertEquals(200, response.status(), "Console endpoint should be available");

        String console = response.text();
        assertFalse(console.contains("NullPointerException"),
            "First load must not NPE on module-level remote creations");
        assertFalse(console.contains("loaded with errors"),
            "Script should load without errors");
    }

    @Test
    @Order(3)
    public void testRemoteBindingsCreatedWithSuggestions() {
        APIResponse response = apiGet("/nodes/" + encode(TEST_NODE) + "/remote");
        assertEquals(200, response.status(), "Remote bindings endpoint should be available");

        String remote = response.text();
        assertTrue(remote.contains("PduPower"), "Remote event from create_remote_event should exist");
        assertTrue(remote.contains("PduPower2"), "Remote event from @remote_event decorator should exist");
        assertTrue(remote.contains("PduReboot"), "Remote action from create_remote_action should exist");
        assertTrue(remote.contains("Some PDU"), "Suggested node should be pre-filled in the binding values");
    }
}
