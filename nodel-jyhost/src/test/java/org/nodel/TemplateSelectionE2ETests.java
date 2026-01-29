package org.nodel;

import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * End-to-end tests for the template selection UI in the add-node dialog.
 * Tests recipe selection, node duplication with and without config copying,
 * and card click behaviors.
 */
@Tag("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TemplateSelectionE2ETests extends TestBase {

    private static String runId;
    private static String recipeName;
    private static String recipeMarker;
    private static String recipeScript;
    private static String sourceNodeName;
    private static String sourceScriptMarker;
    private static String nodeConfigMarker;
    private static Path tempDir;
    private static Path nodesDir;
    private static Path recipesDir;

    @BeforeAll
    static void setup() throws Exception {
        initBrowser();

        tempDir = resolveTempDir();
        nodesDir = tempDir.resolve("nodes");
        recipesDir = tempDir.resolve("recipes");
        Files.createDirectories(nodesDir);
        Files.createDirectories(recipesDir);

        runId = String.valueOf(System.currentTimeMillis());
        recipeName = "E2ERecipe" + runId;
        recipeMarker = "recipe-marker-" + runId;
        recipeScript = "# " + recipeMarker + "\n" +
            "def main():\n" +
            "    console.info('Test node started')\n";
        writeRecipe(recipeName, recipeScript);
        assertTrue(waitForRecipeEntry(recipeName, 10000), "Recipe list must be available for template search");

        sourceNodeName = "E2E Template Source " + runId;
        sourceScriptMarker = "source-script-marker-" + runId;
        String sourceScript = SIMPLE_TEST_SCRIPT + "\n# " + sourceScriptMarker + "\n";
        assumeTrue(createTestNode(sourceNodeName, sourceScript), "Source node must be created");

        nodeConfigMarker = "config-marker-" + runId;
        String nodeConfigJson = "{\"remoteBindingValues\":{\"actions\":{\"testAction\":{\"node\":\"OtherNode\",\"action\":\"DoThing\"}},\"events\":{\"testEvent\":{\"node\":\"OtherNode\",\"event\":\"Triggered\"}}},\"paramValues\":{\"testParam\":\"" + nodeConfigMarker + "\"}}";
        writeNodeConfig(sourceNodeName, nodeConfigJson);
        writeSourceExtras(sourceNodeName);

        assertTrue(waitForNodeUrlEntry(sourceNodeName, 30000), "Node URLs must be available for template search");
    }

    @AfterAll
    static void teardown() throws Exception {
        deleteTestNode(sourceNodeName);
        deleteRecipe(recipeName);
        closeBrowser();
    }

    @Test
    @Order(1)
    void testCreateNodeFromRecipeSelection() throws Exception {
        openAddNodeDropdown();
        typeTemplateSearch(recipeName);
        waitForAutocompleteItemInSection("Recipes", recipeName);
        selectAutocompleteItemInSection("Recipes", recipeName);

        Locator card = page.locator(".template-selection-card").first();
        assertTrue(card.locator(".card-type").innerText().contains("Recipe"));
        assertNull(card.getAttribute("data-address"));
        assertEquals(0, page.locator(".copy-config-option").count());

        String newNodeName = "E2E Recipe Node " + runId;
        page.locator(".nodel-add input.nodenamval").first().fill(newNodeName);
        page.locator(".nodel-add .nodeaddsubmit").first().click();

        assertTrue(waitForNodeInList(newNodeName, 15000), "Recipe-based node should appear in node list");
        assertTrue(waitForFileContains(newNodeName, "script.py", recipeMarker, 10000),
            "Recipe-based node should contain recipe script marker");

        deleteTestNode(newNodeName);
    }

    @Test
    @Order(2)
    void testDuplicateNodeWithoutConfigCopy() throws Exception {
        openAddNodeDropdown();
        typeTemplateSearch(sourceNodeName);
        waitForAutocompleteItemInSection("Existing Nodes", sourceNodeName);
        selectAutocompleteItemInSection("Existing Nodes", sourceNodeName);

        Locator card = page.locator(".template-selection-card").first();
        assertNotNull(card.getAttribute("data-address"));

        Locator includeConfig = page.locator(".copy-config-option input.include-node-config").first();
        assertTrue(includeConfig.isVisible());
        assertFalse(includeConfig.isChecked());

        String newNodeName = "E2E Duplicate Off " + runId;
        page.locator(".nodel-add input.nodenamval").first().fill(newNodeName);
        page.locator(".nodel-add .nodeaddsubmit").first().click();

        assertTrue(waitForNodeInList(newNodeName, 15000), "Duplicated node should appear in node list");
        assertTrue(waitForFileContains(newNodeName, "script.py", sourceScriptMarker, 10000),
            "Duplicated node should copy script content");

        String nodeConfigBody = apiGet("/nodes/" + encode(newNodeName) + "/files/contents?path=" +
            URLEncoder.encode("nodeConfig.json", StandardCharsets.UTF_8)).text();
        assertFalse(nodeConfigBody.contains(nodeConfigMarker), "Node config marker should not be copied");

        String filesBody = apiGet("/nodes/" + encode(newNodeName) + "/files").text();
        assertFalse(filesBody.contains("_ignore.txt"), "Excluded underscore files should not be copied");
        assertFalse(filesBody.contains("script_backup_"), "Backup scripts should not be copied");

        deleteTestNode(newNodeName);
    }

    @Test
    @Order(3)
    void testDuplicateNodeWithConfigCopy() throws Exception {
        openAddNodeDropdown();
        typeTemplateSearch(sourceNodeName);
        waitForAutocompleteItemInSection("Existing Nodes", sourceNodeName);
        selectAutocompleteItemInSection("Existing Nodes", sourceNodeName);

        Locator includeConfig = page.locator(".copy-config-option input.include-node-config").first();
        assertTrue(includeConfig.isVisible());
        includeConfig.check();

        String newNodeName = "E2E Duplicate On " + runId;
        page.locator(".nodel-add input.nodenamval").first().fill(newNodeName);
        page.locator(".nodel-add .nodeaddsubmit").first().click();

        assertTrue(waitForNodeInList(newNodeName, 15000), "Duplicated node should appear in node list");
        assertTrue(waitForFileContains(newNodeName, "script.py", sourceScriptMarker, 10000),
            "Duplicated node should copy script content");

        String nodeConfigBody = apiGet("/nodes/" + encode(newNodeName) + "/files/contents?path=" +
            URLEncoder.encode("nodeConfig.json", StandardCharsets.UTF_8)).text();
        assertTrue(nodeConfigBody.contains(nodeConfigMarker), "Node config marker should be copied");

        deleteTestNode(newNodeName);
    }

    @Test
    @Order(4)
    void testTemplateCardBodyClickBehavior() throws Exception {
        openAddNodeDropdown();
        typeTemplateSearch(sourceNodeName);
        waitForAutocompleteItemInSection("Existing Nodes", sourceNodeName);
        selectAutocompleteItemInSection("Existing Nodes", sourceNodeName);

        Locator card = page.locator(".template-selection-card").first();
        String address = card.getAttribute("data-address");
        assertNotNull(address);

        installWindowOpenSpy();
        card.locator(".card-body").click();
        assertEquals(address, getLastOpenedUrl(), "Node card body should open source node URL");

        card.locator(".card-header").click();
        page.waitForSelector(".template-selection-card",
            new Page.WaitForSelectorOptions().setState(WaitForSelectorState.DETACHED));
        assertTrue(page.locator(".unified-template-search").first().isVisible());

        typeTemplateSearch(recipeName);
        waitForAutocompleteItemInSection("Recipes", recipeName);
        selectAutocompleteItemInSection("Recipes", recipeName);

        Locator recipeCard = page.locator(".template-selection-card").first();
        clearOpenedUrls();
        recipeCard.locator(".card-body").click();
        assertNull(getLastOpenedUrl(), "Recipe card body should not open new tab");
    }

    private static void openAddNodeDropdown() {
        page.navigate(BASE_URL);
        waitForElement(".navbar");
        Locator dropdown = page.locator(".nodel-add .addgrp .dropdown-toggle").first();
        assumeTrue(dropdown.isVisible(), "Add node dropdown must exist");
        dropdown.click();
        Locator nodeNameInput = page.locator(".nodel-add input.nodenamval").first();
        assumeTrue(nodeNameInput.isVisible(), "Node name input must exist");
    }

    private static void typeTemplateSearch(String query) {
        Locator templateInput = page.locator(".nodel-add .unified-template-search").first();
        assumeTrue(templateInput.isVisible(), "Template search input must exist");
        templateInput.click();
        templateInput.fill("");
        templateInput.type(query);
        page.waitForSelector(".template-autocomplete");
    }

    private static void waitForAutocompleteItemInSection(String section, String text) {
        page.waitForFunction("(args) => {\n" +
            "  const items = Array.from(document.querySelectorAll('.template-autocomplete li'));\n" +
            "  let current = null;\n" +
            "  for (const li of items) {\n" +
            "    if (li.classList.contains('section-header')) {\n" +
            "      current = (li.textContent || '').trim();\n" +
            "      continue;\n" +
            "    }\n" +
            "    if (current === args.section && (li.textContent || '').includes(args.text)) return true;\n" +
            "  }\n" +
            "  return false;\n" +
            "}", java.util.Map.of("section", section, "text", text));
    }

    private static void selectAutocompleteItemInSection(String section, String... texts) {
        ElementHandle item = findAutocompleteItemInSection(section, texts);
        assertNotNull(item, "Autocomplete item must be found in section: " + section);
        item.dispatchEvent("mousedown");
        waitForElement(".template-selection-card");
    }

    private static ElementHandle findAutocompleteItemInSection(String section, String... texts) {
        List<ElementHandle> items = page.querySelectorAll(".template-autocomplete li");
        String currentSection = null;
        for (ElementHandle item : items) {
            String className = item.getAttribute("class");
            if (className != null && className.contains("section-header")) {
                currentSection = item.innerText().trim();
                continue;
            }
            if (currentSection == null || !currentSection.equals(section)) {
                continue;
            }
            String text = item.innerText();
            boolean matches = true;
            for (String needle : texts) {
                if (needle != null && !needle.isEmpty() && !text.contains(needle)) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return item;
            }
        }
        return null;
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

    private static boolean waitForRecipeEntry(String recipePath, int timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try {
                String body = apiGet("/recipes/list").text();
                if (body.contains(recipePath)) {
                    return true;
                }
                Thread.sleep(500);
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private static boolean waitForFileContains(String nodeName, String filePath, String expected, int timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        String encodedPath = URLEncoder.encode(filePath, StandardCharsets.UTF_8);
        while (System.currentTimeMillis() < deadline) {
            try {
                String body = apiGet("/nodes/" + encode(nodeName) + "/files/contents?path=" + encodedPath).text();
                if (body.contains(expected)) {
                    return true;
                }
                Thread.sleep(500);
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private static Path resolveTempDir() {
        Path candidate = Paths.get("nodelhost-temp");
        if (!Files.exists(candidate)) {
            candidate = Paths.get("nodel-jyhost/nodelhost-temp");
        }
        if (!Files.exists(candidate)) {
            throw new IllegalStateException("nodelhost-temp directory not found");
        }
        return candidate;
    }

    private static void writeRecipe(String name, String scriptContent) throws IOException {
        Path recipeDir = recipesDir.resolve(name);
        Files.createDirectories(recipeDir);
        Files.writeString(recipeDir.resolve("script.py"), scriptContent);
    }

    private static void deleteRecipe(String name) throws IOException {
        Path recipeDir = recipesDir.resolve(name);
        if (Files.exists(recipeDir)) {
            deleteDirectory(recipeDir);
        }
    }

    private static void writeNodeConfig(String nodeName, String json) throws IOException {
        Path nodeDir = nodesDir.resolve(nodeName);
        Files.writeString(nodeDir.resolve("nodeConfig.json"), json);
    }

    private static void writeSourceExtras(String nodeName) throws IOException {
        Path nodeDir = nodesDir.resolve(nodeName);
        Files.writeString(nodeDir.resolve("_ignore.txt"), "ignore");
        Files.writeString(nodeDir.resolve("script_backup_20240101.py"), "backup");
    }

    private static void deleteDirectory(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        Files.walk(dir)
            .sorted((a, b) -> b.compareTo(a))
            .forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
    }

    private static void installWindowOpenSpy() {
        page.evaluate("() => {\n" +
            "  window.__openedUrls = [];\n" +
            "  const original = window.open;\n" +
            "  window.__originalOpen = original;\n" +
            "  window.open = function(url, target) {\n" +
            "    window.__openedUrls.push({url: url, target: target});\n" +
            "    return null;\n" +
            "  };\n" +
            "}");
    }

    private static void clearOpenedUrls() {
        page.evaluate("() => { window.__openedUrls = []; }");
    }

    private static String getLastOpenedUrl() {
        Object result = page.evaluate("() => {\n" +
            "  const list = window.__openedUrls || [];\n" +
            "  if (!list.length) return null;\n" +
            "  return list[list.length - 1].url || null;\n" +
            "}");
        return result == null ? null : result.toString();
    }
}
