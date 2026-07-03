package org.nodel;

import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.jupiter.api.*;

import com.microsoft.playwright.APIResponse;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

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
        assertApiEventuallyContains("/recipes/list", recipeName, 10000,
            "Recipe list must be available for template search");

        sourceNodeName = "E2E Template Source " + runId;
        sourceScriptMarker = "source-script-marker-" + runId;
        String sourceScript = SIMPLE_TEST_SCRIPT + "\n# " + sourceScriptMarker + "\n";
        assertTrue(createTestNode(sourceNodeName, sourceScript), "Source node must be created");

        // fail fast if the host silently fell back to multicast discovery
        assertLocalDiscoveryActive();

        nodeConfigMarker = "config-marker-" + runId;
        String nodeConfigJson = "{\"remoteBindingValues\":{\"actions\":{\"testAction\":{\"node\":\"OtherNode\",\"action\":\"DoThing\"}},\"events\":{\"testEvent\":{\"node\":\"OtherNode\",\"event\":\"Triggered\"}}},\"paramValues\":{\"testParam\":\"" + nodeConfigMarker + "\"}}";
        writeNodeConfig(sourceNodeName, nodeConfigJson);
        writeSourceExtras(sourceNodeName);

        assertApiEventuallyContains("/nodeURLs", sourceNodeName, 30000,
            "Node URLs must be available for template search");
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
        assertApiEventuallyContains(fileContentsPath(newNodeName, "script.py"), recipeMarker, 10000,
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
        assertApiEventuallyContains(fileContentsPath(newNodeName, "script.py"), sourceScriptMarker, 10000,
            "Duplicated node should copy script content");

        APIResponse filesResponse = apiGet("/nodes/" + encode(newNodeName) + "/files");
        assertEquals(200, filesResponse.status(), "Files listing should be available for the duplicated node");
        String filesBody = filesResponse.text();
        assertFalse(filesBody.contains("_ignore.txt"), "Excluded underscore files should not be copied");
        assertFalse(filesBody.contains("script_backup_"), "Backup scripts should not be copied");

        // the node may regenerate a default nodeConfig.json of its own; what must not
        // happen is the source's configuration values arriving in it
        if (filesBody.contains("nodeConfig.json")) {
            APIResponse configResponse = apiGet(fileContentsPath(newNodeName, "nodeConfig.json"));
            assertEquals(200, configResponse.status(), "nodeConfig.json listed but not fetchable");
            assertFalse(configResponse.text().contains(nodeConfigMarker), "Node config marker should not be copied");
        }

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
        assertApiEventuallyContains(fileContentsPath(newNodeName, "script.py"), sourceScriptMarker, 10000,
            "Duplicated node should copy script content");

        assertApiEventuallyContains(fileContentsPath(newNodeName, "nodeConfig.json"), nodeConfigMarker, 10000,
            "Node config marker should be copied");

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
        try {
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
        } finally {
            restoreWindowOpenSpy();
        }
    }

    @Test
    @Order(5)
    void testPartialCopyFailureIsSurfacedWithoutRedirect() {
        openAddNodeDropdown();
        typeTemplateSearch(sourceNodeName);
        waitForAutocompleteItemInSection("Existing Nodes", sourceNodeName);
        selectAutocompleteItemInSection("Existing Nodes", sourceNodeName);

        Locator includeConfig = page.locator(".copy-config-option input.include-node-config").first();
        assertTrue(includeConfig.isVisible());
        includeConfig.check();

        String newNodeName = "E2E Duplicate Partial " + runId;

        // Abort only the nodeConfig.json save so exactly one file fails to copy
        page.route("**/REST/files/save*", route -> {
            if (route.request().url().contains("nodeConfig.json")) {
                route.abort();
            } else {
                route.resume();
            }
        });
        try {
            page.locator(".nodel-add input.nodenamval").first().fill(newNodeName);
            page.locator(".nodel-add .nodeaddsubmit").first().click();

            // the failure must be surfaced as a warning...
            page.waitForSelector(".alert.alert-warning", new Page.WaitForSelectorOptions().setTimeout(60000));
            String alertText = page.locator(".alert").innerText();
            assertTrue(alertText.contains("failed to copy"), "Warning should mention the failed copy, was: " + alertText);
            assertTrue(alertText.contains("nodeConfig.json"), "Warning should identify the failed file, was: " + alertText);

            // ...and must not be lost to an automatic redirect: wait out the redirect window
            // (an intentional fixed wait — we are asserting that navigation does NOT happen)
            page.waitForTimeout(3000);
            assertFalse(page.url().contains("/nodes/"),
                "Should remain on the host page when copies failed, was: " + page.url());
            assertTrue(page.locator(".alert.alert-warning").isVisible(),
                "Warning should still be visible after the redirect window");

            // partial success: the script itself was still copied
            assertApiEventuallyContains(fileContentsPath(newNodeName, "script.py"), sourceScriptMarker, 10000,
                "Script should still be copied when only nodeConfig.json fails");
        } finally {
            page.unroute("**/REST/files/save*");
            deleteTestNode(newNodeName);
        }
    }

    @Test
    @Order(6)
    void testSelectionCardEscapesHostileNodeMetadata() {
        openAddNodeDropdown();

        String hostileAddress = "http://127.0.0.1:18085/\" onmouseover=\"window.__xss=1";
        String hostileName = "Evil Node -- <img src=x onerror=\"window.__xss2=1\">";
        String hostileHost = "evil\"><script>window.__xss3=1</script>";

        // Drive the real selection handler with attacker-influenced metadata,
        // as a malicious advertisement on the network could supply
        page.evaluate("(args) => {\n" +
            "  var $input = $('.nodel-add .unified-template-search').first();\n" +
            "  $input.siblings('.template-autocomplete').remove();\n" +
            "  var $autocomplete = $('<div class=\"template-autocomplete\"><ul></ul></div>');\n" +
            "  var $item = $('<li/>');\n" +
            "  $item.data('type', 'node');\n" +
            "  $item.data('name', args.name);\n" +
            "  $item.data('host', args.host);\n" +
            "  $item.data('address', args.address);\n" +
            "  $autocomplete.find('ul').append($item);\n" +
            "  $input.after($autocomplete);\n" +
            "  $item.trigger('mousedown');\n" +
            "}", Map.of("name", hostileName, "host", hostileHost, "address", hostileAddress));

        waitForElement(".template-selection-card");
        assertEquals(1, page.locator(".template-selection-card").count(), "Exactly one card should render");

        Locator card = page.locator(".template-selection-card").first();
        assertEquals(hostileAddress, card.getAttribute("data-address"),
            "data-address must carry the value verbatim without breaking out of the attribute");
        assertNull(card.getAttribute("onmouseover"), "No event-handler attribute may be injected");
        assertEquals(0, card.locator("img, script").count(), "No elements may be injected via name/host");
        Object executed = page.evaluate("() => window.__xss || window.__xss2 || window.__xss3 || null");
        assertNull(executed, "No injected script may execute");
    }

    private static void openAddNodeDropdown() {
        page.navigate(BASE_URL);
        // the dropdown sits inside div.page, which stays display:none until nodel.js's
        // init reveals the active section - well after .navbar (static XSLT output)
        // renders, so wait for the control itself rather than the navbar. A timeout
        // here still fails (not skips) the suite if these elements regress.
        waitForElement(".nodel-add .addgrp .dropdown-toggle");
        Locator dropdown = page.locator(".nodel-add .addgrp .dropdown-toggle").first();
        dropdown.click();
        Locator nodeNameInput = page.locator(".nodel-add input.nodenamval").first();
        assertTrue(nodeNameInput.isVisible(), "Node name input must exist");
    }

    private static void typeTemplateSearch(String query) {
        Locator templateInput = page.locator(".nodel-add .unified-template-search").first();
        assertTrue(templateInput.isVisible(), "Template search input must exist");
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

    /**
     * REST path for a file's contents within a node.
     */
    private static String fileContentsPath(String nodeName, String filePath) {
        return "/nodes/" + encode(nodeName) + "/files/contents?path="
            + URLEncoder.encode(filePath, StandardCharsets.UTF_8);
    }

    private static void writeRecipe(String name, String scriptContent) throws IOException {
        Path recipeDir = recipesDir.resolve(name);
        Files.createDirectories(recipeDir);
        Files.writeString(recipeDir.resolve("script.py"), scriptContent);
    }

    private static void deleteRecipe(String name) throws IOException {
        deleteDirectoryRecursively(recipesDir.resolve(name));
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

    private static void installWindowOpenSpy() {
        page.evaluate("() => {\n" +
            "  window.__openedUrls = [];\n" +
            "  const original = window.open;\n" +
            "  window.__originalOpen = original;\n" +
            "  window.open = function(url, target) {\n" +
            "    window.__openedUrls.push({url: url, target: target});\n" +
            "    return window; // truthy, so the popup-blocked fallback is not triggered\n" +
            "  };\n" +
            "}");
    }

    private static void restoreWindowOpenSpy() {
        page.evaluate("() => {\n" +
            "  if (window.__originalOpen) {\n" +
            "    window.open = window.__originalOpen;\n" +
            "    delete window.__originalOpen;\n" +
            "  }\n" +
            "  delete window.__openedUrls;\n" +
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
