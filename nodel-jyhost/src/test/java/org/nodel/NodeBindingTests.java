package org.nodel;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.RequestOptions;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * E2E tests for inter-node communication via remote bindings.
 * Tests the core Nodel functionality where nodes can subscribe to events from other nodes.
 *
 * Test scenario:
 * - Producer node has an action that emits a Ping event
 * - Consumer node has a remote event binding that receives the Ping
 * - Binding is configured via REST API
 * - When producer emits, consumer's handler is called
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class NodeBindingTests extends TestBase {

    private static final String PRODUCER_NODE = "E2E Binding Producer";
    private static final String CONSUMER_NODE = "E2E Binding Consumer";

    @BeforeAll
    public static void setup() {
        initBrowser();
        // Create both nodes using centralized scripts from TestBase
        boolean producerCreated = createTestNode(PRODUCER_NODE, Scripts.PRODUCER);
        boolean consumerCreated = createTestNode(CONSUMER_NODE, Scripts.CONSUMER);
        assumeTrue(producerCreated && consumerCreated,
            "Both producer and consumer nodes must be created");
    }

    @AfterAll
    public static void teardown() {
        deleteTestNode(PRODUCER_NODE);
        deleteTestNode(CONSUMER_NODE);
        closeBrowser();
    }

    // ===== Node Setup Verification =====

    @Test
    @Order(1)
    public void testProducerNodeExists() {
        APIResponse response = apiGet("/nodes/" + encode(PRODUCER_NODE) + "/actions");
        assertEquals(200, response.status(), "Producer actions endpoint should return 200");
        assertTrue(response.text().contains("sendPing"),
            "Producer should have sendPing action");
    }

    @Test
    @Order(2)
    public void testProducerHasEvent() {
        APIResponse response = apiGet("/nodes/" + encode(PRODUCER_NODE) + "/events");
        assertEquals(200, response.status(), "Producer events endpoint should return 200");
        assertTrue(response.text().contains("Ping"),
            "Producer should have Ping event");
    }

    @Test
    @Order(3)
    public void testConsumerNodeExists() {
        // Verify consumer node is running (check console for startup message)
        APIResponse response = apiGet("/nodes/" + encode(CONSUMER_NODE) + "/console?from=0&max=10");
        assertEquals(200, response.status(), "Consumer console endpoint should return 200");
        assertTrue(response.text().contains("Consumer node started"),
            "Consumer should have logged startup message");
    }

    @Test
    @Order(4)
    public void testConsumerHasLocalEvent() {
        APIResponse response = apiGet("/nodes/" + encode(CONSUMER_NODE) + "/events");
        assertEquals(200, response.status(), "Consumer events endpoint should return 200");
        assertTrue(response.text().contains("Received"),
            "Consumer should have Received local event");
    }

    // ===== Binding Configuration Tests =====

    @Test
    @Order(10)
    public void testConfigureBinding() {
        // Configure consumer to listen to producer's Ping event
        String bindingConfig = "{\"actions\":{},\"events\":{\"IncomingPing\":{\"node\":\"" +
            PRODUCER_NODE + "\",\"event\":\"Ping\"}}}";

        APIResponse response = page.request().post(
            REST_BASE + "/nodes/" + encode(CONSUMER_NODE) + "/remote/save",
            RequestOptions.create()
                .setHeader("Content-Type", "application/json")
                .setData(bindingConfig)
        );
        assertEquals(200, response.status(), "Binding configuration should succeed");
    }

    @Test
    @Order(11)
    public void testBindingPersisted() {
        APIResponse response = apiGet("/nodes/" + encode(CONSUMER_NODE) + "/remote");
        assertEquals(200, response.status(), "Remote binding endpoint should return 200");
        String body = response.text();
        assertTrue(body.contains(PRODUCER_NODE),
            "Binding should reference producer node: " + body);
        assertTrue(body.contains("Ping"),
            "Binding should reference Ping event: " + body);
    }

    // ===== Inter-Node Communication Tests =====

    @Test
    @Order(20)
    public void testEventPropagation() {
        String uniqueValue = "ping-" + System.currentTimeMillis();

        // Trigger producer's action which emits event
        APIResponse trigger = apiPost(
            "/nodes/" + encode(PRODUCER_NODE) + "/actions/sendPing/call",
            "{\"arg\": \"" + uniqueValue + "\"}"
        );
        assertEquals(200, trigger.status(), "Producer action should succeed");

        // Wait for event propagation across nodes
        page.waitForTimeout(2000);

        // Check consumer's console for received message
        APIResponse consumerConsole = apiGet(
            "/nodes/" + encode(CONSUMER_NODE) + "/console?from=0&max=50"
        );
        assertEquals(200, consumerConsole.status());
        assertTrue(consumerConsole.text().contains(uniqueValue),
            "Consumer should have logged received ping value: " + uniqueValue);
    }

    @Test
    @Order(21)
    public void testConsumerEmitsLocalEvent() {
        String uniqueValue = "propagate-" + System.currentTimeMillis();

        // Trigger producer
        apiPost("/nodes/" + encode(PRODUCER_NODE) + "/actions/sendPing/call",
            "{\"arg\": \"" + uniqueValue + "\"}");

        // Wait for event propagation
        page.waitForTimeout(2000);

        // Check consumer's activity for emitted Received event
        APIResponse activity = apiGet(
            "/nodes/" + encode(CONSUMER_NODE) + "/activity?from=0"
        );
        assertEquals(200, activity.status());
        assertTrue(activity.text().contains("Received"),
            "Consumer should have emitted Received event");
    }

    @Test
    @Order(22)
    public void testProducerConsoleShowsSentPing() {
        String uniqueValue = "verify-send-" + System.currentTimeMillis();

        // Trigger producer
        apiPost("/nodes/" + encode(PRODUCER_NODE) + "/actions/sendPing/call",
            "{\"arg\": \"" + uniqueValue + "\"}");

        page.waitForTimeout(500);

        // Verify producer logged the send
        APIResponse producerConsole = apiGet(
            "/nodes/" + encode(PRODUCER_NODE) + "/console?from=0&max=50"
        );
        assertEquals(200, producerConsole.status());
        assertTrue(producerConsole.text().contains("Ping sent: " + uniqueValue),
            "Producer should have logged 'Ping sent'");
    }
}
