package org.nodel.http.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import javax.net.ssl.SSLContext;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.async.methods.SimpleRequestProducer;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.NTCredentials;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.DefaultRedirectStrategy;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.nio.entity.AbstractBinAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.support.AbstractAsyncResponseConsumer;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.util.ByteArrayBuffer;
import org.apache.hc.core5.util.Timeout;
import org.nodel.Strings;
import org.nodel.Version;
import org.nodel.net.HTTPSimpleResponse;
import org.nodel.net.NodelHTTPClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ApacheNodelHttpClient extends NodelHTTPClient {

    private static Logger s_logger = LoggerFactory.getLogger(ApacheNodelHttpClient.class);

    private final Object _lock = new Object();

    /**
     * The Apache Http Client
     * (created lazily)
     */
    private CloseableHttpAsyncClient _httpAsyncClient;

    /**
     * Required with 'applySecurity'
     */
    private BasicCredentialsProvider _credentialsProvider;

    /**
     * Mainly used for adjusting timeouts
     */
    private RequestConfig _requestConfig;

    /**
     * Maximum content allowed to avoid uncontrolled memory allocation
     * (default 150 MB, anything more should be using a streaming technique instead)
     */
    private final static int MAX_ALLOWED = 150 * 1024 * 1024;

    /**
     * Limits reactor threads so async usage stays bounded.
     */
    private static final int IO_THREAD_COUNT = 2;

    /**
     * Tracks whether this client has been closed.
     */
    private volatile boolean _closed;

    /**
     * Lazily initialises the async client (callers must hold '_lock').
     * This ensures the client (and its proxy configuration) is only set up once.
     */
    private void lazyInit() {
        if (_httpAsyncClient != null) return;

        if (_credentialsProvider == null) {
            _credentialsProvider = new BasicCredentialsProvider();
            _requestConfig = RequestConfig.custom()
                    .setConnectTimeout(Timeout.ofMilliseconds(DEFAULT_CONNECTTIMEOUT))
                    .setResponseTimeout(Timeout.ofMilliseconds(DEFAULT_READTIMEOUT))
                    .build();
        }

        IOReactorConfig ioReactorConfig = IOReactorConfig.custom()
                .setSoTimeout(Timeout.ofMilliseconds(DEFAULT_READTIMEOUT))
                .setIoThreadCount(IO_THREAD_COUNT)
                .build();

        HttpAsyncClientBuilder builder = HttpAsyncClients.custom()
                .setUserAgent("Nodel/" + Version.shared().version)
                .setDefaultCredentialsProvider(_credentialsProvider) // need to reference this later
                .setDefaultRequestConfig(_requestConfig) // default timeouts
                .setIOReactorConfig(ioReactorConfig);

        PoolingAsyncClientConnectionManagerBuilder poolBuilder = PoolingAsyncClientConnectionManagerBuilder.create()
                .setMaxConnTotal(1000)
                .setMaxConnPerRoute(1000);

        // ignore all SSL verifications errors?
        if (_ignoreSSL) {
            prepareForNoSSL(poolBuilder);
        }

        builder.setConnectionManager(poolBuilder.build());

        // using a proxy?
        if (!Strings.isBlank(_proxyAddress)) {
            builder.setProxy(prepareForProxyUse(_proxyAddress, _proxyUsername, _proxyPassword));
        }

        if (_ignoreRedirects) {
            builder.setRedirectStrategy(IGNORE_ALL_REDIRECTS);
        }

        // build the client
        _httpAsyncClient = builder.build();
        _httpAsyncClient.start();
    }

    /**
     * (convenience method)
     */
    private HttpHost prepareForProxyUse(String proxyAddress, String proxyUsername, String proxyPassword) {
        int lastIndexOfColon = proxyAddress.lastIndexOf(':');
        if (lastIndexOfColon <= 0) {
            throw new IllegalArgumentException("Proxy address must be in the form host:port");
        }

        String proxyHost = proxyAddress.substring(0, lastIndexOfColon);
        int proxyPort;

        try {
            proxyPort = Integer.parseInt(proxyAddress.substring(lastIndexOfColon + 1));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid proxy port");
        }

        if (Strings.isBlank(proxyHost) || proxyPort <= 0) {
            throw new IllegalArgumentException("Proxy address must be in the form host:port");
        }

        HttpHost proxy = new HttpHost(proxyHost, proxyPort);

        if (!Strings.isBlank(proxyUsername) && proxyPassword != null) {
            String userPart = proxyUsername;
            String domainPart = null;
            int indexOfBackSlash = proxyUsername.indexOf('\\');

            if (indexOfBackSlash > 0) {
                domainPart = proxyUsername.substring(0, indexOfBackSlash);
                userPart = proxyUsername.substring(Math.min(proxyUsername.length() - 1, indexOfBackSlash + 1));
            }

            AuthScope authScope = new AuthScope(proxyHost, proxyPort);

            if (domainPart == null) {
                _credentialsProvider.setCredentials(authScope,
                        new UsernamePasswordCredentials(proxyUsername, proxyPassword.toCharArray()));
            } else {
                // normally used with NTLM
                _credentialsProvider.setCredentials(authScope,
                        new NTCredentials(userPart, proxyPassword.toCharArray(), getLocalHostName(), domainPart));
            }
        }

        return proxy;
    }

    /**
     * (convenience method)
     */
    private void prepareForNoSSL(PoolingAsyncClientConnectionManagerBuilder poolBuilder) {
        try {
            SSLContext sslContext = SSLContexts.custom().loadTrustMaterial(null, (chain, authType) -> true).build();
            poolBuilder.setTlsStrategy(ClientTlsStrategyBuilder.create()
                    .setSslContext(sslContext)
                    .setHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("Error initialising SSL context", e);
        }
    }

    @Override
    public CompletableFuture<HTTPSimpleResponse> makeRequestAsync(String urlStr, String method, Map<String, String> query,
                         String username, String password,
                         Map<String, String> headers, String contentType,
                         String body,
                         Integer connectTimeout, Integer readTimeout) {

        final CompletableFuture<HTTPSimpleResponse> future = new CompletableFuture<>();

        final CloseableHttpAsyncClient client;
        synchronized (_lock) {
            if (_closed) {
                future.completeExceptionally(new IllegalStateException("HTTP client is closed"));
                return future;
            }
            lazyInit();
            client = _httpAsyncClient;
        }

        // record rate of new connections
        s_attemptRate.incrementAndGet();

        // construct the full URL (includes query string)
        String fullURL = urlStr;
        String queryPart = urlEncodeQuery(query);
        if (!Strings.isEmpty(queryPart)) {
            fullURL = String.format("%s?%s", urlStr, queryPart);
        }

        try {
            final SimpleHttpRequest request = createRequest(method, fullURL, body, contentType);

            // if username is supplied, apply security
            if (!Strings.isBlank(username)) {
                applySecurity(request, username, !Strings.isEmpty(password) ? password : "");
            }

            // add (or override) any request headers
            if (headers != null) {
                for (Entry<String, String> entry : headers.entrySet()) {
                    request.setHeader(entry.getKey(), entry.getValue());
                }
            }

            // advertise compression support unless the caller manages it themselves
            // (the response consumer transparently decompresses, matching the previous HttpClient 4 behaviour)
            if (!request.containsHeader("Accept-Encoding")) {
                request.setHeader("Accept-Encoding", "gzip, deflate");
            }

            applyTimeouts(request, connectTimeout, readTimeout);

            // execute() is non-blocking — it hands the exchange straight to the client's I/O reactor
            s_activeConnections.incrementAndGet();
            boolean submitted = false;
            try {
                client.execute(SimpleRequestProducer.create(request), new BoundedResponseConsumer(), new FutureCallback<HTTPSimpleResponse>() {
                    // (counters are adjusted *before* completing the future because dependents
                    //  attached to it run synchronously on this thread)

                    @Override
                    public void completed(HTTPSimpleResponse result) {
                        s_activeConnections.decrementAndGet();
                        if (!Strings.isEmpty(body)) {
                            s_sendRate.addAndGet(body.length());
                        }
                        future.complete(result);
                    }

                    @Override
                    public void failed(Exception ex) {
                        s_activeConnections.decrementAndGet();
                        future.completeExceptionally(ex);
                    }

                    @Override
                    public void cancelled() {
                        s_activeConnections.decrementAndGet();
                        future.cancel(true);
                    }
                });
                submitted = true;
            } finally {
                if (!submitted) {
                    s_activeConnections.decrementAndGet();
                }
            }
        } catch (Exception e) {
            future.completeExceptionally(e);
        }

        return future;
    }

    private void applyTimeouts(SimpleHttpRequest request, Integer connectTimeout, Integer readTimeout) {
        int actualConnTimeout = connectTimeout != null ? connectTimeout : DEFAULT_CONNECTTIMEOUT;
        int actualReadTimeout = readTimeout != null ? readTimeout : DEFAULT_READTIMEOUT;

        RequestConfig customConfig = RequestConfig.copy(_requestConfig)
                .setConnectTimeout(Timeout.ofMilliseconds(actualConnTimeout))
                .setResponseTimeout(Timeout.ofMilliseconds(actualReadTimeout))
                .build();

        request.setConfig(customConfig);
    }

    /**
     * Creates a request using HttpClient 5's SimpleRequestBuilder,
     * replacing the old selectHTTPbyMethod() and nonstandardHTTPMethod() helpers.
     */
    private SimpleHttpRequest createRequest(String method, String url, String body, String contentType) {
        String effectiveMethod;
        if (Strings.isBlank(method)) {
            effectiveMethod = Strings.isEmpty(body) ? "GET" : "POST";
        } else {
            effectiveMethod = method;
        }

        SimpleRequestBuilder builder = SimpleRequestBuilder.create(effectiveMethod).setUri(url);

        if (!Strings.isEmpty(body)) {
            String effectiveContentType = contentType != null ? contentType : "text/plain";

            ContentType cType = null;
            // Accept callers passing full header values with parameters (charset, boundary, etc.).
            // Prefer parse to keep parameters, and fall back to stripping parameters if needed.
            try {
                cType = ContentType.parse(effectiveContentType);
                if (cType.getCharset() == null) {
                    cType = cType.withCharset("utf-8");
                }
            } catch (Exception parseExc) {
                try {
                    String mimeOnly = effectiveContentType.split(";", 2)[0].trim();
                    cType = ContentType.create(mimeOnly, "utf-8");
                } catch (Exception createExc) {
                    cType = ContentType.TEXT_PLAIN.withCharset("utf-8");
                }
            }

            builder.setBody(body, cType);
        }

        SimpleHttpRequest request = builder.build();

        if (!Strings.isBlank(contentType)) {
            request.setHeader("Content-Type", contentType);
        }

        return request;
    }

    /**
     * Applies security for a given HTTP request.
     */
    private void applySecurity(SimpleHttpRequest request, String username, String password) {
        // in case of NTLM, check for '\' in username
        String userPart = username;
        String domainPart = null;
        int indexOfBackSlash = username.indexOf('\\');

        if (indexOfBackSlash > 0) {
            domainPart = username.substring(0, indexOfBackSlash);
            userPart = username.substring(Math.min(username.length() - 1, indexOfBackSlash + 1));
        }

        Credentials creds;
        if (domainPart == null) {
            // BasicAuth: pre-emptive to save the challenge round-trip
            // (never for NTLM credentials — those must not leak in Basic-encoded form)
            request.setHeader("Authorization", "Basic " + Base64.getEncoder()
                    .encodeToString((username + ":" + password).getBytes(StandardCharsets.ISO_8859_1)));

            creds = new UsernamePasswordCredentials(username, password.toCharArray());
        } else {
            // NTLM
            creds = new NTCredentials(userPart, password.toCharArray(), getLocalHostName(), domainPart);
        }

        try {
            _credentialsProvider.setCredentials(
                    new AuthScope(request.getAuthority().getHostName(), request.getAuthority().getPort()),
                    creds);
        } catch (Exception e) {
            s_logger.warn("Could not register credentials for challenge-response authentication; the request will proceed without them.", e);
        }
    }

    @Override
    public void setProxy(String address, String username, String password) {
        synchronized (_lock) {
            super.setProxy(address, username, password);
            closeClient(); // Force re-initialisation on the next request
        }
    }

    @Override
    public void setIgnoreSSL(boolean value) {
        synchronized (_lock) {
            super.setIgnoreSSL(value);
            closeClient(); // Force re-initialisation on the next request
        }
    }

    @Override
    public void setIgnoreRedirects(boolean value) {
        synchronized (_lock) {
            super.setIgnoreRedirects(value);
            closeClient(); // Force re-initialisation on the next request
        }
    }

    private void closeClient() {
        if (_httpAsyncClient != null) {
            _httpAsyncClient.close(CloseMode.GRACEFUL);
            _httpAsyncClient = null;
        }
    }

    @Override
    public void close() {
        synchronized (_lock) {
            if (_closed) {
                return;
            }
            _closed = true;

            // GRACEFUL lets in-flight exchanges complete while the I/O threads wind down;
            // deliberately no waiting here — node shutdown must never stall on network activity
            closeClient();

            _credentialsProvider = null;
            _requestConfig = null;
        }
    }

    // static convenience methods

    /**
     * Returns the local host name (does not throw exceptions).
     */
    private static String getLocalHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "localhost";
        }
    }

    // convenience instances

    /**
     *  A redirect strategy used to ignore all redirect directives
     */
    private static final DefaultRedirectStrategy IGNORE_ALL_REDIRECTS = new DefaultRedirectStrategy() {
        @Override
        public boolean isRedirected(HttpRequest request, HttpResponse response, HttpContext context) {
            return false;
        }
    };

    /**
     * Buffers the response in memory (bounded by MAX_ALLOWED), transparently decompressing
     * gzip/deflate bodies the way HttpClient 4 used to (HttpClient 5's async chain has no
     * built-in equivalent). Responses without an entity (e.g. HEAD, 204, 304) complete with
     * null content via the base class.
     */
    private static class BoundedResponseConsumer extends AbstractAsyncResponseConsumer<HTTPSimpleResponse, byte[]> {

        public BoundedResponseConsumer() {
            super(new BoundedEntityConsumer());
        }

        @Override
        public void informationResponse(HttpResponse response, HttpContext context) {
            // Ignore informational responses so 1xx flows (e.g. 100-Continue) proceed normally.
        }

        @Override
        protected HTTPSimpleResponse buildResult(HttpResponse response, byte[] entity, ContentType contentType) {
            HTTPSimpleResponse result = new HTTPSimpleResponse();
            result.statusCode = response.getCode();
            result.reasonPhrase = response.getReasonPhrase();

            byte[] content = entity;

            // transparently decompress (mirrors HttpClient 4's ResponseContentEncoding behaviour)
            boolean decompressed = false;
            Header contentEncodingHeader = response.getFirstHeader("Content-Encoding");
            if (content != null && content.length > 0 && contentEncodingHeader != null) {
                String contentEncoding = contentEncodingHeader.getValue().trim().toLowerCase();
                try {
                    if (contentEncoding.contains("gzip")) {
                        content = readFullyBounded(new GZIPInputStream(new ByteArrayInputStream(content)));
                        decompressed = true;
                    } else if (contentEncoding.contains("deflate")) {
                        content = readFullyBounded(new InflaterInputStream(new ByteArrayInputStream(content)));
                        decompressed = true;
                    }
                } catch (IOException e) {
                    throw new RuntimeException("Could not decompress '" + contentEncoding + "' response", e);
                }
            }

            for (Header header : response.getHeaders()) {
                // a decompressed body no longer matches these headers, so drop them (as HttpClient 4 did)
                if (decompressed && (header.getName().equalsIgnoreCase("Content-Encoding")
                        || header.getName().equalsIgnoreCase("Content-Length")
                        || header.getName().equalsIgnoreCase("Content-MD5"))) {
                    continue;
                }

                result.addHeader(header.getName(), header.getValue());
            }

            if (content != null) {
                result.content = new String(content, selectCharset(contentType));
            }

            return result;
        }

        /**
         * Uses the response charset when specified; if not, falls back to UTF-8 for json/xml or
         * a straight 8-bit widening otherwise (for convenience ISO-8859-1 does that trick)
         */
        private static Charset selectCharset(ContentType contentType) {
            if (contentType != null) {
                if (contentType.getCharset() != null) {
                    return contentType.getCharset();
                }

                String mimeType = contentType.getMimeType() != null ? contentType.getMimeType().toLowerCase() : "";
                if (mimeType.contains("json") || mimeType.contains("xml")) {
                    return StandardCharsets.UTF_8;
                }
            }
            return StandardCharsets.ISO_8859_1;
        }

        /**
         * (drains a decompression stream, still enforcing the MAX_ALLOWED cap)
         */
        private static byte[] readFullyBounded(InputStream is) throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream(8192);
            byte[] chunk = new byte[8192];
            for (int read; (read = is.read(chunk)) > 0;) {
                if (out.size() + read > MAX_ALLOWED) {
                    throw new IOException("Too big - HTTP response over " + MAX_ALLOWED + " bytes is not allowed");
                }
                out.write(chunk, 0, read);
            }
            return out.toByteArray();
        }
    }

    /**
     * In-memory entity consumer that rejects bodies larger than MAX_ALLOWED and feeds the
     * receive-rate counter as bytes arrive.
     */
    private static class BoundedEntityConsumer extends AbstractBinAsyncEntityConsumer<byte[]> {

        private final ByteArrayBuffer _buffer = new ByteArrayBuffer(8192);

        @Override
        protected void streamStart(ContentType contentType) {
        }

        @Override
        protected int capacityIncrement() {
            return 65536;
        }

        @Override
        protected void data(ByteBuffer src, boolean endOfStream) throws IOException {
            int length = src.remaining();

            if (_buffer.length() + length > MAX_ALLOWED) {
                throw new IOException("Too big - HTTP response over " + MAX_ALLOWED + " bytes is not allowed");
            }

            // count bytes (not characters) as they arrive, failure or not
            s_receiveRate.addAndGet(length);

            if (src.hasArray()) {
                _buffer.append(src.array(), src.arrayOffset() + src.position(), length);
                src.position(src.position() + length);
            } else {
                byte[] chunk = new byte[length];
                src.get(chunk);
                _buffer.append(chunk, 0, length);
            }
        }

        @Override
        protected byte[] generateContent() {
            return _buffer.toByteArray();
        }

        @Override
        public void releaseResources() {
            _buffer.clear();
        }
    }
}
