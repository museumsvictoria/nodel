package org.nodel;

import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("e2e")
public class LocalsNameReductionE2ETests extends TestBase {

    private static String PAREN_RAW;
    private static String DASH_RAW;
    private static String SLASH_RAW;

    private static String PAREN_CANONICAL;
    private static String DASH_CANONICAL;
    private static String SLASH_CANONICAL;

    @BeforeAll
    static void setup() {
        initBrowser();

        String suffix = String.valueOf(System.currentTimeMillis());
        PAREN_RAW = "Issue235Paren " + suffix + " (Meta)";
        DASH_RAW = "Issue235Dash " + suffix + " -- Meta";
        SLASH_RAW = "Issue235Slash " + suffix + " // Meta";

        PAREN_CANONICAL = reduceNodeNameForPath(PAREN_RAW);
        DASH_CANONICAL = reduceNodeNameForPath(DASH_RAW);
        SLASH_CANONICAL = reduceNodeNameForPath(SLASH_RAW);

        createNodeAndWait(PAREN_RAW, PAREN_CANONICAL);
        createNodeAndWait(DASH_RAW, DASH_CANONICAL);
        createNodeAndWait(SLASH_RAW, SLASH_CANONICAL);
    }

    @AfterAll
    static void teardown() {
        tryDeleteCanonicalNode(PAREN_CANONICAL);
        tryDeleteCanonicalNode(DASH_CANONICAL);
        tryDeleteCanonicalNode(SLASH_CANONICAL);
        closeBrowser();
    }

    @Test
    void testFrontendReducerMatchesCanonicalReductionForIssue235Names() {
        page.navigate(BASE_URL + "/locals.xml#Locals");
        waitForElement(".nodel-locals .list-group");

        String reducedParen = page.evaluate("(name) => getVerySimpleName(name)", PAREN_RAW).toString();
        String reducedDash = page.evaluate("(name) => getVerySimpleName(name)", DASH_RAW).toString();
        String reducedSlash = page.evaluate("(name) => getVerySimpleName(name)", SLASH_RAW).toString();

        assertEquals(PAREN_CANONICAL, reducedParen);
        assertEquals(DASH_CANONICAL, reducedDash);
        assertEquals(SLASH_CANONICAL, reducedSlash);
    }

    @Test
    void testLocalsLinksNavigateToCanonicalNodePaths() {
        assertLocalsLinkNavigates(PAREN_RAW, PAREN_CANONICAL);
        assertLocalsLinkNavigates(DASH_RAW, DASH_CANONICAL);
        assertLocalsLinkNavigates(SLASH_RAW, SLASH_CANONICAL);
    }

    private static void createNodeAndWait(String rawName, String canonicalName) {
        APIResponse response = apiPost("/newNode", "{\"value\":\"" + rawName + "\"}");
        assertTrue(response.status() == 200 || response.status() == 204,
                "Creating test node should succeed for: " + rawName + ", got " + response.status());

        assertTrue(waitForNodeInList(rawName, 20000),
                "Node should appear in /REST/nodes list: " + rawName);

        assertTrue(waitForCanonicalNodeResponsive(canonicalName, 20000),
                "Canonical node path should become responsive: " + canonicalName);
    }

    private static void tryDeleteCanonicalNode(String canonicalName) {
        if (canonicalName == null || canonicalName.isEmpty()) {
            return;
        }

        try {
            page.request().get(BASE_URL + "/nodes/" + canonicalName + "/REST/remove?confirm=true");
        } catch (Exception ignored) {
            // Best-effort cleanup only.
        }
    }

    private static boolean waitForCanonicalNodeResponsive(String canonicalName, int timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try {
                APIResponse response = page.request().get(BASE_URL + "/nodes/" + canonicalName + "/REST/actions");
                if (response.status() == 200) {
                    return true;
                }
                Thread.sleep(250);
            } catch (Exception ignored) {
                // Continue polling.
            }
        }
        return false;
    }

    private void assertLocalsLinkNavigates(String rawName, String canonicalName) {
        page.navigate(BASE_URL + "/locals.xml#Locals");
        waitForElement(".nodel-locals .list-group");

        Locator nodeLink = page.locator(".nodel-locals .list-group .list-group-item:has-text('" + rawName + "')").first();
        assertTrue(nodeLink.isVisible(), "Locals entry should be visible for: " + rawName);

        nodeLink.click();

        assertTrue(waitForUrlContains("/nodes/" + canonicalName + "/", 15000),
                "Clicking locals entry should navigate to canonical node path for: " + rawName);
        waitForElement(".navbar");
    }

    private static boolean waitForUrlContains(String expectedPart, int timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try {
                if (page.url().contains(expectedPart)) {
                    return true;
                }
                Thread.sleep(250);
            } catch (Exception ignored) {
                // Continue polling.
            }
        }
        return false;
    }

    // Mirrors backend Nodel.reduce for path-key expectation in tests.
    private static String reduceNodeNameForPath(String name) {
        StringBuilder sb = new StringBuilder(name.length());
        char lastChar = 0;
        int commentLevel = 0;

        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);

            if (c == '(') {
                commentLevel += 1;
            } else if (commentLevel > 0) {
                if (c == ')') {
                    commentLevel -= 1;
                }
            } else if (c == '-' && lastChar == '-' || c == '/' && lastChar == '/') {
                break;
            } else if (Character.isLetterOrDigit(c)) {
                sb.append(c);
            } else if (c > 127 && !Character.isSpaceChar(c)) {
                sb.append(c);
            }

            lastChar = c;
        }

        return sb.toString();
    }
}
