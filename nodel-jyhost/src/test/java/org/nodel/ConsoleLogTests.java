package org.nodel;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for console and log API functionality.
 * Verifies log retrieval endpoints and time formatting.
 */
public class ConsoleLogTests extends TestBase {

    @BeforeAll
    public static void setup() {
        initBrowser();
        navigateToHome();
    }

    @AfterAll
    public static void tearDown() {
        closeBrowser();
    }

    // ===== System Logs API Tests =====

    @Test
    public void testSystemLogsEndpoint() {
        APIResponse response = apiGet("/logs");
        assertEquals(200, response.status(), "System logs endpoint should return 200");
    }

    @Test
    public void testSystemLogsReturnsData() {
        APIResponse response = apiGet("/logs");
        String body = response.text();
        assertTrue(body.length() > 0, "System logs should return data");
    }

    @Test
    public void testWarningLogsEndpoint() {
        APIResponse response = apiGet("/warningLogs");
        assertEquals(200, response.status(), "Warning logs endpoint should return 200");
    }

    @Test
    public void testLogsReturnsJson() {
        APIResponse response = apiGet("/logs");
        String contentType = response.headers().get("content-type");
        assertTrue(contentType != null && contentType.contains("application/json"),
            "Logs endpoint should return JSON");
    }

    // ===== Log Pagination Tests =====

    @Test
    public void testLogsWithFromParameter() {
        APIResponse response = apiGet("/logs?from=0");
        assertEquals(200, response.status(), "Logs with from parameter should return 200");
    }

    @Test
    public void testLogsWithMaxParameter() {
        APIResponse response = apiGet("/logs?max=10");
        assertEquals(200, response.status(), "Logs with max parameter should return 200");
    }

    @Test
    public void testLogsWithBothParameters() {
        APIResponse response = apiGet("/logs?from=0&max=10");
        assertEquals(200, response.status(), "Logs with from and max parameters should return 200");
    }

    // ===== Moment.js Time Formatting (used for log display) =====
    // Note: Moment.js loading is tested in StaticContentTests; here we test formatting

    @Test
    public void testMomentJsFormatting() {
        Object result = page.evaluate("() => moment().format('YYYY-MM-DD')");
        String formatted = result.toString();
        assertTrue(formatted.matches("\\d{4}-\\d{2}-\\d{2}"), "Moment.js should format dates correctly");
    }
}
