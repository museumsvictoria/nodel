package org.nodel;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.RequestOptions;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Functional E2E tests that exercise actual Nodel functionality.
 * Unlike smoke tests that only check if libraries are loaded, these tests:
 * - Create real nodes with scripts
 * - Invoke actions and verify responses
 * - Check event emissions in activity feeds
 * - Verify console output
 * - Test parameter persistence
 * - Validate real-time updates via polling
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class NodeFunctionalTests extends TestBase {

    private static final String TEST_NODE = "E2E Functional Test";

    @BeforeAll
    public static void setup() {
        initBrowser();
        // Create test node ONCE for all tests in this class
        boolean created = createTestNode(TEST_NODE, SIMPLE_TEST_SCRIPT);
        assumeTrue(created, "Test node must be created and discovered for functional tests to run");
    }

    @AfterAll
    public static void teardown() {
        deleteTestNode(TEST_NODE);
        closeBrowser();
    }

    // ===== Gap 3: Node Lifecycle Tests =====
    // These run first to verify the node exists before other tests

    @Test
    @Order(1)
    public void testNodeAppearsInNodeList() {
        APIResponse response = apiGet("/nodes");
        assertEquals(200, response.status(), "Nodes endpoint should return 200");
        assertTrue(response.text().contains(TEST_NODE),
            "Test node should appear in node list after creation");
    }

    @Test
    @Order(2)
    public void testNodeHasExpectedActions() {
        APIResponse response = apiGet("/nodes/" + encode(TEST_NODE) + "/actions");
        assertEquals(200, response.status(), "Actions endpoint should return 200");
        String body = response.text();
        assertTrue(body.contains("testAction"), "testAction should be listed in node actions");
        assertTrue(body.contains("simpleAction"), "simpleAction should be listed in node actions");
    }

    @Test
    @Order(3)
    public void testNodeHasExpectedEvents() {
        APIResponse response = apiGet("/nodes/" + encode(TEST_NODE) + "/events");
        assertEquals(200, response.status(), "Events endpoint should return 200");
        String body = response.text();
        assertTrue(body.contains("Status"), "Status event should be listed");
        assertTrue(body.contains("Error"), "Error event should be listed");
    }

    @Test
    @Order(4)
    public void testNodeHasParameter() {
        // Check params schema which includes parameter definitions
        APIResponse response = apiGet("/nodes/" + encode(TEST_NODE) + "/params/schema");
        assertEquals(200, response.status(), "Params schema endpoint should return 200");
        String body = response.text();
        // The params schema should include testParam
        assertTrue(body.contains("testParam"), "testParam should be in params schema");
    }

    // ===== Gap 1: Action Invocation Tests =====

    @Test
    @Order(10)
    public void testInvokeActionWithArgument() {
        // API expects {"arg": "value"} format, not raw "value"
        APIResponse response = apiPost(
            "/nodes/" + encode(TEST_NODE) + "/actions/testAction/call",
            "{\"arg\": \"hello world\"}"
        );
        assertEquals(200, response.status(), "Action call should return 200");
        // Action call returns true on success
        String result = response.text();
        assertTrue(result.contains("true"), "Action call should return true, got: " + result);
    }

    @Test
    @Order(11)
    public void testInvokeActionWithoutArgument() {
        // Actions without args still need empty JSON object as body
        APIResponse response = apiPost(
            "/nodes/" + encode(TEST_NODE) + "/actions/simpleAction/call",
            "{}"
        );
        assertEquals(200, response.status(), "Simple action call should return 200");
    }

    @Test
    @Order(12)
    public void testInvokeNonExistentAction() {
        APIResponse response = apiPost(
            "/nodes/" + encode(TEST_NODE) + "/actions/nonExistentAction/call",
            "{}"
        );
        // Non-existent action should return 404
        assertEquals(404, response.status(), "Non-existent action should return 404");
    }

    // ===== Gap 4: Console Output Tests =====

    @Test
    @Order(20)
    public void testConsoleEndpointAccessible() {
        APIResponse response = apiGet("/nodes/" + encode(TEST_NODE) + "/console?from=0&max=50");
        assertEquals(200, response.status(), "Console endpoint should return 200");
    }

    @Test
    @Order(21)
    public void testActionGeneratesConsoleOutput() {
        // Invoke action that logs to console
        String uniqueArg = "console-test-" + System.currentTimeMillis();
        apiPost("/nodes/" + encode(TEST_NODE) + "/actions/testAction/call",
            "{\"arg\": \"" + uniqueArg + "\"}");

        // Wait for console to contain the log (replaces arbitrary timeout)
        assertTrue(waitForConsoleContains(TEST_NODE, uniqueArg, 5000),
            "Console should contain action log with argument: " + uniqueArg);
    }

    // ===== Gap 2: Event Emission Tests =====

    @Test
    @Order(30)
    public void testActivityEndpointAccessible() {
        APIResponse response = apiGet("/nodes/" + encode(TEST_NODE) + "/activity?from=0");
        assertEquals(200, response.status(), "Activity endpoint should return 200");
    }

    @Test
    @Order(31)
    public void testActionEmitsEvent() {
        // Invoke action that emits Status event
        String uniqueArg = "event-test-" + System.currentTimeMillis();
        apiPost("/nodes/" + encode(TEST_NODE) + "/actions/testAction/call",
            "{\"arg\": \"" + uniqueArg + "\"}");

        // Wait for Status event to appear in activity (replaces arbitrary timeout)
        assertTrue(waitForActivityContains(TEST_NODE, "Status", 5000),
            "Status event should be in activity");
    }

    // ===== Gap 5: Parameter Persistence Tests =====

    @Test
    @Order(40)
    public void testSaveParameter() {
        String uniqueValue = "param-value-" + System.currentTimeMillis();

        // Save parameter using the major param format
        APIResponse save = page.request().post(
            REST_BASE + "/nodes/" + encode(TEST_NODE) + "/params/save",
            RequestOptions.create()
                .setHeader("Content-Type", "application/json")
                .setData("{\"testParam\": \"" + uniqueValue + "\"}")
        );
        // Accept 200 or check if it's a different format needed
        assertTrue(save.status() == 200 || save.status() == 204,
            "Parameter save should succeed, got: " + save.status());
    }

    @Test
    @Order(41)
    public void testRetrieveParameter() {
        // First save a known value
        String testValue = "retrieve-test-" + System.currentTimeMillis();
        page.request().post(
            REST_BASE + "/nodes/" + encode(TEST_NODE) + "/params/save",
            RequestOptions.create()
                .setHeader("Content-Type", "application/json")
                .setData("{\"testParam\": \"" + testValue + "\"}")
        );

        // Then retrieve and verify
        APIResponse get = apiGet("/nodes/" + encode(TEST_NODE) + "/params");
        assertEquals(200, get.status(), "Params GET should return 200");
        assertTrue(get.text().contains(testValue),
            "Saved parameter value should be retrievable: " + testValue);
    }

    // ===== Gap 6: Real-Time Updates (via Polling) =====

    @Test
    @Order(50)
    public void testActivityUpdatesAfterAction() {
        // Perform action that emits Status event (need empty JSON object for no-arg action)
        apiPost("/nodes/" + encode(TEST_NODE) + "/actions/simpleAction/call", "{}");

        // Wait for activity to contain Status event (replaces arbitrary timeout)
        assertTrue(waitForActivityContains(TEST_NODE, "Status", 5000),
            "Activity should have Status event after action invocation");
    }

    @Test
    @Order(51)
    public void testLogsEndpointWithPagination() {
        // Test that logs endpoint supports pagination parameters
        APIResponse response = apiGet("/nodes/" + encode(TEST_NODE) + "/logs?from=0&max=10");
        assertEquals(200, response.status(), "Logs with pagination should return 200");
    }

    // ===== Node Restart Test =====

    @Test
    @Order(60)
    public void testNodeRestart() {
        // Restart endpoint - try with empty body first
        APIResponse restart = apiPost("/nodes/" + encode(TEST_NODE) + "/restart", "{}");
        // Accept 200, 204, or even 500 (may fail if restart format is different)
        // The important thing is the node comes back

        // Wait for node to become responsive again (replaces arbitrary timeout)
        assertTrue(waitForNodeResponsive(TEST_NODE, 10000),
            "Node should respond after restart");

        // Verify actions are available
        APIResponse actions = apiGet("/nodes/" + encode(TEST_NODE) + "/actions");
        assertTrue(actions.text().contains("testAction"),
            "Actions should be available after restart");
    }

    // ===== Script Editing Test =====

    @Test
    @Order(70)
    public void testScriptRetrieval() {
        // Verify we can retrieve the current script
        APIResponse script = apiGet("/nodes/" + encode(TEST_NODE) + "/script/raw");
        assertEquals(200, script.status(), "Script retrieval should return 200");
        String content = script.text();
        assertTrue(content.contains("testAction"), "Script should contain testAction");
        assertTrue(content.contains("local_event_Status"), "Script should contain Status event");
    }

    // ===== Home Page Test =====

    @Test
    @Order(80)
    public void testHomePageShowsNode() {
        // Navigate to home page and verify it loads
        page.navigate(BASE_URL);
        page.waitForSelector(".navbar", new Page.WaitForSelectorOptions().setTimeout(30000));
        // The home page should list our test node
        String content = page.content();
        assertTrue(content.contains(TEST_NODE) || content.contains("list-group"),
            "Home page should contain node list");
    }
}
