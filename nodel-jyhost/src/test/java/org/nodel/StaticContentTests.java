package org.nodel;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for static content and asset loading.
 * Verifies that CSS, JS, and other assets are served correctly.
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

    // ===== Static Asset Availability =====

    @Test
    public void testCssAvailable() {
        APIResponse response = page.request().get(BASE_URL + "/v1/css/components.default.css");
        assertEquals(200, response.status(), "components.default.css should be available");
    }

    @Test
    public void testLogoAvailable() {
        APIResponse response = page.request().get(BASE_URL + "/v1/img/logo.png");
        assertEquals(200, response.status(), "logo.png should be available");
    }

    @Test
    public void testComponentsJsAvailable() {
        APIResponse response = page.request().get(BASE_URL + "/v1/js/components.min.js");
        assertEquals(200, response.status(), "components.min.js should be available");
    }

    @Test
    public void testNodelJsAvailable() {
        APIResponse response = page.request().get(BASE_URL + "/v1/js/nodel.js");
        assertEquals(200, response.status(), "nodel.js should be available");
    }

    @Test
    public void testFaviconAvailable() {
        APIResponse response = page.request().get(BASE_URL + "/v1/img/favicon.ico");
        assertEquals(200, response.status(), "favicon.ico should be available");
    }

    @Test
    public void testDarkThemeCssAvailable() {
        APIResponse response = page.request().get(BASE_URL + "/v1/css/components.css");
        assertEquals(200, response.status(), "components.css (dark theme) should be available");
    }

    // ===== JavaScript Library Loading =====

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

    // ===== Page Structure =====

    @Test
    public void testNavbarExists() {
        assertNotNull(page.querySelector(".navbar"), "Navbar should be present");
    }

    @Test
    public void testActiveNavigationItem() {
        String activeNavItemText = page.textContent(".nav.navbar-nav .active");
        assertEquals("Locals", activeNavItemText.trim(), "Active navigation item should be 'Locals'");
    }

    @Test
    public void testNodelAddElement() {
        assertNotNull(page.querySelector(".nodel-add"), ".nodel-add should be present");
    }

    @Test
    public void testListGroupContainer() {
        assertNotNull(page.querySelector(".list-group"), "list-group container should be present");
    }

    // ===== CSS Styling Verification =====

    @Test
    public void testFontFamily() {
        String fontFamily = page.evaluate("() => window.getComputedStyle(document.body).getPropertyValue('font-family')").toString();
        assertTrue(fontFamily.contains("Roboto"), "Font family should include Roboto");
    }

    @Test
    public void testBootstrapLayout() {
        assertNotNull(page.querySelector("div.row > div.col-sm-12"), "Bootstrap row and column should be present");
    }
}
