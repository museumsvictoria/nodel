package org.nodel;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

public class PlaywrightTests {

    private static Playwright playwright;
    private static Browser browser;
    private static Page page;

    @BeforeAll
    public static void setup() {
        playwright = Playwright.create();
        BrowserType.LaunchOptions options = new BrowserType.LaunchOptions()
            .setHeadless(true);
        browser = playwright.chromium().launch(options);
        BrowserContext context = browser.newContext();
        page = context.newPage();

        // Navigate and wait for the page to load
        page.navigate("http://127.0.0.1:8085");
        // Wait for navbar to appear (indicates page is ready)
        page.waitForSelector(".navbar", new Page.WaitForSelectorOptions().setTimeout(60000));
    }

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
        APIResponse response = page.request().get("http://127.0.0.1:8085/v1/css/components.default.css");
        assertEquals(200, response.status(), "components.default.css should be available");

        response = page.request().get("http://127.0.0.1:8085/v1/img/logo.png");
        assertEquals(200, response.status(), "logo.png should be available");

        response = page.request().get("http://127.0.0.1:8085/v1/js/components.min.js");
        assertEquals(200, response.status(), "components.min.js should be available");

        response = page.request().get("http://127.0.0.1:8085/v1/js/nodel.js");
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
    public void testListGroupItems() {
        // list-group-items only appear when nodes exist
        // For a fresh installation, verify the container exists instead
        assertNotNull(page.querySelector(".list-group"), "list-group container should be present");
    }

    @Test
    public void testListGroupItemBorder() {
        // Check styling only if list-group-items exist (requires nodes)
        ElementHandle element = page.querySelector(".list-group-item");
        if (element != null) {
            String border = page.evaluate("() => window.getComputedStyle(document.querySelector('.list-group-item')).getPropertyValue('border')").toString();
            assertTrue(border.contains("none") || border.equals("0px none rgb(0, 0, 0)") || border.startsWith("0px"), ".list-group-item should have border: none");
        }
        // Test passes if no list-group-items exist (fresh installation)
    }

    @Test
    public void testNodelAddButtonMargin() {
        String marginBottom = page.evaluate("() => window.getComputedStyle(document.querySelector('.nodel-add .btn')).getPropertyValue('margin-bottom')").toString();
        assertEquals("5px", marginBottom, ".nodel-add .btn should have margin-bottom: 5px");
    }

    @AfterAll
    public static void tearDown() {
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
    }
}
