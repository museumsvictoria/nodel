package org.nodel;

import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the node-duplication JavaScript logic.
 * Verifies that preflight checks prevent node creation when the source is unreachable,
 * and pins down the pure file-filtering and name-truncation rules.
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

    @Test
    @Order(2)
    @SuppressWarnings("unchecked")
    void testShouldCopyFileRules() {
        assertJsDefined("shouldCopyFile");

        // note: exclusion is by basename only — an underscore-prefixed *directory*
        // does not exclude the files inside it
        List<Map<String, Object>> cases = List.of(
            Map.of("path", "script.py", "expected", true),
            Map.of("path", "custom.xml", "expected", true),
            Map.of("path", "nodeConfig.json", "expected", true),
            Map.of("path", "_ignore.txt", "expected", false),
            Map.of("path", "subdir/_generated.txt", "expected", false),
            Map.of("path", "_private/script.py", "expected", true),
            Map.of("path", "script_backup_20240101.py", "expected", false),
            Map.of("path", "subdir/script_backup_1.py", "expected", false),
            Map.of("path", "myscript_backup_1.py", "expected", true),
            Map.of("path", "script_backup_.txt", "expected", true)
        );

        Object failures = page.evaluate("(cases) => cases" +
            ".filter(c => shouldCopyFile(c.path) !== c.expected)" +
            ".map(c => c.path + ' => ' + !c.expected)", cases);
        assertEquals(List.of(), failures, "shouldCopyFile rules changed unexpectedly");
    }

    @Test
    @Order(3)
    @SuppressWarnings("unchecked")
    void testFilterFilesForDuplication() {
        assertJsDefined("filterFilesForDuplication");

        Object result = page.evaluate("() => {\n" +
            "  const files = [\n" +
            "    {path: 'nodeConfig.json'},\n" +
            "    {path: 'sub/nodeConfig.json'},\n" +   // must match by basename, not exact path
            "    {path: 'script.py'},\n" +
            "    {path: 'custom.xml'},\n" +
            "    {path: '_generated.txt'},\n" +
            "    {path: 'script_backup_20240101.py'}\n" +
            "  ];\n" +
            "  return {\n" +
            "    withoutConfig: filterFilesForDuplication(files, false).map(f => f.path),\n" +
            "    withConfig: filterFilesForDuplication(files, true).map(f => f.path)\n" +
            "  };\n" +
            "}");

        Map<String, Object> lists = (Map<String, Object>) result;
        assertEquals(List.of("custom.xml", "script.py"), lists.get("withoutConfig"),
            "Without config copy: nodeConfig.json excluded wherever it sits, script.py ordered last");
        assertEquals(List.of("nodeConfig.json", "sub/nodeConfig.json", "custom.xml", "script.py"), lists.get("withConfig"),
            "With config copy: nodeConfig.json included, script.py still ordered last");
    }

    @Test
    @Order(4)
    void testGetSimpleNameTruncationRules() {
        assertJsDefined("getSimpleName");

        // getSimpleName truncates display names at "(", " (", "--" and "//" — this
        // applies everywhere node names are shown, not just the duplication dialog
        List<Map<String, Object>> cases = List.of(
            Map.of("input", "Node Name (v2)", "expected", "Node Name"),
            Map.of("input", "Node Name(v2)", "expected", "Node Name"),
            Map.of("input", "Device--Location", "expected", "Device"),
            Map.of("input", "Device -- Location", "expected", "Device"),
            Map.of("input", "Service//Type", "expected", "Service"),
            Map.of("input", "AMX - Foyer", "expected", "AMX - Foyer"),
            Map.of("input", "plain", "expected", "plain")
        );

        Object failures = page.evaluate("(cases) => cases" +
            ".filter(c => getSimpleName(c.input) !== c.expected)" +
            ".map(c => c.input + ' => ' + JSON.stringify(getSimpleName(c.input)))", cases);
        assertEquals(List.of(), failures, "getSimpleName truncation rules changed unexpectedly");
    }
}
