package org.nodel;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Assumptions;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Base class for all Playwright e2e tests providing shared setup, utilities and helpers.
 */
public abstract class TestBase {

    protected static final String BASE_URL = "http://127.0.0.1:18085";
    protected static final String REST_BASE = BASE_URL + "/REST";

    protected static Playwright playwright;
    protected static Browser browser;
    protected static BrowserContext context;
    protected static Page page;

    /**
     * Initialise Playwright browser - call from @BeforeAll in subclasses
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
     * Replace the shared page with a fresh one. Pages left behind by earlier tests
     * carry pending timers (e.g. nodel.js's checkRedirect/checkReload polls) whose
     * delayed location changes interrupt an in-flight navigation on the same tab
     * ("Navigation ... is interrupted by another navigation"). A new page has none.
     */
    protected static void recreatePage() {
        if (page != null) {
            page.close();
        }
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
     * A poll condition that may throw (e.g. while the server is still starting).
     */
    @FunctionalInterface
    protected interface PollCheck {
        boolean check() throws Exception;
    }

    /**
     * Poll a condition until it holds or the timeout elapses.
     * Sleeps between attempts even when the check throws (no hot-spinning),
     * records the last exception and returns it as part of the failure detail.
     *
     * @return null on success, otherwise a failure description including the last exception
     */
    protected static String pollUntil(PollCheck check, int timeoutMs, int intervalMs, String description) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        Exception lastError = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                if (check.check()) {
                    return null;
                }
            } catch (Exception e) {
                lastError = e;
            }
            try {
                Thread.sleep(intervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "interrupted while waiting for " + description;
            }
        }
        return "timed out after " + timeoutMs + "ms waiting for " + description
                + (lastError != null ? "; last error: " + lastError : "");
    }

    /**
     * Poll a REST endpoint until its body contains the expected text, failing the test
     * (with the last underlying exception, if any) when the timeout elapses.
     */
    protected static void assertApiEventuallyContains(String path, String expected, int timeoutMs, String message) {
        String failure = pollUntil(() -> apiGet(path).text().contains(expected), timeoutMs, 500,
                "/REST" + path + " to contain \"" + expected + "\"");
        if (failure != null) {
            fail(message + " — " + failure);
        }
    }

    /**
     * Whether the test run explicitly requested real multicast discovery
     * (mirrors the NODEL_TEST_DISCOVERY handling in nodel-jyhost/build.gradle).
     */
    protected static boolean isMulticastDiscoveryRequested() {
        String value = System.getenv("NODEL_TEST_DISCOVERY");
        if (value == null) return false;
        String trimmed = value.trim();
        return !trimmed.isEmpty() && !trimmed.equalsIgnoreCase("false") && !trimmed.equals("0");
    }

    /**
     * Fail fast if the spawned host silently fell back to multicast discovery.
     * AutoDNS.loadImpl() only WARNs when the org.nodel.discovery.impl system property fails to
     * load, so without this check a misconfiguration would reintroduce multicast flakiness
     * while every test keeps passing on machines where multicast happens to work.
     * No-op when multicast was explicitly requested via NODEL_TEST_DISCOVERY.
     */
    protected static void assertLocalDiscoveryActive() {
        if (isMulticastDiscoveryRequested()) {
            return;
        }
        String failure = pollUntil(TestBase::hostLogsShowLocalDiscovery, 15000, 500,
                "\"LocalAutoDNS enabled\" in the host logs");
        if (failure != null) {
            fail("LocalAutoDNS must be active for deterministic discovery — " + failure
                    + ". The host may have silently fallen back to multicast discovery; check "
                    + resolveTempDir().resolve("error.log") + " for a 'Could not load alternative Discovery"
                    + " implementation' warning.");
        }
    }

    private static boolean hostLogsShowLocalDiscovery() throws IOException {
        // the host's SLF4J binding writes to stderr by default, but check both redirected logs
        for (String logName : new String[] { "error.log", "output.log" }) {
            Path log = resolveTempDir().resolve(logName);
            if (Files.exists(log) && Files.readString(log).contains("LocalAutoDNS enabled")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolve the temporary host directory created by the startNodelhost Gradle task,
     * tolerating either the module or repo root as working directory.
     */
    protected static Path resolveTempDir() {
        Path candidate = Paths.get("nodelhost-temp");
        if (!Files.exists(candidate)) {
            candidate = Paths.get("nodel-jyhost/nodelhost-temp");
        }
        if (!Files.exists(candidate)) {
            throw new IllegalStateException("nodelhost-temp directory not found. Ensure the startNodelhost task ran successfully."
                    + " Current working directory: " + System.getProperty("user.dir"));
        }
        return candidate;
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
     * Upload a file into a node's directory via the 'files/save' endpoint
     * (e.g. a custom 'content/index.xml' dashboard).
     */
    protected static APIResponse uploadNodeFile(String nodeName, String path, String content) {
        return page.request().post(
            REST_BASE + "/nodes/" + encode(nodeName) + "/files/save?path=" + path,
            com.microsoft.playwright.options.RequestOptions.create()
                .setHeader("Content-Type", "application/octet-stream")
                .setData(content));
    }

    /**
     * Create a test node by creating its directory and script file
     * Returns true if created successfully
     */
    protected static boolean createTestNode(String nodeName, String scriptContent) {
        try {
            // Find the nodes directory relative to where the server runs
            Path nodesDir = resolveTempDir().resolve("nodes");
            if (!Files.exists(nodesDir)) {
                System.err.println("ERROR: Nodes directory not found at " + nodesDir + ". Ensure startNodelhost task ran successfully.");
                return false;
            }

            Path nodeDir = nodesDir.resolve(nodeName);
            Files.createDirectories(nodeDir);

            Path scriptFile = nodeDir.resolve("script.py");
            Files.writeString(scriptFile, scriptContent);

            // Wait for node to be discovered and initialised by polling the API
            return waitForNodeDiscovery(nodeName, 30000);
        } catch (Exception e) {
            System.err.println("Failed to create test node: " + e.getMessage());
            return false;
        }
    }

    /**
     * Poll the REST API until the specified node appears in the node list
     * and its script has finished loading (actions are available).
     * @param nodeName the name of the node to wait for
     * @param timeoutMs maximum time to wait in milliseconds
     * @return true if node was discovered and initialised, false if timeout
     */
    private static boolean waitForNodeDiscovery(String nodeName, int timeoutMs) {
        boolean[] foundInList = { false };
        String failure = pollUntil(() -> {
            // First check if node appears in node list
            if (!foundInList[0]) {
                APIResponse listResponse = page.request().get(REST_BASE + "/nodes");
                if (listResponse.text().contains(nodeName)) {
                    foundInList[0] = true;
                }
            }

            // Then check if node endpoints are ready (script has loaded)
            if (foundInList[0]) {
                // Check if actions endpoint returns 200 (node is initialised)
                APIResponse actionsResponse = page.request().get(
                    REST_BASE + "/nodes/" + encode(nodeName) + "/actions");
                if (actionsResponse.status() == 200) {
                    // Also verify console endpoint works (confirms script executed)
                    APIResponse consoleResponse = page.request().get(
                        REST_BASE + "/nodes/" + encode(nodeName) + "/console?from=0&max=10");
                    return consoleResponse.status() == 200 && consoleResponse.text().contains("started");
                }
            }
            return false;
        }, timeoutMs, 500, "discovery/initialisation of node '" + nodeName + "'");

        if (failure != null) {
            System.err.println(failure);
            return false;
        }
        return true;
    }

    /**
     * Delete a test node by removing its directory
     */
    protected static boolean deleteTestNode(String nodeName) {
        try {
            Path nodeDir = resolveTempDir().resolve("nodes").resolve(nodeName);
            deleteDirectoryRecursively(nodeDir);
            return true;
        } catch (Exception e) {
            System.err.println("Failed to delete test node: " + e.getMessage());
            return false;
        }
    }

    /**
     * Recursively delete a directory, reporting (rather than hiding) any paths
     * that could not be removed so leaked test artefacts are visible in the output.
     */
    protected static void deleteDirectoryRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        Files.walk(dir)
            .sorted((a, b) -> b.compareTo(a))
            .forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    System.err.println("WARNING: failed to delete test artefact " + path + ": " + e);
                }
            });
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
     * Poll the console API until it contains the expected text.
     * Use this instead of arbitrary page.waitForTimeout() after actions.
     */
    protected static boolean waitForConsoleContains(String nodeName, String expectedText, int timeoutMs) {
        return reportOnTimeout(pollUntil(() -> {
            APIResponse response = page.request().get(REST_BASE + "/nodes/" + encode(nodeName) + "/console?from=0&max=100");
            return response.status() == 200 && response.text().contains(expectedText);
        }, timeoutMs, 200, "console of '" + nodeName + "' to contain \"" + expectedText + "\""));
    }

    /**
     * Print the pollUntil failure detail (if any) and translate it to a boolean result.
     */
    private static boolean reportOnTimeout(String failure) {
        if (failure != null) {
            System.err.println(failure);
            return false;
        }
        return true;
    }

    /**
     * Poll the activity API until it contains the expected text.
     * Use this instead of arbitrary page.waitForTimeout() after events.
     */
    protected static boolean waitForActivityContains(String nodeName, String expectedText, int timeoutMs) {
        return reportOnTimeout(pollUntil(() -> {
            APIResponse response = page.request().get(REST_BASE + "/nodes/" + encode(nodeName) + "/activity?from=0");
            return response.status() == 200 && response.text().contains(expectedText);
        }, timeoutMs, 200, "activity of '" + nodeName + "' to contain \"" + expectedText + "\""));
    }

    /**
     * Poll until a node becomes responsive (e.g., after restart).
     * Use this instead of arbitrary page.waitForTimeout() after node operations.
     */
    protected static boolean waitForNodeResponsive(String nodeName, int timeoutMs) {
        return reportOnTimeout(pollUntil(() -> {
            APIResponse response = page.request().get(REST_BASE + "/nodes/" + encode(nodeName) + "/actions");
            return response.status() == 200;
        }, timeoutMs, 200, "node '" + nodeName + "' to become responsive"));
    }

    /**
     * Poll the node list API until the specified node appears.
     * Use this after creating a node via UI to wait for it to be registered.
     */
    protected static boolean waitForNodeInList(String nodeName, int timeoutMs) {
        return reportOnTimeout(pollUntil(() -> {
            APIResponse response = page.request().get(REST_BASE + "/nodes");
            return response.status() == 200 && response.text().contains(nodeName);
        }, timeoutMs, 500, "node '" + nodeName + "' to appear in node list"));
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
     * Centralised test scripts for node creation
     */
    protected static class Scripts {
        public static final String PRODUCER =
            "local_event_Ping = LocalEvent({'title': 'Ping', 'schema': {'type': 'string'}})\n\n" +
            "@local_action({'title': 'Send Ping', 'schema': {'type': 'string'}})\n" +
            "def sendPing(arg):\n" +
            "    local_event_Ping.emit(arg)\n" +
            "    console.info('Ping sent: %s' % arg)\n" +
            "    return True\n\n" +
            "def main():\n" +
            "    console.info('Producer node started')\n";

        public static final String CONSUMER =
            "def remote_event_IncomingPing(arg):\n" +
            "    console.info('Received ping: %s' % arg)\n" +
            "    local_event_Received.emit(arg)\n\n" +
            "local_event_Received = LocalEvent({'title': 'Received', 'schema': {'type': 'string'}})\n\n" +
            "def main():\n" +
            "    console.info('Consumer node started')\n";

        public static final String WITH_PARAMS =
            "param_testParam = Parameter({'title': 'Test Parameter', 'schema': {'type': 'string'}})\n" +
            "param_numberParam = Parameter({'title': 'Number Param', 'schema': {'type': 'integer'}})\n\n" +
            "def main():\n" +
            "    console.info('Param test node started')\n";
    }
}
