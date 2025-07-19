package org.nodel.http.impl;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
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
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
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

    private static final X509TrustManager IGNORE_SSL_TRUSTMANAGER = new X509TrustManager() {
        public void checkClientTrusted(X509Certificate[] chain, String authType) {}
        public void checkServerTrusted(X509Certificate[] chain, String authType) {}
        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
    };

    private static final DefaultRedirectStrategy IGNORE_ALL_REDIRECTS = new DefaultRedirectStrategy() {
        @Override
        public boolean isRedirected(HttpRequest request, HttpResponse response, HttpContext context) {
            return false;
        }
    };

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
            throw new RuntimeException("Error initializing SSL context", e);
        }
    }

    private static String getLocalHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "localhost";
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

        CompletableFuture<HTTPSimpleResponse> future = new CompletableFuture<>();

        try {
            SimpleHttpRequest request = createRequest(method, fullURL, body, contentType);

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

            _httpAsyncClient.execute(request, new FutureCallback<SimpleHttpResponse>() {
                @Override
                public void completed(SimpleHttpResponse response) {
                    try {
                        if (!Strings.isEmpty(body)) {
                            s_sendRate.addAndGet(body.length());
                        }

                        byte[] contentBytes = response.getBodyBytes();
                        if (contentBytes != null) {
                            if (contentBytes.length > MAX_ALLOWED) {
                                throw new IOException("Too big - HTTP response over " + MAX_ALLOWED + " bytes is not allowed");
                            }
                            s_receiveRate.addAndGet(contentBytes.length);
                        }

                        String content = null;
                        if (contentBytes != null) {
                            String encoding = "ISO-8859-1";
                            ContentType contentType = response.getContentType();
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
                            content = new String(contentBytes, encoding);
                        }

                        HTTPSimpleResponse result = new HTTPSimpleResponse();
                        result.content = content;
                        result.statusCode = response.getCode();
                        result.reasonPhrase = response.getReasonPhrase();

                        for (Header header : response.getHeaders()) {
                            result.addHeader(header.getName(), header.getValue());
                        }

                        future.complete(result);
                    } catch (Exception e) {
                        failed(e);
                    } finally {
                        s_activeConnections.decrementAndGet();
                    }
                }

                @Override
                public void failed(Exception ex) {
                    try {
                        future.completeExceptionally(new IOException(ex));
                    } finally {
                        s_activeConnections.decrementAndGet();
                    }
                }

                @Override
                public void cancelled() {
                    try {
                        future.completeExceptionally(new RuntimeException("Request was cancelled"));
                    } finally {
                        s_activeConnections.decrementAndGet();
                    }
                }
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

    private void applySecurity(SimpleHttpRequest request, String username, String password) {
        request.setHeader("Authorization", "Basic " +
                java.util.Base64.getEncoder().encodeToString((username + ":" + password).getBytes()));

        try {
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
                Credentials creds = new UsernamePasswordCredentials(username, password.toCharArray());
                _credentialsProvider.setCredentials(
                        new AuthScope(request.getAuthority().getHostName(), request.getAuthority().getPort()),
                        creds);
            }
        } catch (Exception e) {
        }
    }

    /**
     * Creates a request using HttpClient 5's cleaner SimpleRequestBuilder,
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

        return builder.build();
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

    @Override
    public void close() throws IOException {
        synchronized (_lock) {
            if (_httpAsyncClient != null) {
                _httpAsyncClient.close(CloseMode.GRACEFUL);
                _httpAsyncClient = null;
            }

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
}