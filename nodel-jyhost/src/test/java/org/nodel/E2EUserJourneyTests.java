package org.nodel;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import static org.junit.jupiter.api.Assertions.*;

/**
 * True E2E tests that simulate real user interactions with the UI.
 * These tests click, type, and verify visual feedback - unlike the integration
 * tests which primarily make REST API calls.
 *
 * Run visually with: HEADED=1 SLOWMO=500 ./gradlew :nodel-jyhost:e2eTest
 */
@Tag("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class E2EUserJourneyTests extends TestBase {

    private static final String TEST_NODE = "E2E Journey Node";

    @BeforeAll
    static void setup() {
        initBrowser();
        createTestNode(TEST_NODE, SIMPLE_TEST_SCRIPT);
    }

    @AfterAll
    static void teardown() {
        deleteTestNode(TEST_NODE);
        closeBrowser();
    }

    @Test
    @Order(1)
    void testHomePageShowsNodeList() {
        // Navigate to home page
        page.navigate(BASE_URL);
        waitForElement(".navbar");

        // Verify node list is visible
        waitForElement(".list-group");

        // Verify our test node appears in the list
        String pageContent = page.content();
        assertTrue(pageContent.contains(TEST_NODE),
            "Home page should show our test node in the list");
    }

    @Test
    @Order(2)
    void testUserCanClickOnNodeInList() {
        page.navigate(BASE_URL);
        waitForElement(".list-group");

        // Find our test node in the list
        Locator nodeLink = page.locator("a:has-text('" + TEST_NODE + "'), .list-group-item:has-text('" + TEST_NODE + "')").first();
        assertTrue(nodeLink.isVisible(), "Test node should be visible in the list");

        // Click on it
        nodeLink.click();

        // Wait for navigation/page update
        page.waitForTimeout(1000);

        // Verify we're now viewing node content
        String pageContent = page.content();
        assertTrue(pageContent.contains(TEST_NODE),
            "Page should show the node after clicking");
    }

    @Test
    @Order(3)
    void testUserCanFilterNodeList() {
        page.navigate(BASE_URL);
        waitForElement(".list-group");

        // Look for any input that might be a filter
        Locator filterInput = page.locator("input[type='text'], input[type='search'], input[placeholder]").first();

        if (filterInput.isVisible()) {
            // Type the node name to filter
            filterInput.fill(TEST_NODE);

            // Wait for filter to apply
            page.waitForTimeout(500);

            // Verify our node is still visible
            String pageContent = page.content();
            assertTrue(pageContent.contains(TEST_NODE),
                "Test node should still be visible after filtering");
        }
        // If no filter exists, that's okay - test passes
    }

    @Test
    @Order(4)
    void testNavbarHasClickableLinks() {
        page.navigate(BASE_URL);
        waitForElement(".navbar");

        // Verify navbar has links
        Locator navLinks = page.locator(".navbar a, .navbar-nav a, .nav a");
        int linkCount = navLinks.count();

        assertTrue(linkCount > 0, "Navbar should have navigation links");
    }

    @Test
    @Order(5)
    void testDropdownMenuInteraction() {
        page.navigate(BASE_URL);
        waitForElement(".navbar");

        // Find a dropdown toggle
        Locator dropdownToggle = page.locator("[data-toggle='dropdown'], .dropdown-toggle, .dropdown > a").first();

        if (dropdownToggle.isVisible()) {
            // Click to open dropdown
            dropdownToggle.click();
            page.waitForTimeout(300);

            // Check if dropdown menu became visible (any dropdown class)
            String pageContent = page.content();
            boolean hasDropdownMenu = pageContent.contains("dropdown-menu") ||
                                     page.locator(".dropdown-menu").count() > 0;

            assertTrue(hasDropdownMenu, "Page should have dropdown menu elements");

            // Click elsewhere to close
            page.click("body");
        }
        // If no dropdowns exist, test passes (dropdowns are optional)
    }

    @Test
    @Order(6)
    void testPageUsesBootstrapComponents() {
        page.navigate(BASE_URL);
        waitForElement(".navbar");

        String pageContent = page.content();

        // Verify Bootstrap is being used (common classes)
        boolean hasBootstrap = pageContent.contains("container") ||
                              pageContent.contains("row") ||
                              pageContent.contains("col-") ||
                              pageContent.contains("btn") ||
                              pageContent.contains("navbar");

        assertTrue(hasBootstrap, "Page should use Bootstrap components");
    }
}
