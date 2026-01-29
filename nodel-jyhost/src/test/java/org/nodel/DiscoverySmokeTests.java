package org.nodel;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Opt-in smoke test for real multicast discovery.
 * Enable with: NODEL_TEST_DISCOVERY=1 ./gradlew :nodel-jyhost:integrationTest --tests org.nodel.DiscoverySmokeTests
 */
@Tag("discovery")
public class DiscoverySmokeTests extends TestBase {

    private static final String TEST_NODE = "Discovery Smoke Node";

    @BeforeAll
    public static void setup() {
        initBrowser();
        assumeTrue(isDiscoveryEnabled(), "NODEL_TEST_DISCOVERY not set; skipping multicast discovery smoke test");

        boolean created = createTestNode(TEST_NODE, SIMPLE_TEST_SCRIPT);
        assumeTrue(created, "Test node must be created for discovery smoke test");
    }

    @AfterAll
    public static void teardown() {
        deleteTestNode(TEST_NODE);
        closeBrowser();
    }

    @Test
    public void testNodeUrlsContainsLocalNode() {
        assertTrue(waitForNodeUrlEntry(TEST_NODE, 30000),
            "nodeURLs should include the local test node when multicast discovery is enabled");
    }

    private static boolean isDiscoveryEnabled() {
        String value = System.getenv("NODEL_TEST_DISCOVERY");
        if (value == null) return false;
        String trimmed = value.trim();
        return !trimmed.isEmpty() && !trimmed.equalsIgnoreCase("false") && !trimmed.equals("0");
    }

    private static boolean waitForNodeUrlEntry(String nodeName, int timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try {
                String body = apiGet("/nodeURLs").text();
                if (body.contains(nodeName)) {
                    return true;
                }
                Thread.sleep(500);
            } catch (Exception ignored) {
            }
        }
        return false;
    }
}
