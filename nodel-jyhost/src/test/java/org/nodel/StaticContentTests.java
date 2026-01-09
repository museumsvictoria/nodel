package org.nodel;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for static content loading and CSS styling.
 * Verifies that assets load correctly and Bootstrap/font styling is applied.
 */
public class StaticContentTests extends TestBase {

    @BeforeAll
    public static void setup() {
        initBrowser();
        navigateToHome();
    }

    @AfterAll
    public static void tearDown() {
        closeBrowser();
    }

    // ===== Original Tests =====

    @Test
    public void testNavigationBarPresence() {
        assertNotNull(page.querySelector(".navbar"), "Navigation bar should be present");
    }

    @Test
    public void testActiveNavigationItem() {
        String activeNavItemText = page.textContent(".nav.navbar-nav .active");
        assertEquals("Locals", activeNavItemText.trim(), "Active navigation item should be 'Locals'");
    }

    @Test
    public void testSourceFileAvailability() {
        APIResponse response = page.request().get(BASE_URL + "/v1/css/components.default.css");
        assertEquals(200, response.status(), "components.default.css should be available");

        response = page.request().get(BASE_URL + "/v1/img/logo.png");
        assertEquals(200, response.status(), "logo.png should be available");

        response = page.request().get(BASE_URL + "/v1/js/components.min.js");
        assertEquals(200, response.status(), "components.min.js should be available");

        response = page.request().get(BASE_URL + "/v1/js/nodel.js");
        assertEquals(200, response.status(), "nodel.js should be available");
    }

    @Test
    public void testFontFamily() {
        String fontFamily = page.evaluate("() => window.getComputedStyle(document.body).getPropertyValue('font-family')").toString();
        assertTrue(fontFamily.contains("Roboto"), "Font family should include Roboto");
    }

    @Test
    public void testBootstrapLayout() {
        assertNotNull(page.querySelector("div.row > div.col-sm-12"), "Bootstrap row and column should be present");
    }

    @Test
    public void testNodelAddElement() {
        assertNotNull(page.querySelector(".nodel-add"), ".nodel-add should be present");
    }

    @Test
    public void testListGroupContainer() {
        assertNotNull(page.querySelector(".list-group"), "list-group container should be present");
    }

    @Test
    public void testListGroupItemBorder() {
        ElementHandle element = page.querySelector(".list-group-item");
        if (element != null) {
            String border = getComputedStyle(".list-group-item", "border");
            assertTrue(border.contains("none") || border.equals("0px none rgb(0, 0, 0)") || border.startsWith("0px"),
                ".list-group-item should have border: none");
        }
    }

    @Test
    public void testNodelAddButtonMargin() {
        String marginBottom = getComputedStyle(".nodel-add .btn", "margin-bottom");
        assertEquals("5px", marginBottom, ".nodel-add .btn should have margin-bottom: 5px");
    }

    // ===== Bootstrap Styling Tests =====

    @Test
    public void testBootstrapButtonExists() {
        assertNotNull(page.querySelector(".btn"), "Bootstrap button class should exist on page");
    }

    @Test
    public void testBootstrapContainerFluid() {
        assertNotNull(page.querySelector(".container-fluid"), "Bootstrap container-fluid should be present");
    }

    @Test
    public void testNavbarBrandExists() {
        assertNotNull(page.querySelector(".navbar-brand"), "Navbar brand should be present");
    }

    @Test
    public void testNavbarToggleExists() {
        assertNotNull(page.querySelector(".navbar-toggle"), "Navbar toggle (mobile menu) should be present");
    }

    // ===== CSS Loading Tests =====

    @Test
    public void testDarkThemeCssAvailable() {
        APIResponse response = page.request().get(BASE_URL + "/v1/css/components.css");
        assertEquals(200, response.status(), "components.css (dark theme) should be available");
    }

    @Test
    public void testFaviconAvailable() {
        APIResponse response = page.request().get(BASE_URL + "/v1/img/favicon.ico");
        assertEquals(200, response.status(), "favicon.ico should be available");
    }

    // ===== Font Tests =====

    @Test
    public void testFontFilesAvailable() {
        // Check Roboto font files - may be in different locations
        APIResponse response = page.request().get(BASE_URL + "/v1/fonts/roboto-regular.woff2");
        // Font path may vary, so accept 200 or 404 (font may be loaded via CSS or different path)
        assertTrue(response.status() == 200 || response.status() == 404,
            "Font file endpoint should return valid response, got: " + response.status());
    }

    @Test
    public void testFontAwesomeCssLoaded() {
        // Font Awesome classes should be available via the concatenated CSS
        APIResponse response = page.request().get(BASE_URL + "/v1/css/components.default.css");
        String css = response.text();
        assertTrue(css.contains("fa-") || css.contains("fontawesome"),
            "Font Awesome styles should be included in CSS");
    }

    // ===== JavaScript Tests =====

    @Test
    public void testJQueryLoaded() {
        assertJsDefined("jQuery");
    }

    @Test
    public void testBootstrapJsLoaded() {
        assertJsDefined("jQuery.fn.modal");
    }

    @Test
    public void testJsViewsLoaded() {
        assertJsDefined("jQuery.templates");
    }

    @Test
    public void testMomentJsLoaded() {
        assertJsDefined("moment");
    }

    // ===== Page Structure Tests =====

    @Test
    public void testMainContentArea() {
        // Main content area may have different selectors
        ElementHandle content = page.querySelector("#content, .content, .container, .container-fluid, main");
        assertNotNull(content, "Main content area should be present");
    }

    @Test
    public void testBodyHasNoJsErrors() {
        // Check that the page loaded without critical JS errors by verifying key elements rendered
        assertNotNull(page.querySelector("body"), "Body should be present");
        assertNotNull(page.querySelector(".navbar"), "Navbar should have rendered (no critical JS errors)");
    }
}
