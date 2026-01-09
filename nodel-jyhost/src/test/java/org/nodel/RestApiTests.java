package org.nodel;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for REST API endpoints.
 * Verifies that all REST endpoints respond correctly with expected status codes and data structures.
 */
public class RestApiTests extends TestBase {

    @BeforeAll
    public static void setup() {
        initBrowser();
        navigateToHome();
    }

    @AfterAll
    public static void tearDown() {
        closeBrowser();
    }

    // ===== Node List Endpoints =====

    @Test
    public void testGetNodes() {
        APIResponse response = apiGet("/nodes");
        assertEquals(200, response.status(), "GET /REST/nodes should return 200");
        String body = response.text();
        assertTrue(body.startsWith("[") || body.startsWith("{"), "Response should be JSON");
    }

    @Test
    public void testGetNodesReturnsJson() {
        APIResponse response = apiGet("/nodes");
        String body = response.text();
        // Nodes can return array [] or object {} depending on configuration
        assertTrue(body.startsWith("[") || body.startsWith("{"),
            "GET /REST/nodes should return JSON (array or object)");
    }

    // ===== Diagnostics Endpoints =====

    @Test
    public void testGetDiagnostics() {
        assertEndpointOk("/diagnostics");
    }

    @Test
    public void testDiagnosticsContainsData() {
        APIResponse response = apiGet("/diagnostics");
        String body = response.text();
        assertTrue(body.length() > 10, "Diagnostics should contain data");
    }

    // ===== Toolkit Endpoint =====

    @Test
    public void testGetToolkit() {
        assertEndpointOk("/toolkit");
    }

    @Test
    public void testToolkitContainsDocumentation() {
        APIResponse response = apiGet("/toolkit");
        String body = response.text();
        assertTrue(body.contains("TCP") || body.contains("UDP") || body.contains("Timer") || body.contains("console"),
            "Toolkit should contain API documentation");
    }

    // ===== Discovery Endpoint =====

    @Test
    public void testGetDiscovery() {
        assertEndpointOkOrEmpty("/discovery");
    }

    // ===== All Nodes Endpoint =====

    @Test
    public void testGetAllNodes() {
        assertEndpointOk("/allNodes");
    }

    // ===== Node URLs Endpoint =====

    @Test
    public void testGetNodeURLs() {
        assertEndpointOk("/nodeURLs");
    }

    // ===== Logs Endpoint =====

    @Test
    public void testGetLogs() {
        assertEndpointOk("/logs");
    }

    @Test
    public void testGetWarningLogs() {
        assertEndpointOk("/warningLogs");
    }

    // ===== Recipes Endpoint =====

    @Test
    public void testGetRecipes() {
        assertEndpointOk("/recipes");
    }

    // ===== Error Handling =====

    @Test
    public void testInvalidEndpoint404() {
        APIResponse response = apiGet("/nonexistent-endpoint-12345");
        assertEquals(404, response.status(), "Invalid endpoint should return 404");
    }

    @Test
    public void testInvalidNodeEndpoint() {
        APIResponse response = apiGet("/nodes/NonExistentNode12345XYZ/console");
        // Should return 404 or similar error for non-existent node
        assertTrue(response.status() >= 400, "Non-existent node endpoint should return error status");
    }

    // ===== Content Type Tests =====

    @Test
    public void testNodesReturnsJson() {
        APIResponse response = apiGet("/nodes");
        String contentType = response.headers().get("content-type");
        assertTrue(contentType != null && contentType.contains("application/json"),
            "GET /REST/nodes should return JSON content type");
    }

    @Test
    public void testDiagnosticsReturnsJson() {
        APIResponse response = apiGet("/diagnostics");
        String contentType = response.headers().get("content-type");
        assertTrue(contentType != null && contentType.contains("application/json"),
            "GET /REST/diagnostics should return JSON content type");
    }
}
