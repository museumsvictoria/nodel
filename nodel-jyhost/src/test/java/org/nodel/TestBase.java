package org.nodel;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.*;

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
     */
    protected static void initBrowser() {
        playwright = Playwright.create();
        BrowserType.LaunchOptions options = new BrowserType.LaunchOptions()
            .setHeadless(true);
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
     * Navigate to a specific node's page
     */
    protected static void navigateToNode(String nodeName) {
        String encodedName = nodeName.replace(" ", "%20");
        page.navigate(BASE_URL + "/" + encodedName + "/");
        page.waitForSelector(".navbar", new Page.WaitForSelectorOptions().setTimeout(60000));
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

            Path nodeDir = nodesDir.resolve(nodeName);
            Files.createDirectories(nodeDir);

            Path scriptFile = nodeDir.resolve("script.py");
            Files.writeString(scriptFile, scriptContent);

            // Wait for node to be discovered
            Thread.sleep(3000);
            return true;
        } catch (Exception e) {
            System.err.println("Failed to create test node: " + e.getMessage());
            return false;
        }
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
     * Get computed CSS property value for an element
     */
    protected static String getComputedStyle(String selector, String property) {
        return page.evaluate("(args) => window.getComputedStyle(document.querySelector(args.selector)).getPropertyValue(args.property)",
            java.util.Map.of("selector", selector, "property", property)).toString();
    }

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
}
