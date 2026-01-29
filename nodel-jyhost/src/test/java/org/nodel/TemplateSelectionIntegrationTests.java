package org.nodel;

import org.junit.jupiter.api.*;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the duplicateNode JavaScript function.
 * Verifies that preflight checks prevent node creation when the source is unreachable.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TemplateSelectionIntegrationTests extends TestBase {

    @BeforeAll
    static void setup() {
        initBrowser();
        navigateToHome();
    }

    @AfterAll
    static void teardown() {
        closeBrowser();
    }

    @Test
    @Order(1)
    void testDuplicateNodePreflightFailureDoesNotCreateNode() {
        assertJsDefined("duplicateNode");

        String runId = String.valueOf(System.currentTimeMillis());
        String sourceUrl = BASE_URL + "/nodes/NonExistentNode" + runId + "/";
        String newNodeName = "E2E Duplicate Preflight " + runId;

        Object result = page.evaluate("async (args) => {\n" +
            "  return await new Promise((resolve) => {\n" +
            "    duplicateNode(args.source, args.name, {}, function() {})\n" +
            "      .then(function() { resolve('resolved'); })\n" +
            "      .fail(function(err) { resolve('rejected:' + (err && err.message)); });\n" +
            "  });\n" +
            "}", Map.of("source", sourceUrl, "name", newNodeName));

        String resultText = String.valueOf(result);
        assertTrue(resultText.startsWith("rejected:"), "Duplicate should fail when source is unreachable");
        assertTrue(resultText.contains("Source node is not reachable"));

        String nodesBody = apiGet("/nodes").text();
        assertFalse(nodesBody.contains(newNodeName), "Node should not be created on preflight failure");
    }
}
