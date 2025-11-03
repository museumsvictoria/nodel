package org.nodel.http.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.channels.FileChannel;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.NTCredentials;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.DefaultRedirectStrategy;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.nio.AsyncResponseConsumer;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.client5.http.async.methods.SimpleRequestProducer;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.util.Timeout;
import org.nodel.Strings;
import org.nodel.Version;
import org.nodel.net.HTTPSimpleResponse;
import org.nodel.net.NodelHTTPClient;

public class ApacheNodelHttpClient extends NodelHTTPClient {

    private final Object _lock = new Object();

    /**
     * The Apache Http Client
     * (created lazily)
     */
    private CloseableHttpAsyncClient _httpAsyncClient;

    /**
     * Used for managing asynchronous tasks
     */
    private ExecutorService _executor;

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
     * Threshold for switching from memory to file-based streaming (10 MB)
     */
    private final static int STREAMING_THRESHOLD = 10 * 1024 * 1024;

    /**
     * Lazily initialises the async client in a thread-safe manner.
     * This ensures the client (and its proxy configuration) is only set up once.
     */
    private synchronized void lazyInit() {
        if (_httpAsyncClient != null) return;

        if (_credentialsProvider == null) {
            _credentialsProvider = new BasicCredentialsProvider();
            _requestConfig = RequestConfig.custom()
                    .setConnectTimeout(Timeout.ofMilliseconds(DEFAULT_CONNECTTIMEOUT))
                    .setResponseTimeout(Timeout.ofMilliseconds(DEFAULT_READTIMEOUT))
                    .build();
        }

        if (_executor == null) {
            _executor = Executors.newCachedThreadPool();
        }

        IOReactorConfig ioReactorConfig = IOReactorConfig.custom()
                .setSoTimeout(Timeout.ofMilliseconds(DEFAULT_READTIMEOUT))
                .build();

        org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder builder = HttpAsyncClients.custom()
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
    public HTTPSimpleResponse makeRequest(String urlStr, String method, Map<String, String> query,
                         String username, String password,
                         Map<String, String> headers, String contentType,
                         String body,
                         Integer connectTimeout, Integer readTimeout) {
        try {
            return makeRequestAsync(urlStr, method, query, username, password,
                    headers, contentType, body, connectTimeout, readTimeout).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Request was interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            // Propagate IOExceptions (like SSLHandshakeException) to be handled by the caller
            if (cause instanceof IOException) {
                throw new RuntimeException(cause);
            }
            throw new RuntimeException("Error executing request", cause);
        }
    }

    @Override
    public CompletableFuture<HTTPSimpleResponse> makeRequestAsync(String urlStr, String method, Map<String, String> query,
                         String username, String password,
                         Map<String, String> headers, String contentType,
                         String body,
                         Integer connectTimeout, Integer readTimeout) {

        synchronized (_lock) {
            lazyInit();
        }

        s_attemptRate.incrementAndGet();

        String fullURL = urlStr;
        String queryPart = urlEncodeQuery(query);
        if (!Strings.isEmpty(queryPart)) {
            fullURL = String.format("%s?%s", urlStr, queryPart);
        }

        final CompletableFuture<HTTPSimpleResponse> future = new CompletableFuture<>();

        try {
            final SimpleHttpRequest request = createRequest(method, fullURL, body, contentType);

            if (!Strings.isBlank(username)) {
                applySecurity(request, username, !Strings.isEmpty(password) ? password : "");
            }

            if (headers != null) {
                for (Entry<String, String> entry : headers.entrySet()) {
                    request.setHeader(entry.getKey(), entry.getValue());
                }
            }

            applyTimeouts(request, connectTimeout, readTimeout);

            s_activeConnections.incrementAndGet();

            _executor.submit(() -> {
                SmartResponseConsumer consumer = new SmartResponseConsumer();
                _httpAsyncClient.execute(SimpleRequestProducer.create(request), consumer, new FutureCallback<HTTPSimpleResponse>() {
                    @Override
                    public void completed(HTTPSimpleResponse result) {
                        if (!Strings.isEmpty(body)) {
                            s_sendRate.addAndGet(body.length());
                        }
                        s_receiveRate.addAndGet(result.getRawContent() != null ? result.getRawContent().length : 0);
                        future.complete(result);
                        s_activeConnections.decrementAndGet();
                    }

                    @Override
                    public void failed(Exception ex) {
                        future.completeExceptionally(ex);
                        s_activeConnections.decrementAndGet();
                    }

                    @Override
                    public void cancelled() {
                        future.cancel(true);
                        s_activeConnections.decrementAndGet();
                    }
                });
            });
        } catch (Exception e) {
            future.completeExceptionally(new IOException(e));
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
            ContentType cType = ContentType.create(
                    contentType != null ? contentType : "text/plain", "utf-8");
            builder.setBody(body, cType);
        }

        SimpleHttpRequest request = builder.build();

        if (!Strings.isBlank(contentType)) {
            request.setHeader("Content-Type", contentType);
        }

        return request;
    }

    @Override
    public CompletableFuture<String> makeSimpleRequestAsync(String urlStr, String method, Map<String, String> query,
                              String username, String password,
                              Map<String, String> headers, String contentType, String post,
                              Integer connectTimeout, Integer readTimeout) {

        return makeRequestAsync(urlStr, method, query, username, password, headers, contentType, post, connectTimeout, readTimeout)
                .thenApply(response -> {
                    if (response.statusCode >= 200 && response.statusCode < 300) {
                        return response.content;
                    } else {
                        throw new RuntimeException(String.format("Server returned '%s' with content %s",
                                response.statusCode + " " + response.reasonPhrase,
                                Strings.isEmpty(response.content) ? "<empty>" : org.nodel.json.JSONObject.quote(response.content)));
                    }
                });
    }

    /**
     * Applies security for a given HTTP request.
     */
    private void applySecurity(SimpleHttpRequest request, String username, String password) {
        request.setHeader("Authorization", "Basic " +
                java.util.Base64.getEncoder().encodeToString((username + ":" + password).getBytes()));

        try {
            // in case of NTLM, check for '\' in username
            String userPart = username;
            String domainPart = null;
            int indexOfBackSlash = username.indexOf('\\');

            if (indexOfBackSlash > 0) {
                domainPart = username.substring(0, indexOfBackSlash);
                userPart = username.substring(Math.min(username.length() - 1, indexOfBackSlash + 1));

                Credentials creds = new NTCredentials(userPart, password.toCharArray(), getLocalHostName(), domainPart);
                _credentialsProvider.setCredentials(
                        new AuthScope(request.getAuthority().getHostName(), request.getAuthority().getPort()),
                        creds);
            } else {
                // normally used with NTLM
                Credentials creds = new UsernamePasswordCredentials(username, password.toCharArray());
                _credentialsProvider.setCredentials(
                        new AuthScope(request.getAuthority().getHostName(), request.getAuthority().getPort()),
                        creds);
            }
        } catch (Exception e) {
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
            closeClient();

            if (_executor != null) {
                _executor.shutdown();
                try {
                    if (!_executor.awaitTermination(5, TimeUnit.SECONDS)) {
                        _executor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    _executor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
                _executor = null;
            }

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
     * Smart response consumer that automatically chooses between memory and file streaming
     * based on response size.
     */
    private static class SmartResponseConsumer implements AsyncResponseConsumer<HTTPSimpleResponse> {

        private HTTPSimpleResponse result;
        private ByteArrayOutputStream memoryBuffer;
        private FileChannel fileChannel;
        private Path tempFile;
        private boolean useFileStreaming;
        private long totalBytesReceived;
        private FutureCallback<HTTPSimpleResponse> callback;

        @Override
        public void consumeResponse(HttpResponse response, EntityDetails entityDetails, HttpContext context,
                                    FutureCallback<HTTPSimpleResponse> callback) throws HttpException, IOException {
            this.callback = callback;
            this.result = new HTTPSimpleResponse();
            this.result.statusCode = response.getCode();
            this.result.reasonPhrase = response.getReasonPhrase();
            for (Header header : response.getHeaders()) {
                this.result.addHeader(header.getName(), header.getValue());
            }

            // Determine streaming strategy based on Content-Length
            long contentLength = entityDetails != null ? entityDetails.getContentLength() : -1;

            if (contentLength > MAX_ALLOWED) {
                callback.failed(new IOException("Too big - HTTP response over " + MAX_ALLOWED + " bytes is not allowed"));
                return;
            }

            // Use file streaming for large or unknown-size responses
            this.useFileStreaming = (contentLength > STREAMING_THRESHOLD) || (contentLength < 0 && STREAMING_THRESHOLD > 0);

            if (this.useFileStreaming) {
                this.tempFile = Files.createTempFile("nodel-response-", ".tmp");
                this.fileChannel = FileChannel.open(tempFile, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            } else {
                int initialCapacity = contentLength > 0 ? (int) contentLength : 4096;
                this.memoryBuffer = new ByteArrayOutputStream(initialCapacity);
            }
        }

        @Override
        public void informationResponse(HttpResponse response, HttpContext context) throws HttpException, IOException {
            // Ignore informational responses so 1xx flows (e.g. 100-Continue) proceed normally.
        }

        @Override
        public void failed(Exception cause) {
            releaseResources();
        }

        @Override
        public void updateCapacity(CapacityChannel capacityChannel) throws IOException {
            // Required by interface but we use HttpClient's automatic flow control
            capacityChannel.update(Integer.MAX_VALUE);
        }

        @Override
        public void consume(ByteBuffer src) throws IOException {
            int bytesToRead = src.remaining();
            this.totalBytesReceived += bytesToRead;

            if (this.totalBytesReceived > MAX_ALLOWED) {
                throw new IOException("Too big - HTTP response over " + MAX_ALLOWED + " bytes is not allowed");
            }

            if (this.useFileStreaming) {
                while (src.hasRemaining()) {
                    this.fileChannel.write(src);
                }
            } else {
                // Check if we should switch to file streaming mid-response
                if (this.memoryBuffer.size() + bytesToRead > STREAMING_THRESHOLD) {
                    switchToFileStreaming(src);
                } else {
                    writeToMemoryBuffer(src);
                }
            }
        }

        private void writeToMemoryBuffer(ByteBuffer src) {
            if (src.hasArray()) {
                int pos = src.position();
                int len = src.remaining();
                this.memoryBuffer.write(src.array(), src.arrayOffset() + pos, len);
                src.position(pos + len);
            } else {
                while (src.hasRemaining()) {
                    this.memoryBuffer.write(src.get());
                }
            }
        }

        private void switchToFileStreaming(ByteBuffer src) throws IOException {
            this.tempFile = Files.createTempFile("nodel-response-", ".tmp");
            this.fileChannel = FileChannel.open(tempFile, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            
            // Write existing buffer to file
            if (this.memoryBuffer.size() > 0) {
                this.fileChannel.write(ByteBuffer.wrap(this.memoryBuffer.toByteArray()));
                this.memoryBuffer = null;
            }
            
            // Write current data to file
            while (src.hasRemaining()) {
                this.fileChannel.write(src);
            }
            
            this.useFileStreaming = true;
        }

        @Override
        public void streamEnd(List<? extends Header> trailers) throws HttpException, IOException {
            try {
                byte[] content;
                
                if (this.useFileStreaming) {
                    this.fileChannel.close();
                    content = Files.readAllBytes(this.tempFile);
                    Files.deleteIfExists(this.tempFile);
                } else {
                    content = this.memoryBuffer != null ? this.memoryBuffer.toByteArray() : new byte[0];
                }
                
                this.result.setRawContent(content);
                
                // Set content string with encoding detection
                try {
                    String encoding = "ISO-8859-1"; // default
                    String contentTypeHeader = this.result.getFirstHeader("Content-Type");
                    if (contentTypeHeader != null) {
                        ContentType contentType = ContentType.parse(contentTypeHeader);
                        if (contentType != null) {
                            if (contentType.getCharset() != null) {
                                encoding = contentType.getCharset().name();
                            } else {
                                String mimeType = contentType.getMimeType().toLowerCase();
                                if (mimeType.contains("json") || mimeType.contains("xml")) {
                                    encoding = "utf-8";
                                }
                            }
                        }
                    }
                    this.result.content = new String(content, encoding);
                } catch (UnsupportedEncodingException e) {
                    this.result.content = new String(content);
                }
                
                // Now complete the callback with the result
                this.callback.completed(this.result);
            } catch (Exception e) {
                this.callback.failed(e);
            }
        }


        @Override
        public void releaseResources() {
            try {
                if (this.fileChannel != null) {
                    this.fileChannel.close();
                }
                if (this.tempFile != null) {
                    Files.deleteIfExists(this.tempFile);
                }
            } catch (IOException ignored) {}
            this.memoryBuffer = null;
        }
    }
}
