package org.nodel;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for node management API operations.
 * Verifies node listing, discovery, and management endpoints.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class NodeManagementTests extends TestBase {

    @BeforeAll
    public static void setup() {
        initBrowser();
        navigateToHome();
    }

    @AfterAll
    public static void tearDown() {
        closeBrowser();
    }

    // ===== Node List API Tests =====

    @Test
    @Order(1)
    public void testNodeListEndpoint() {
        APIResponse response = apiGet("/nodes");
        assertEquals(200, response.status(), "Node list endpoint should return 200");
    }

    @Test
    @Order(2)
    public void testNodeListIsJson() {
        APIResponse response = apiGet("/nodes");
        String body = response.text();
        assertTrue(body.startsWith("[") || body.startsWith("{"),
            "Node list should return JSON");
    }

    @Test
    @Order(3)
    public void testAllNodesEndpoint() {
        APIResponse response = apiGet("/allNodes");
        assertEquals(200, response.status(), "All nodes endpoint should return 200");
    }

    @Test
    @Order(4)
    public void testNodeUrlsEndpoint() {
        APIResponse response = apiGet("/nodeURLs");
        assertEquals(200, response.status(), "Node URLs endpoint should return 200");
    }

    // ===== Recipes API Tests =====

    @Test
    @Order(10)
    public void testRecipesEndpoint() {
        APIResponse response = apiGet("/recipes");
        assertEquals(200, response.status(), "Recipes endpoint should return 200");
    }

    // ===== Discovery API Tests =====

    @Test
    @Order(15)
    public void testDiscoveryEndpoint() {
        APIResponse response = apiGet("/discovery");
        assertTrue(response.status() == 200 || response.status() == 204,
            "Discovery endpoint should return 200 or 204");
    }

    // ===== Version/Diagnostics Tests =====

    @Test
    @Order(20)
    public void testDiagnosticsEndpoint() {
        APIResponse response = apiGet("/diagnostics");
        assertEquals(200, response.status(), "Diagnostics endpoint should be available");
    }

    // ===== UI Element Tests =====

    @Test
    @Order(25)
    public void testAddNodeAreaExists() {
        ElementHandle addArea = page.querySelector(".nodel-add");
        assertNotNull(addArea, "Node add area (.nodel-add) should exist");
    }

    @Test
    @Order(26)
    public void testListGroupExists() {
        ElementHandle listGroup = page.querySelector(".list-group");
        assertNotNull(listGroup, "List group container for nodes should exist");
    }
}
