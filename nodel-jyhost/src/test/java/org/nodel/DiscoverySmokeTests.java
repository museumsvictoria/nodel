package org.nodel;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Opt-in smoke test for real multicast discovery.
 * Enable with: NODEL_TEST_DISCOVERY=1 ./gradlew :nodel-jyhost:integrationTest --tests org.nodel.DiscoverySmokeTests --rerun
 * (--rerun is needed on repeat runs since the environment variable is not a Gradle task input)
 */
@Tag("discovery")
public class DiscoverySmokeTests extends TestBase {

    private static final String TEST_NODE = "Discovery Smoke Node";

    @BeforeAll
    public static void setup() {
        initBrowser();
        assumeTrue(isMulticastDiscoveryRequested(), "NODEL_TEST_DISCOVERY not set; skipping multicast discovery smoke test");

        // assert (not assume): the user explicitly opted in, so an infrastructure
        // failure here must fail the run rather than silently skip it
        assertTrue(createTestNode(TEST_NODE, SIMPLE_TEST_SCRIPT), "Test node must be created for discovery smoke test");
    }

    @AfterAll
    public static void teardown() {
        deleteTestNode(TEST_NODE);
        closeBrowser();
    }

    @Test
    public void testNodeUrlsContainsLocalNode() {
        assertApiEventuallyContains("/nodeURLs", TEST_NODE, 30000,
            "nodeURLs should include the local test node when multicast discovery is enabled");
    }
}
