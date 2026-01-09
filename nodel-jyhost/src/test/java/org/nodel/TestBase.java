package org.nodel;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Assumptions;
import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Base class for all Playwright e2e tests providing shared setup, utilities and helpers.
 */
public abstract class TestBase {

    protected static final String BASE_URL = "http://127.0.0.1:8085";
    protected static final String REST_BASE = BASE_URL + "/REST";

    protected static Playwright playwright;
    protected static Browser browser;
    protected static BrowserContext context;
    protected static Page page;

    /**
     * Initialize Playwright browser - call from @BeforeAll in subclasses
     *
     * Environment variables:
     *   HEADED=1     - Run browser in visible mode (default: headless)
     *   SLOWMO=500   - Add delay in ms between actions for visibility
     */
    protected static void initBrowser() {
        playwright = Playwright.create();

        boolean headless = System.getenv("HEADED") == null;
        String slowMo = System.getenv("SLOWMO");

        BrowserType.LaunchOptions options = new BrowserType.LaunchOptions()
            .setHeadless(headless);

        if (slowMo != null) {
            options.setSlowMo(Double.parseDouble(slowMo));
        }

        browser = playwright.chromium().launch(options);
        context = browser.newContext();
        page = context.newPage();
    }

    /**
     * Navigate to base URL and wait for page ready
     */
    protected static void navigateToHome() {
        page.navigate(BASE_URL);
        page.waitForSelector(".navbar", new Page.WaitForSelectorOptions().setTimeout(60000));
    }

    /**
     * Navigate to a specific node's page using the reduced name (Nodel removes spaces, etc.)
     */
    protected static void navigateToNode(String nodeName) {
        String reducedName = getReducedName(nodeName);
        page.navigate(BASE_URL + "/nodes/" + reducedName + "/");
        page.waitForSelector(".navbar", new Page.WaitForSelectorOptions().setTimeout(60000));
    }

    /**
     * Get the reduced name for a node (removes spaces, hyphens, etc.)
     * Nodel uses reduced names in URLs.
     */
    protected static String getReducedName(String nodeName) {
        return nodeName.replaceAll("[\\s\\-_.]", "");
    }

    /**
     * Close browser - call from @AfterAll in subclasses
     */
    protected static void closeBrowser() {
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
    }

    /**
     * Make a GET request to a REST endpoint
     */
    protected static APIResponse apiGet(String path) {
        return page.request().get(REST_BASE + path);
    }

    /**
     * Make a POST request to a REST endpoint with JSON body
     */
    protected static APIResponse apiPost(String path, String jsonBody) {
        return page.request().post(REST_BASE + path,
            com.microsoft.playwright.options.RequestOptions.create()
                .setHeader("Content-Type", "application/json")
                .setData(jsonBody));
    }

    /**
     * Make a POST request to a REST endpoint without body
     */
    protected static APIResponse apiPost(String path) {
        return page.request().post(REST_BASE + path);
    }

    /**
     * Create a test node by creating its directory and script file
     * Returns true if created successfully
     */
    protected static boolean createTestNode(String nodeName, String scriptContent) {
        try {
            // Find the nodes directory relative to where the server runs
            Path nodesDir = Paths.get("nodelhost-temp/nodes");
            if (!Files.exists(nodesDir)) {
                nodesDir = Paths.get("nodel-jyhost/nodelhost-temp/nodes");
            }
            if (!Files.exists(nodesDir)) {
                System.err.println("ERROR: Nodes directory not found. Ensure startNodelhost task ran successfully.");
                System.err.println("Checked paths: nodelhost-temp/nodes, nodel-jyhost/nodelhost-temp/nodes");
                System.err.println("Current working directory: " + System.getProperty("user.dir"));
                return false;
            }

            Path nodeDir = nodesDir.resolve(nodeName);
            Files.createDirectories(nodeDir);

            Path scriptFile = nodeDir.resolve("script.py");
            Files.writeString(scriptFile, scriptContent);

            // Wait for node to be discovered by polling the API
            return waitForNodeDiscovery(nodeName, 15000);
        } catch (Exception e) {
            System.err.println("Failed to create test node: " + e.getMessage());
            return false;
        }
    }

    /**
     * Poll the REST API until the specified node appears in the node list
     * @param nodeName the name of the node to wait for
     * @param timeoutMs maximum time to wait in milliseconds
     * @return true if node was discovered, false if timeout
     */
    private static boolean waitForNodeDiscovery(String nodeName, int timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try {
                APIResponse response = page.request().get(REST_BASE + "/nodes");
                if (response.text().contains(nodeName)) {
                    return true;
                }
                Thread.sleep(500); // Poll every 500ms
            } catch (Exception e) {
                // Continue polling - server might not be ready yet
            }
        }
        System.err.println("Timeout waiting for node discovery: " + nodeName);
        return false;
    }

    /**
     * Delete a test node by removing its directory
     */
    protected static boolean deleteTestNode(String nodeName) {
        try {
            Path nodesDir = Paths.get("nodelhost-temp/nodes");
            if (!Files.exists(nodesDir)) {
                nodesDir = Paths.get("nodel-jyhost/nodelhost-temp/nodes");
            }

            Path nodeDir = nodesDir.resolve(nodeName);
            if (Files.exists(nodeDir)) {
                deleteDirectory(nodeDir.toFile());
            }
            return true;
        } catch (Exception e) {
            System.err.println("Failed to delete test node: " + e.getMessage());
            return false;
        }
    }

    private static void deleteDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        dir.delete();
    }

    /**
     * Wait for an element to appear with custom timeout
     */
    protected static void waitForElement(String selector, int timeoutMs) {
        page.waitForSelector(selector, new Page.WaitForSelectorOptions().setTimeout(timeoutMs));
    }

    /**
     * Wait for an element to appear with default timeout
     */
    protected static void waitForElement(String selector) {
        waitForElement(selector, 30000);
    }

    /**
     * Check if an element exists on the page
     */
    protected static boolean elementExists(String selector) {
        return page.querySelector(selector) != null;
    }

    /**
     * URL-encode a node name for use in REST paths
     */
    protected static String encode(String nodeName) {
        return nodeName.replace(" ", "%20");
    }

    /**
     * Get computed CSS property value for an element
     */
    protected static String getComputedStyle(String selector, String property) {
        return page.evaluate("(args) => window.getComputedStyle(document.querySelector(args.selector)).getPropertyValue(args.property)",
            java.util.Map.of("selector", selector, "property", property)).toString();
    }

    // ===== Assertion Helpers =====

    /**
     * Assert that a JavaScript symbol is defined (typeof !== 'undefined')
     */
    protected static void assertJsDefined(String symbol) {
        Object result = page.evaluate("() => typeof " + symbol + " !== 'undefined'");
        assertEquals(true, result, symbol + " should be defined");
    }

    /**
     * Assert that a JavaScript expression evaluates to true
     */
    protected static void assertJsExpression(String expression, String description) {
        Object result = page.evaluate("() => " + expression);
        assertEquals(true, result, description);
    }

    /**
     * Assert that a REST endpoint returns HTTP 200
     */
    protected static void assertEndpointOk(String path) {
        APIResponse response = apiGet(path);
        assertEquals(200, response.status(), "GET /REST" + path + " should return 200");
    }

    /**
     * Assert that a REST endpoint returns HTTP 200 or 204
     */
    protected static void assertEndpointOkOrEmpty(String path) {
        APIResponse response = apiGet(path);
        assertTrue(response.status() == 200 || response.status() == 204,
            "GET /REST" + path + " should return 200 or 204");
    }

    /**
     * Skip test if element is not visible (proper JUnit skip semantics)
     */
    protected static void assumeVisible(Locator element, String description) {
        Assumptions.assumeTrue(element.isVisible(), description + " not visible - skipping");
    }

    // ===== Test Scripts =====

    /**
     * Simple test node script with basic actions and events
     */
    protected static final String SIMPLE_TEST_SCRIPT =
        "# Test Node Script\n\n" +
        "param_testParam = Parameter({'title': 'Test Parameter', 'schema': {'type': 'string'}})\n\n" +
        "local_event_Status = LocalEvent({'title': 'Status', 'schema': {'type': 'string'}})\n" +
        "local_event_Error = LocalEvent({'title': 'Error', 'schema': {'type': 'string'}})\n\n" +
        "@local_action({'title': 'Test Action', 'schema': {'type': 'string'}})\n" +
        "def testAction(arg):\n" +
        "    console.info('Test action called with: %s' % arg)\n" +
        "    local_event_Status.emit('Action executed: %s' % arg)\n" +
        "    return 'OK'\n\n" +
        "@local_action({'title': 'Simple Action'})\n" +
        "def simpleAction():\n" +
        "    console.info('Simple action called')\n" +
        "    local_event_Status.emit('Simple action executed')\n\n" +
        "def main():\n" +
        "    console.info('Test node started')\n" +
        "    local_event_Status.emit('Ready')\n";

    /**
     * Centralized test scripts for node creation
     */
    protected static class Scripts {
        public static final String PRODUCER =
            "local_event_Ping = LocalEvent({'title': 'Ping', 'schema': {'type': 'string'}})\n\n" +
            "@local_action({'title': 'Send Ping', 'schema': {'type': 'string'}})\n" +
            "def sendPing(arg):\n" +
            "    local_event_Ping.emit(arg)\n" +
            "    console.info('Ping sent: %s' % arg)\n\n" +
            "def main():\n" +
            "    console.info('Producer started')\n";

        public static final String CONSUMER =
            "def remote_event_IncomingPing(arg):\n" +
            "    console.info('Received ping: %s' % arg)\n" +
            "    local_event_Received.emit(arg)\n\n" +
            "local_event_Received = LocalEvent({'title': 'Received', 'schema': {'type': 'string'}})\n\n" +
            "def main():\n" +
            "    console.info('Consumer started')\n";

        public static final String WITH_PARAMS =
            "param_testParam = Parameter({'title': 'Test Parameter', 'schema': {'type': 'string'}})\n" +
            "param_numberParam = Parameter({'title': 'Number Param', 'schema': {'type': 'integer'}})\n\n" +
            "def main():\n" +
            "    console.info('Param test node started')\n";
    }
}
