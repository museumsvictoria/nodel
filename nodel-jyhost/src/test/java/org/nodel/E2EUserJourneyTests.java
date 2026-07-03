package org.nodel;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E tests that simulate real user interactions with the UI.
 * These tests click, type, and verify actual behavior - not just element presence.
 *
 * Run visually with: HEADED=1 SLOWMO=500 ./gradlew :nodel-jyhost:e2eTest
 */
@Tag("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class E2EUserJourneyTests extends TestBase {

    private static final String TEST_NODE = "E2E Journey Node";

    @BeforeAll
    static void setup() {
        initBrowser();
        createTestNode(TEST_NODE, SIMPLE_TEST_SCRIPT);
    }

    @AfterAll
    static void teardown() {
        deleteTestNode(TEST_NODE);
        closeBrowser();
    }

    @Test
    @Order(1)
    void testHomePageShowsTestNode() {
        page.navigate(BASE_URL);
        waitForElement(".list-group");

        // Verify our test node appears - strict assertion
        Locator nodeLink = page.locator("a:has-text('" + TEST_NODE + "'), .list-group-item:has-text('" + TEST_NODE + "')").first();
        assertTrue(nodeLink.isVisible(), "Test node '" + TEST_NODE + "' must be visible in node list");
    }

    @Test
    @Order(2)
    void testClickingNodeNavigatesToNodePage() {
        page.navigate(BASE_URL);
        waitForElement(".list-group");

        Locator nodeLink = page.locator("a:has-text('" + TEST_NODE + "'), .list-group-item:has-text('" + TEST_NODE + "')").first();
        assertTrue(nodeLink.isVisible(), "Test node must be visible before clicking");

        nodeLink.click();
        page.waitForTimeout(2000);

        // Verify URL changed to node page (Nodel uses reduced names in URLs)
        String currentUrl = page.url();
        String reducedName = getReducedName(TEST_NODE);
        assertTrue(currentUrl.contains(reducedName),
            "URL should contain reduced node name '" + reducedName + "' after clicking. Current URL: " + currentUrl);
    }

    @Test
    @Order(3)
    void testInvokingActionUpdatesConsole() {
        // Navigate to node page
        navigateToNode(TEST_NODE);
        page.waitForTimeout(1000);

        // Generate unique marker and invoke action via API
        String uniqueMarker = "e2e-action-" + System.currentTimeMillis();
        APIResponse actionResponse = apiPost("/nodes/" + encode(TEST_NODE) + "/actions/testAction/call",
            "{\"arg\": \"" + uniqueMarker + "\"}");
        assertEquals(200, actionResponse.status(), "Action invocation must succeed");

        // Wait for console to contain our marker (replaces arbitrary timeout)
        assertTrue(waitForConsoleContains(TEST_NODE, uniqueMarker, 5000),
            "Console must contain action output with marker: " + uniqueMarker);
    }

    @Test
    @Order(4)
    void testActionEmitsEventToActivity() {
        navigateToNode(TEST_NODE);

        String uniqueMarker = "e2e-event-" + System.currentTimeMillis();
        apiPost("/nodes/" + encode(TEST_NODE) + "/actions/testAction/call",
            "{\"arg\": \"" + uniqueMarker + "\"}");

        // Wait for Status event to appear in activity (replaces arbitrary timeout)
        assertTrue(waitForActivityContains(TEST_NODE, "Status", 5000),
            "Activity must contain Status event after action");
    }

    // ===== Node Creation via UI =====

    @Test
    @Order(10)
    void testCreateNodeViaUI() {
        page.navigate(BASE_URL);
        // the add-node UI sits inside div.page, which stays display:none until nodel.js's
        // init reveals the active section - well after .navbar (static XSLT output)
        // renders, so wait for the control itself rather than the navbar. A timeout
        // here fails (not skips) the test if these elements regress.
        waitForElement(".nodel-add .addgrp .dropdown-toggle");

        Locator addDropdown = page.locator(".nodel-add .addgrp .dropdown-toggle").first();
        addDropdown.click();

        Locator nodeNameInput = page.locator(".nodel-add input.nodenamval").first();
        assertTrue(nodeNameInput.isVisible(), "Node name input must exist");

        String uniqueNodeName = "E2ECreated" + System.currentTimeMillis();
        nodeNameInput.fill(uniqueNodeName);

        Locator submitBtn = page.locator(".nodel-add .nodeaddsubmit").first();
        assertTrue(submitBtn.isVisible(), "Submit button must exist");
        submitBtn.click();

        // Poll until node appears in API (replaces fixed 5s wait)
        boolean nodeCreated = waitForNodeInList(uniqueNodeName, 15000);

        // Cleanup
        if (nodeCreated) {
            deleteTestNode(uniqueNodeName);
        }

        assertTrue(nodeCreated, "Node created via UI must appear in the node list");
    }

    // ===== Inter-Node Binding =====

    private static final String BINDING_PRODUCER_NODE = "E2E Binding Producer UI";
    private static final String BINDING_CONSUMER_NODE = "E2E Binding Consumer UI";

    @Test
    @Order(11)
    void testConfigureBindingAndVerifyEventPropagation() {
        // Setup nodes
        boolean producerCreated = createTestNode(BINDING_PRODUCER_NODE, Scripts.PRODUCER);
        boolean consumerCreated = createTestNode(BINDING_CONSUMER_NODE, Scripts.CONSUMER);

        try {
            assertTrue(producerCreated, "Producer node must be created");
            assertTrue(consumerCreated, "Consumer node must be created");

            // Configure binding via API
            String bindingConfig = "{\"events\":{\"IncomingPing\":{\"node\":\"" +
                BINDING_PRODUCER_NODE + "\",\"event\":\"Ping\"}},\"actions\":{}}";
            APIResponse saveResponse = apiPost("/nodes/" + encode(BINDING_CONSUMER_NODE) + "/remote/save", bindingConfig);
            assertEquals(200, saveResponse.status(), "Binding save must succeed");

            // Verify binding was persisted
            APIResponse getResponse = apiGet("/nodes/" + encode(BINDING_CONSUMER_NODE) + "/remote");
            assertTrue(getResponse.text().contains(BINDING_PRODUCER_NODE),
                "Remote binding must reference producer node");

            // Trigger producer and verify consumer receives
            String uniqueValue = "binding-test-" + System.currentTimeMillis();
            apiPost("/nodes/" + encode(BINDING_PRODUCER_NODE) + "/actions/sendPing/call",
                "{\"arg\": \"" + uniqueValue + "\"}");

            // Wait for consumer to receive and log the ping (bindings need time to establish)
            assertTrue(waitForConsoleContains(BINDING_CONSUMER_NODE, uniqueValue, 10000),
                "Consumer must have logged received ping value: " + uniqueValue);

        } finally {
            deleteTestNode(BINDING_PRODUCER_NODE);
            deleteTestNode(BINDING_CONSUMER_NODE);
        }
    }

    // ===== Parameter Editing =====

    private static final String PARAM_TEST_NODE = "E2E Param Test Node";

    @Test
    @Order(12)
    void testSaveAndRetrieveParameter() {
        boolean nodeCreated = createTestNode(PARAM_TEST_NODE, Scripts.WITH_PARAMS);

        try {
            assertTrue(nodeCreated, "Param test node must be created");

            String uniqueValue = "param-value-" + System.currentTimeMillis();
            String paramData = "{\"testParam\": \"" + uniqueValue + "\"}";

            APIResponse saveResponse = apiPost("/nodes/" + encode(PARAM_TEST_NODE) + "/params/save", paramData);
            assertTrue(saveResponse.status() == 200 || saveResponse.status() == 204,
                "Parameter save must succeed");

            // Verify parameter was persisted (param save is synchronous, no wait needed)
            APIResponse getResponse = apiGet("/nodes/" + encode(PARAM_TEST_NODE) + "/params");
            assertTrue(getResponse.text().contains(uniqueValue),
                "Saved parameter value '" + uniqueValue + "' must be retrievable");

        } finally {
            deleteTestNode(PARAM_TEST_NODE);
        }
    }
}
