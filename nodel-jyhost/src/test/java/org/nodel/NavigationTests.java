package org.nodel;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for page navigation and URL routing.
 * Verifies that navigation works and pages are accessible.
 */
public class NavigationTests extends TestBase {

    @BeforeAll
    public static void setup() {
        initBrowser();
        navigateToHome();
    }

    @AfterAll
    public static void tearDown() {
        closeBrowser();
    }

    // ===== URL Accessibility Tests =====

    @Test
    public void testBaseUrlAccessible() {
        APIResponse response = page.request().get(BASE_URL + "/");
        assertEquals(200, response.status(), "Base URL should be accessible");
    }

    @Test
    public void testLocalsPageAccessible() {
        APIResponse response = page.request().get(BASE_URL + "/locals.xml");
        assertEquals(200, response.status(), "Locals page should be accessible");
    }

    @Test
    public void testNodesPageAccessible() {
        APIResponse response = page.request().get(BASE_URL + "/nodes.xml");
        assertEquals(200, response.status(), "Nodes page should be accessible");
    }

    @Test
    public void testDiagnosticsPageAccessible() {
        APIResponse response = page.request().get(BASE_URL + "/diagnostics.xml");
        assertEquals(200, response.status(), "Diagnostics page should be accessible");
    }

    // ===== Navigation Element Tests =====

    @Test
    public void testNavbarExists() {
        assertNotNull(page.querySelector(".navbar"), "Navbar should exist");
    }

    @Test
    public void testNavbarBrandExists() {
        assertNotNull(page.querySelector(".navbar-brand"), "Navbar brand should exist");
    }

    @Test
    public void testActiveNavItemExists() {
        ElementHandle activeItem = page.querySelector(".navbar-nav .active, .nav.navbar-nav .active");
        assertNotNull(activeItem, "An active navigation item should exist");
    }

    // ===== Page Reload Test =====

    @Test
    public void testPageCanBeReloaded() {
        page.reload();
        page.waitForSelector(".navbar", new Page.WaitForSelectorOptions().setTimeout(30000));
        assertNotNull(page.querySelector(".navbar"), "Page should reload successfully");
    }
}
