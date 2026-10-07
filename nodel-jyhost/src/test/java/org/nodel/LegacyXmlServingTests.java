package org.nodel;

import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.RequestOptions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nodel.jyhost.NodelHostHTTPD;
import org.nanohttpd.protocols.http.request.Request;

import java.io.IOException;
import java.io.File;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;

/** Same-URL HTTP representation and browser/runtime compatibility, without native XSLT. */
public class LegacyXmlServingTests extends TestBase {
    private static final String NODE = "Legacy XML Serving";
    private static final String DEFAULT_NODE = "Legacy XML Default";
    private static final String PI = "<?xml version='1.0' encoding='UTF-8'?>\r\n"
            + "<?xml-stylesheet type='text/xsl' href='./v1/index.xsl'?>\r\n";
    private static final String XML = PI + "<pages title='Legacy fixture'><page title='Main'><row><column>"
            + "<title>Same URL café</title><button action='testAction' arg='browser-live'>Run</button>"
            + "<text event='Status'>Waiting</text></column></row></page></pages>\r\n";
    private static final String BROKEN_PI_XML = "<?other?broken?>" + XML.substring(XML.indexOf("<?xml-stylesheet"));
    private static Path custom, nodeContent;
    private static final List<Path> hostFixtures = new ArrayList<>();

    @BeforeAll static void setup() throws IOException {
        initBrowser();
        // This guard rejects JS XSLT use; it is not a browser-native XSLT disable switch.
        context.addInitScript("window.XSLTProcessor = function() { throw new Error('Native XSLT must not be used'); };");
        assertTrue(createTestNode(NODE, SIMPLE_TEST_SCRIPT), "fixture node must initialise");
        assertTrue(createTestNode(DEFAULT_NODE, SIMPLE_TEST_SCRIPT), "default node must initialise");
        custom = resolveTempDir().resolve("custom/content");
        nodeContent = resolveTempDir().resolve("nodes").resolve(NODE).resolve("content");
        Files.createDirectories(nodeContent);
        hostFile("legacy-serving.XML", XML);
        hostFile("legacy-data.xml", "<data>raw café</data>\r\n");
        hostFile("legacy-dtd.xml", "<!DOCTYPE pages>" + XML);
        hostFile("legacy-broken-pi.xml", BROKEN_PI_XML);
        hostFile("legacy-precedence.xml", XML);
        Files.writeString(nodeContent.resolve("index.xml"), XML);
        Files.writeString(nodeContent.resolve("legacy-precedence.xml"), "<node>override without stylesheet</node>");
        Files.writeString(nodeContent.resolve("legacy-python.xml"), XML);
        Files.writeString(nodeContent.resolve("legacy-python.xml.pysp"), "python-page-wins");
    }

    private static void hostFile(String name, String text) throws IOException {
        Path path = custom.resolve(name);
        assertFalse(Files.exists(path), "isolated fixture must not overwrite existing content");
        hostFixtures.add(path);
        Files.writeString(path, text);
    }

    @AfterAll static void teardown() throws IOException {
        try {
            for (Path path : hostFixtures) Files.deleteIfExists(path);
            deleteTestNode(NODE);
            deleteTestNode(DEFAULT_NODE);
        } finally {
            closeBrowser();
        }
    }

    @BeforeEach void newPage() { recreatePage(); }

    private static String nodeUrl() { return BASE_URL + "/nodes/" + getReducedName(NODE) + "/"; }

    private static void assertHtml(APIResponse response) {
        assertEquals(200, response.status());
        assertEquals("text/html; charset=utf-8", response.headers().get("content-type"));
        assertEquals("no-store", response.headers().get("cache-control"));
        for (String header : new String[] { "etag", "content-range", "accept-ranges", "location" })
            assertFalse(response.headers().containsKey(header), header);
        assertTrue(response.text().contains("NODEL_LEGACY_REVEAL_GATED = true"));
        assertTrue(response.text().contains("src=\"/legacy-loader.js\""));
        assertFalse(response.text().contains("<base"));
    }

    @Test void getHeadAndConstantBootstrapAtHostAndNodeUrls() {
        for (String url : new String[] { BASE_URL + "/legacy-serving.XML", nodeUrl() + "index.xml" }) {
            APIResponse get = page.request().get(url);
            assertHtml(get);
            assertEquals(url, get.url());
            APIResponse head = page.request().head(url, RequestOptions.create().setHeader("Accept-Encoding", "identity"));
            assertEquals(200, head.status());
            assertEquals(get.headers().get("content-type"), head.headers().get("content-type"));
            assertEquals("no-store", head.headers().get("cache-control"));
            assertEquals(0, head.body().length);
            assertEquals(get.body().length, Integer.parseInt(head.headers().get("content-length")));
        }
        String host = page.request().get(BASE_URL + "/legacy-serving.XML?evil=%3Cscript%3E").text();
        assertEquals(host, page.request().get(nodeUrl() + "index.xml?xml=elsewhere.xml").text());
    }

    @Test void rawBytesConditionalAndRangeSemanticsRemainInherited() {
        for (String url : new String[] { BASE_URL + "/legacy-serving.XML", nodeUrl() + "index.xml" }) {
            APIResponse raw = page.request().get(url + "?_raw=1&keep=two");
            assertEquals(200, raw.status());
            assertArrayEquals(XML.getBytes(StandardCharsets.UTF_8), raw.body());
            assertFalse(raw.headers().get("content-type").startsWith("text/html"));
            String etag = raw.headers().get("etag");
            assertNotNull(etag);
            APIResponse conditional = page.request().get(url + "?_raw=1",
                    RequestOptions.create().setHeader("If-None-Match", etag));
            assertEquals(304, conditional.status());
            assertEquals(0, conditional.body().length);
            assertHtml(page.request().get(url, RequestOptions.create().setHeader("If-None-Match", etag)));
            assertHtml(page.request().get(url, RequestOptions.create().setHeader("Range", "bytes=0-10")));
            // Existing custom/node range errors fall through to the missing bundled file.
            APIResponse range = page.request().get(url + "?_raw=1", RequestOptions.create().setHeader("Range", "bytes=0-10"));
            assertEquals(404, range.status());
            // Raw HEAD retains framework behavior; close the connection so its legacy
            // fixed-length HEAD body cannot contaminate the next keepalive request.
            APIResponse head = page.request().head(url + "?_raw=1", RequestOptions.create().setHeader("Connection", "close"));
            assertEquals(200, head.status());
            assertEquals(Integer.toString(raw.body().length), head.headers().get("content-length"));
            assertEquals(0, head.body().length);
            assertHtml(page.request().get(url + "?_raw=0"));
        }
        APIResponse bundledRange = page.request().get(BASE_URL + "/locals.xml?_raw=1",
                RequestOptions.create().setHeader("Range", "bytes=0-10"));
        assertEquals(500, bundledRange.status());
        assertTrue(bundledRange.text().contains("'range' not supported"));
    }

    @Test void nonUiMissingAndUnsupportedRequestsStayOnExistingPaths() {
        APIResponse ordinary = page.request().get(BASE_URL + "/legacy-data.xml");
        assertEquals("<data>raw café</data>\r\n", ordinary.text());
        assertEquals("<!DOCTYPE pages>" + XML, page.request().get(BASE_URL + "/legacy-dtd.xml").text());
        APIResponse malformed = page.request().get(BASE_URL + "/legacy-broken-pi.xml");
        assertEquals(200, malformed.status());
        assertArrayEquals(BROKEN_PI_XML.getBytes(StandardCharsets.UTF_8), malformed.body());
        assertFalse(malformed.headers().get("content-type").startsWith("text/html"));
        APIResponse xsl = page.request().get(BASE_URL + "/v1/index.xsl");
        assertEquals(200, xsl.status());
        assertTrue(xsl.text().contains("xsl:stylesheet"));
        assertEquals(404, page.request().get(BASE_URL + "/legacy-missing.xml").status());
        APIResponse post = page.request().post(BASE_URL + "/legacy-serving.XML");
        assertEquals(XML, post.text(), "POST must not select the HTML representation");
        assertEquals(404, page.request().get(BASE_URL + "/REST/legacy-missing.xml").status());
    }

    @Test void unsafePathsDoNotResolveOrReturnLoader() throws Exception {
        // A raw socket preserves dot segments that browser/API URL implementations normalise away.
        for (String path : new String[] { "/../legacy-serving.XML", "/nodes/LegacyXMLServing/../index.xml", "/%2e%2e/legacy-serving.XML" }) {
            try (Socket socket = new Socket("127.0.0.1", 18085)) {
                socket.setSoTimeout(10000);
                socket.getOutputStream().write(("GET " + path + " HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n")
                        .getBytes(StandardCharsets.US_ASCII));
                String response = new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(response.startsWith("HTTP/1.1 403"), response);
                assertFalse(response.contains("NODEL_LEGACY_REVEAL_GATED"));
            }
        }
    }

    @Test void htmlHeadNeverWritesBodyEvenWithGzipAccepted() throws IOException {
        for (String encoding : new String[] { "identity", "gzip" }) {
            try (Socket socket = new Socket("127.0.0.1", 18085)) {
                socket.setSoTimeout(10000);
                socket.getOutputStream().write(("HEAD /legacy-serving.XML HTTP/1.1\r\nHost: localhost\r\n"
                        + "Accept-Encoding: " + encoding + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                String response = new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(response.startsWith("HTTP/1.1 200"), response);
                assertTrue(response.contains("Content-Type: text/html; charset=utf-8"), response);
                int end = response.indexOf("\r\n\r\n");
                assertTrue(end > 0, response);
                assertEquals("", response.substring(end + 4), "HEAD must not write body/trailer bytes");
                assertFalse(response.contains("Content-Encoding: gzip"));
            }
        }
    }

    @Test void eligibilityBeforeResolutionAndFileFailuresFallBack(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("fixture.xml"), XML);
        BoundaryHost host = new BoundaryHost(root.toFile());
        host.setFirstChoiceDir(root.toFile());
        try {
            Properties raw = new Properties();
            raw.setProperty("_raw", "1");
            try (org.nanohttpd.protocols.http.response.Response response = direct(host, root, "/fixture.xml", "GET", raw)) {
                assertEquals(XML, new String(response.getData().readAllBytes(), StandardCharsets.UTF_8));
            }
            try (org.nanohttpd.protocols.http.response.Response response = direct(host, root, "/fixture.xml", "POST", new Properties())) {
                assertEquals(XML, new String(response.getData().readAllBytes(), StandardCharsets.UTF_8));
            }
            try (org.nanohttpd.protocols.http.response.Response response = direct(host, root, "/../fixture.xml", "GET", new Properties())) {
                assertEquals(403, response.getStatus().getRequestStatus());
            }
            try (org.nanohttpd.protocols.http.response.Response response = direct(host, root, "/fixture.txt", "GET", new Properties())) {
                assertEquals(404, response.getStatus().getRequestStatus());
            }
            assertEquals(0, host.resolutions, "raw, method, extension and unsafe path checks precede resolveFile");
            for (File target : new File[] {
                    new File(root.toFile(), "unreadable") {
                        @Override public boolean isFile() { return true; }
                        @Override public boolean canRead() { return false; }
                    },
                    new File(root.toFile(), "vanished-after-resolution") {
                        @Override public boolean isFile() { return true; }
                        @Override public boolean canRead() { return true; }
                    }, root.toFile() }) {
                host.forced = target;
                try (org.nanohttpd.protocols.http.response.Response response = direct(host, root, "/fixture.xml", "GET", new Properties())) {
                    assertEquals(XML, new String(response.getData().readAllBytes(), StandardCharsets.UTF_8),
                            "unreadable, vanished or non-regular scan target must return to normal static serving");
                }
            }
        } finally { host.stop(); }
    }

    @Test void nullContentRootsNeverInspectOutsideFiles(@TempDir Path fixtures) throws IOException {
        Path bundled = Files.createDirectory(fixtures.resolve("bundled"));
        Path override = Files.createDirectory(fixtures.resolve("override"));
        Path outside = Files.createDirectory(fixtures.resolve("outside"));
        Path outsideXml = outside.resolve("ui.xml");
        Files.writeString(outsideXml, XML);
        Files.writeString(bundled.resolve("bundled.xml"), XML);
        Files.writeString(bundled.resolve("precedence.xml"), XML);
        Files.writeString(override.resolve("override.xml"), XML);
        Files.writeString(override.resolve("precedence.xml"), "<custom>no stylesheet</custom>");
        String outsideUri = outsideXml.toAbsolutePath().toString().replace(File.separatorChar, '/');
        BoundaryHost host = new BoundaryHost(bundled.toFile());
        try {
            Properties raw = new Properties();
            raw.setProperty("_raw", "1");
            for (File firstChoice : new File[] { override.toFile(), null }) {
                host.setFirstChoiceDir(firstChoice);
                for (Path requestRoot : new Path[] { null, bundled }) {
                    try (org.nanohttpd.protocols.http.response.Response response = direct(host, requestRoot,
                            outsideUri, "GET", new Properties())) {
                        assertEquals(404, response.getStatus().getRequestStatus(),
                                "a recognised XML outside every content root must not become a loader response");
                    }
                    try (org.nanohttpd.protocols.http.response.Response response = direct(host, requestRoot,
                            outsideUri, "GET", raw)) {
                        assertEquals(404, response.getStatus().getRequestStatus());
                    }
                }
                try (org.nanohttpd.protocols.http.response.Response response = direct(host, null,
                        "/bundled.xml", "GET", new Properties())) {
                    assertEquals(200, response.getStatus().getRequestStatus());
                    assertEquals("text/html; charset=utf-8", response.getMimeType(),
                            "bundled host XML must still work with either first-choice configuration");
                }
            }
            host.setFirstChoiceDir(override.toFile());
            try (org.nanohttpd.protocols.http.response.Response response = direct(host, null,
                    "/override.xml", "GET", new Properties())) {
                assertEquals(200, response.getStatus().getRequestStatus());
                assertEquals("text/html; charset=utf-8", response.getMimeType());
            }
            try (org.nanohttpd.protocols.http.response.Response response = direct(host, null,
                    "/precedence.xml", "GET", new Properties())) {
                assertEquals("<custom>no stylesheet</custom>",
                        new String(response.getData().readAllBytes(), StandardCharsets.UTF_8),
                        "a non-UI first-choice override must win over recognised bundled XML");
            }
        } finally { host.stop(); }
    }

    private static class BoundaryHost extends NodelHostHTTPD {
        int resolutions;
        File forced;
        BoundaryHost(File root) throws IOException { super(0, root); }
        @Override public File resolveFile(String uri, File root) {
            resolutions++;
            return forced == null ? super.resolveFile(uri, root) : forced;
        }
    }

    private static org.nanohttpd.protocols.http.response.Response direct(BoundaryHost host, Path root,
            String uri, String method, Properties params) {
        Socket peer = new Socket() {
            @Override public java.net.SocketAddress getRemoteSocketAddress() { return new InetSocketAddress("127.0.0.1", 1); }
            @Override public java.net.SocketAddress getLocalSocketAddress() { return new InetSocketAddress("127.0.0.1", 2); }
        };
        Request request = new Request(uri, method, params, new Properties(), new Properties(), null, peer);
        return host.serve(uri, root == null ? null : root.toFile(), method, params, request);
    }

    @Test void specialOperationsPythonAndOverridePrecedenceRemainUnchanged() throws IOException {
        APIResponse source = page.request().get(nodeUrl() + "index.xml?_source");
        assertEquals(XML, source.text());
        assertTrue(source.headers().get("content-type").startsWith("text/plain"));
        APIResponse edit = page.request().get(nodeUrl() + "index.xml?_edit");
        assertEquals(page.request().get(nodeUrl() + "editor.htm").status(), edit.status());
        assertFalse(edit.text().contains("NODEL_LEGACY_REVEAL_GATED"));
        Path writable = nodeContent.resolve("legacy-write.xml");
        Files.writeString(writable, XML);
        APIResponse write = page.request().post(nodeUrl() + "legacy-write.xml?_write",
                RequestOptions.create().setData("<written/>"));
        assertEquals(200, write.status());
        assertEquals("<written/>", Files.readString(writable));
        assertEquals(403, page.request().get(nodeUrl() + "index.xml?_write").status());
        assertEquals("python-page-wins", page.request().get(nodeUrl() + "legacy-python.xml").text());
        assertEquals("python-page-wins", page.request().get(nodeUrl() + "legacy-python.xml?_raw=1").text());
        assertEquals("<node>override without stylesheet</node>", page.request().get(nodeUrl() + "legacy-precedence.xml").text());
        assertHtml(page.request().get(BASE_URL + "/legacy-precedence.xml"));
        // First-choice overrides of bundled content must also control recognition.
        Path override = custom.resolve("toolkit.xml");
        assertFalse(Files.exists(override));
        try {
            Files.writeString(override, "<custom>no stylesheet</custom>");
            assertEquals("<custom>no stylesheet</custom>", page.request().get(BASE_URL + "/toolkit.xml").text());
            assertEquals("<custom>no stylesheet</custom>", page.request().get(nodeUrl() + "toolkit.xml").text());
            Files.writeString(override, XML);
            assertHtml(page.request().get(BASE_URL + "/toolkit.xml"));
            assertHtml(page.request().get(nodeUrl() + "toolkit.xml"));
        } finally {
            Files.deleteIfExists(override);
        }
    }

    @Test void browserNavigationRefreshHistoryAndLiveBindings() {
        String url = nodeUrl() + "index.xml?xml=wrong.xml&keep=yes#Main";
        List<String> requests = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        page.onRequest(request -> requests.add(request.url()));
        page.onPageError(errors::add);
        Response response = page.navigate(url);
        assertEquals(200, response.status());
        assertTrue(response.headers().get("content-type").startsWith("text/html"));
        page.waitForSelector("[data-action='testAction']");
        assertEquals(url, page.url());
        assertTrue(requests.stream().anyMatch(request -> request.contains("index.xml?xml=wrong.xml&keep=yes&_raw=1")));
        page.locator("[data-action='testAction']").click();
        assertTrue(waitForConsoleContains(NODE, "browser-live", 10000), "browser action must reach node runtime");
        page.waitForFunction("document.querySelector('[data-event=Status]').textContent.includes('Action executed: browser-live')");
        assertTrue(errors.isEmpty(), errors.toString());

        Response refreshed = page.reload();
        assertTrue(refreshed.headers().get("content-type").startsWith("text/html"));
        page.waitForSelector("[data-action='testAction']");
        assertEquals(url, page.url());
        page.evaluate("() => { const a = document.createElement('a'); a.id='legacy-normal'; a.href='/toolkit.xml?keep=yes#Toolkit'; a.textContent='Next'; document.body.append(a); }");
        page.locator("#legacy-normal").click();
        page.waitForSelector(".nodel-toolkit");
        assertEquals(BASE_URL + "/toolkit.xml?keep=yes#Toolkit", page.url());
        page.goBack();
        page.waitForSelector("[data-action='testAction']");
        assertEquals(url, page.url());
        page.goForward();
        page.waitForSelector(".nodel-toolkit");
        page.evaluate("() => { const a = document.createElement('a'); a.id='legacy-new-tab'; a.href='/diagnostics.xml?keep=yes#Diagnostics'; a.target='_blank'; a.textContent='New tab'; document.body.append(a); }");
        Page popup = page.waitForPopup(() -> page.locator("#legacy-new-tab").click());
        try {
            popup.waitForSelector(".nodel-diagnostics");
            assertEquals(BASE_URL + "/diagnostics.xml?keep=yes#Diagnostics", popup.url());
        } finally { popup.close(); }
        assertTrue(errors.isEmpty(), errors.toString());
    }

    @Test void rootNodeDefaultAndCustomIndexSelectionStillWork() {
        page.navigate(BASE_URL + "/");
        page.waitForSelector(".nodel-locals");
        assertEquals(BASE_URL + "/locals.xml#Locals", page.url(), "root bootstrap replaces its URL with the selected XML page");
        recreatePage();
        page.navigate(BASE_URL + "/nodes/" + getReducedName(DEFAULT_NODE) + "/");
        page.waitForSelector(".nodel-actsig");
        assertTrue(page.url().matches(".*/nodes/[^/]+/(index|nodel)\\.xml(?:#.*)?"),
                "node entry must end at the selected visible XML filename: " + page.url());
        recreatePage();
        List<String> requests = new ArrayList<>();
        page.onRequest(request -> requests.add(request.url()));
        page.navigate(nodeUrl() + "#Main");
        page.waitForSelector("[data-action='testAction']");
        assertTrue(page.url().endsWith("/index.xml#Main"));
        assertTrue(requests.stream().anyMatch(request -> request.equals(nodeUrl() + "index.xml?_raw=1")),
                "node index probe must request raw XML");
        recreatePage();
        page.navigate(nodeUrl() + "index.htm?keep=yes#Main");
        page.waitForSelector("[data-action='testAction']");
        assertEquals(nodeUrl() + "index.xml?keep=yes#Main", page.url(),
                "explicit node index.htm bootstrap must select node-local index.xml");
        recreatePage();
        page.navigate(BASE_URL + "/nodes/" + getReducedName(DEFAULT_NODE) + "/index.htm?keep=yes");
        page.waitForSelector(".nodel-actsig");
        assertTrue(page.url().matches(".*/nodes/[^/]+/nodel\\.xml\\?keep=yes(?:#.*)?"),
                "explicit node index.htm without index.xml must select nodel.xml: " + page.url());
        recreatePage();
        page.navigate(BASE_URL + "/legacy-serving.XML?keep=host#Main");
        page.waitForSelector(".page h4");
        assertEquals("Same URL café", page.textContent(".page h4"));
        assertEquals(BASE_URL + "/legacy-serving.XML?keep=host#Main", page.url());
        recreatePage();
        page.navigate(BASE_URL + "/index.htm?xml=legacy-serving.XML&keep=explicit#Main");
        page.waitForSelector(".page h4");
        assertEquals("Same URL café", page.textContent(".page h4"));
        assertEquals(BASE_URL + "/legacy-serving.XML?keep=explicit#Main", page.url(),
                "explicit bootstrap selection removes only xml and preserves query/fragment");
        recreatePage();
        List<String> scriptErrors = new ArrayList<>();
        page.onPageError(scriptErrors::add);
        page.navigate(BASE_URL + "/index.htm?xml=https%3A%2F%2Fevil.test%2Foutside.xml");
        page.waitForFunction("document.title === 'Legacy UI unavailable' && document.body.textContent.includes('Unable to load the requested XML page')");
        assertEquals(BASE_URL + "/index.htm?xml=https%3A%2F%2Fevil.test%2Foutside.xml", page.url(),
                "invalid explicit target must not navigate");
        assertEquals("Legacy UI unavailable", page.title());
        assertTrue(page.locator("body").textContent().contains("Unable to load the requested XML page"));
        assertTrue(scriptErrors.isEmpty(), scriptErrors.toString());
        recreatePage();
        page.navigate(BASE_URL + "/index.htm?xml=");
        page.waitForFunction("document.title === 'Legacy UI unavailable'");
        assertTrue(page.locator("body").textContent().contains("Unable to load the requested XML page"),
                "empty xml selector is invalid, not an absent selector");
    }
}
