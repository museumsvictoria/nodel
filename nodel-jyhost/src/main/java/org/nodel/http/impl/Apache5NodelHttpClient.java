package org.nodel.http.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.lang.reflect.Method;

import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.NTCredentials;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpHead;
import org.apache.hc.client5.http.classic.methods.HttpOptions;
import org.apache.hc.client5.http.classic.methods.HttpPatch;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.classic.methods.HttpTrace;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.DefaultRedirectStrategy;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.auth.BasicAuthCache;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.auth.BasicScheme;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.nio.AsyncClientConnectionManager;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.Timeout;
import org.nodel.Strings;
import org.nodel.Version;
import org.nodel.diagnostics.CountableInputStream;
import org.nodel.diagnostics.SharableMeasurementProvider;
import org.nodel.io.Stream;
import org.nodel.io.UnexpectedIOException;
import org.nodel.net.HTTPSimpleResponse;
import org.nodel.net.NodelHTTPClient;

/**
 * Apache HTTP Client 5 implementation of NodelHTTPClient that supports asynchronous HTTP requests.
 */
public class Apache5NodelHttpClient extends NodelHTTPClient {
    
    private Object _lock = new Object();
    
    /**
     * The Apache Http Client for synchronous operations
     * (created lazily)
     */
    private CloseableHttpClient _httpClient;
    
    /**
     * The Apache Http Async Client for asynchronous operations
     * (created lazily)
     */
    private CloseableHttpAsyncClient _httpAsyncClient;
    
    /**
     * Executor service for handling callbacks
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
     * This needs to be done lazily because proxy can only be set up once
     * 
     * (uses double-check singleton)
     */
    private void lazyInit() {
        if (_httpClient == null || _httpAsyncClient == null) {
            synchronized (_lock) {
                if (_httpClient != null && _httpAsyncClient != null)
                    return;
                
                _credentialsProvider = new BasicCredentialsProvider();
                
                // Create request config with default timeouts
                _requestConfig = RequestConfig.custom()
                        .setConnectTimeout(Timeout.ofMilliseconds(DEFAULT_CONNECTTIMEOUT))
                        .setResponseTimeout(Timeout.ofMilliseconds(DEFAULT_READTIMEOUT))
                        .build();
                
                // Initialize synchronous client
                initSyncClient();
                
                // Initialize asynchronous client
                initAsyncClient();
                
                // Create executor for handling async callbacks
                _executor = Executors.newCachedThreadPool();
            }
        }
    }
    
    /**
     * Initialize the synchronous HTTP client
     */
    private void initSyncClient() {
        HttpClientBuilder builder = HttpClients.custom()
                .setUserAgent("Nodel/" + Version.shared().version)
                
                // need to reference this later
                .setDefaultCredentialsProvider(_credentialsProvider)
                
                // default timeouts
                .setDefaultRequestConfig(_requestConfig);
                
                // Set connection limits using reflection to handle API differences
                try {
                    HttpClientBuilder.class.getMethod("setMaxConnTotal", int.class).invoke(builder, 1000);
                    HttpClientBuilder.class.getMethod("setMaxConnPerRoute", int.class).invoke(builder, 1000);
                } catch (Exception e) {
                    // Fallback to alternative method names if available
                    try {
                        HttpClientBuilder.class.getMethod("setConnectionsTotal", int.class).invoke(builder, 1000);
                        HttpClientBuilder.class.getMethod("setConnectionsPerRoute", int.class).invoke(builder, 1000);
                    } catch (Exception ignored) {
                        // If neither method exists, just continue without setting these values
                    }
                }
        
        // using a proxy?
        if (!Strings.isBlank(_proxyAddress))
            builder.setProxy(prepareForProxyUse(_proxyAddress, _proxyUsername, _proxyPassword));
        
        // ignore all SSL verifications errors?
        if (_ignoreSSL)
            prepareForNoSSL(builder);
        
        // ignore all redirect codes
        if (_ignoreRedirects)
            builder.setRedirectStrategy(IGNORE_ALL_REDIRECTS);
        
        // build the client
        _httpClient = builder.build();
    }
    
    /**
     * Initialize the asynchronous HTTP client
     */
    private void initAsyncClient() {
        // Configure IO reactor
        IOReactorConfig ioReactorConfig = IOReactorConfig.custom()
                .setSoTimeout(Timeout.ofMilliseconds(DEFAULT_READTIMEOUT))
                .build();
        
        org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder builder = HttpAsyncClients.custom()
                .setUserAgent("Nodel/" + Version.shared().version)
                .setDefaultCredentialsProvider(_credentialsProvider)
                .setDefaultRequestConfig(_requestConfig)
                .setIOReactorConfig(ioReactorConfig);
        
        // using a proxy?
        if (!Strings.isBlank(_proxyAddress)) {
            HttpHost proxy = prepareForProxyUse(_proxyAddress, _proxyUsername, _proxyPassword);
            builder.setProxy(proxy);
        }
        
        // ignore all SSL verifications errors?
        if (_ignoreSSL) {
            try {
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, new X509TrustManager[] { IGNORE_SSL_TRUSTMANAGER }, new SecureRandom());
                // Using a helper method to handle the differences between API versions
                setSSLContextForBuilder(builder, sslContext);
                
                AsyncClientConnectionManager connectionManager = PoolingAsyncClientConnectionManagerBuilder.create()
                        .build();
                
                builder.setConnectionManager(connectionManager);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        
        // Build and start the client
        _httpAsyncClient = builder.build();
        _httpAsyncClient.start();
    }

    /**
     * (convenience method) 
     */
    private HttpHost prepareForProxyUse(String proxyAddress, String proxyUsername, String proxyPassword) {
        HttpHost proxy;
        
        String proxyHost = null;
        int proxyPort = -1;
        
        try {
            int lastIndexOfColon = proxyAddress.lastIndexOf(':');
            proxyHost = proxyAddress.substring(0, lastIndexOfColon);
            proxyPort = Integer.parseInt(proxyAddress.substring(lastIndexOfColon + 1));
        } catch (Exception ignore) {
        }
        
        if (Strings.isBlank(proxyHost) || proxyPort <= 0)
            throw new IllegalArgumentException("Proxy address is not in form host:port");
        
        proxy = new HttpHost(proxyHost, proxyPort);
        
        // using proxy credentials?
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
                _credentialsProvider.setCredentials(authScope, new UsernamePasswordCredentials(proxyUsername, proxyPassword.toCharArray()));
            } else {
                // normally used with NTLM
                _credentialsProvider.setCredentials(authScope, new NTCredentials(userPart, proxyPassword.toCharArray(), getLocalHostName(), domainPart));
            }
        }
        
        return proxy;
    }
    
    /**
     * (convenience method)
     */
    private void prepareForNoSSL(HttpClientBuilder builder) {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new X509TrustManager[] { IGNORE_SSL_TRUSTMANAGER }, new SecureRandom());
            
            // Create a custom SSL connection socket factory with the no-op hostname verifier
            SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(
                    sslContext, 
                    new org.apache.hc.client5.http.ssl.NoopHostnameVerifier());
            
            // Build a connection manager with our custom SSL factory
            HttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                    .setSSLSocketFactory(sslSocketFactory)
                    .build();
            
            // Set the connection manager on the builder
            builder.setConnectionManager(connectionManager);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }    
    
    @Override
    public HTTPSimpleResponse makeRequest(String urlStr, String method, Map<String, String> query, 
                         String username, String password, 
                         Map<String, String> headers, String contentType, 
                         String body, 
                         Integer connectTimeout, Integer readTimeout) {
        try {
            // Using the async API but blocking on it to maintain the same behavior
            return makeRequestAsync(urlStr, method, query, username, password, headers, contentType, body, connectTimeout, readTimeout).get();
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
    
    /**
     * Asynchronous version of makeRequest
     */
    @Override
    public CompletableFuture<HTTPSimpleResponse> makeRequestAsync(String urlStr, String method, Map<String, String> query, 
                         String username, String password, 
                         Map<String, String> headers, String contentType, 
                         String body, 
                         Integer connectTimeout, Integer readTimeout) {
        
        lazyInit();
        
        // record rate of new connections
        s_attemptRate.incrementAndGet();
        
        // construct the full URL (includes query string)
        String fullURL;
        
        String queryPart = urlEncodeQuery(query);
        if (!Strings.isEmpty(queryPart))
            fullURL = String.format("%s?%s", urlStr, queryPart);
        else
            fullURL = urlStr;

        CompletableFuture<HTTPSimpleResponse> future = new CompletableFuture<>();
        
        try {
            // Build the request using SimpleHttpRequest for async client
            SimpleHttpRequest request = buildAsyncRequest(method, fullURL, body, contentType);
            
            // if username is supplied, apply security
            if (!Strings.isBlank(username)) {
                // Add Basic Auth header for async request
                request.setHeader("Authorization", "Basic " + 
                      java.util.Base64.getEncoder().encodeToString((username + ":" + 
                      (!Strings.isEmpty(password) ? password : "")).getBytes()));
                
                // Also prepare auth for any redirects
                prepareAuthForAsync(request, username, !Strings.isEmpty(password) ? password : "");
            }
            
            // set 'Content-Type' header if not already set
            if (!Strings.isBlank(contentType) && request.getHeader("Content-Type") == null) {
                request.setHeader("Content-Type", contentType);
            }

            // add (or override) any request headers
            if (headers != null) {
                for (Entry<String, String> entry : headers.entrySet())
                    request.setHeader(entry.getKey(), entry.getValue());
            }
            
            // set any timeouts that apply
            if (connectTimeout != null || readTimeout != null) {
                int actualConnTimeout = connectTimeout != null ? connectTimeout : DEFAULT_CONNECTTIMEOUT;
                int actualReadTimeout = readTimeout != null ? readTimeout : DEFAULT_READTIMEOUT;

                RequestConfig customConfig = RequestConfig.copy(_requestConfig)
                        .setConnectTimeout(Timeout.ofMilliseconds(actualConnTimeout))
                        .setResponseTimeout(Timeout.ofMilliseconds(actualReadTimeout))
                        .build();
                
                request.setConfig(customConfig);
            }
            
            // perform the request
            s_activeConnections.incrementAndGet();

            _httpAsyncClient.execute(request, new FutureCallback<SimpleHttpResponse>() {
                
                @Override
                public void completed(SimpleHttpResponse httpResponse) {
                    try {
                        // count the post now
                        if (!Strings.isEmpty(body))
                            s_sendRate.addAndGet(body.length());
                        
                        // Process the response
                        String content = httpResponse.getBodyText();
                        if (content != null) {
                            s_receiveRate.addAndGet(content.length());
                        }
                        
                        HTTPSimpleResponse result = new HTTPSimpleResponse();
                        result.content = content;
                        result.statusCode = httpResponse.getCode();
                        result.reasonPhrase = httpResponse.getReasonPhrase();
                        
                        for (Header header : httpResponse.getHeaders())
                            result.addHeader(header.getName(), header.getValue());
                        
                        future.complete(result);
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

    /**
     * Creates an async request with appropriate method and body
     */
    private SimpleHttpRequest buildAsyncRequest(String method, String url, String body, String contentType) {
        SimpleRequestBuilder builder = SimpleRequestBuilder.create(
                Strings.isBlank(method) ? (Strings.isEmpty(body) ? "GET" : "POST") : method);
        
        builder.setUri(url);
        
        // Set body if provided
        if (!Strings.isEmpty(body)) {
            builder.setBody(body, ContentType.create(contentType != null ? contentType : "text/plain", "utf-8"));
        }
        
        return builder.build();
    }
    
    /**
     * Prepare authentication for async client
     */
    private void prepareAuthForAsync(SimpleHttpRequest request, String username, String password) {
        // in case of NTLM, check for '\' in username
        String userPart = username;
        String domainPart = null;
        int indexOfBackSlash = username.indexOf('\\');
        if (indexOfBackSlash > 0) {
            // NTLM
            domainPart = username.substring(0, indexOfBackSlash);
            userPart = username.substring(Math.min(username.length() - 1, indexOfBackSlash + 1));
            
            Credentials creds = new NTCredentials(userPart, password.toCharArray(), getLocalHostName(), domainPart);
            _credentialsProvider.setCredentials(
                    new AuthScope(request.getAuthority().getHostName(), request.getAuthority().getPort()),
                    creds);
        } else {
            // BasicAuth already handled by setting the Authorization header
            Credentials creds = new UsernamePasswordCredentials(username, password.toCharArray());
            _credentialsProvider.setCredentials(
                    new AuthScope(request.getAuthority().getHostName(), request.getAuthority().getPort()),
                    creds);
        }
    }

    /**
     * Similar to makeRequestAsync but returns just the content on HTTP_OK or throws on non-2xx status
     */
    @Override
    public CompletableFuture<String> makeSimpleRequestAsync(String urlStr, String method, Map<String, String> query,
                              String username, String password,
                              Map<String, String> headers, String contentType, String post,
                              Integer connectTimeout, Integer readTimeout) {
        
        return makeRequestAsync(urlStr, method, query, username, password, headers, contentType, post, connectTimeout, readTimeout)
                .thenApply(response -> {
                    if (response.statusCode >= 200 && response.statusCode < 300) { // 200 is HTTP_OK
                        // any 'OK'-related response, just return content
                        return response.content;
                    } else {
                        // non-OK, so raise an exception including the content
                        throw new RuntimeException(String.format("Server returned '%s' with content %s", 
                                response.statusCode + " " + response.reasonPhrase, 
                                Strings.isEmpty(response.content) ? "<empty>" : org.nodel.json.JSONObject.quote(response.content)));
                    }
                });
    }
    
    /**
     * (convenience function)
     */
    private static HttpUriRequestBase selectHTTPbyMethod(String method, String body, String url) {
        // deal with in order of likelihood
        if (Strings.isBlank(method) && Strings.isEmpty(body) || "GET".equals(method))
            return new HttpGet(url);
        
        else if (Strings.isBlank(method) && !Strings.isEmpty(body) || "POST".equals(method))
            return new HttpPost(url);
        
        else if ("DELETE".equals(method))
            return new HttpDelete(url);
        
        else if ("HEAD".equals(method))
            return new HttpHead(url);
        
        else if ("PUT".equals(method))
            return new HttpPut(url);
        
        else if ("TRACE".equals(method))
            return new HttpTrace(url);
        
        else if ("OPTIONS".equals(method))
            return new HttpOptions(url);

        else if ("PATCH".equals(method))
            return new HttpPatch(url);

        else if (!Strings.isBlank(method))
            return nonstandardHTTPMethod(method, url);

        else
            throw new IllegalArgumentException("Unknown HTTP method - " + method);
    }

    /**
     * (convenience function for non-standard HTTP methods)
     */
    private static HttpUriRequestBase nonstandardHTTPMethod(String method, String url) {
        // nonstandard / uncommon method has been specified - related issue #344
        return new HttpUriRequestBase(method, URI.create(url)) {
            // Using the base implementation
        };
    }

    /**
     * Applies security for a given HTTP request.
     */
    private void applySecurity(HttpUriRequestBase httpRequest, String username, String password) {
        Credentials creds;

        // in case of NTLM, check for '\' in username
        String userPart = username;
        String domainPart = null;
        int indexOfBackSlash = username.indexOf('\\');
        if (indexOfBackSlash > 0) {
            // NTLM
            domainPart = username.substring(0, indexOfBackSlash);
            userPart = username.substring(Math.min(username.length() - 1, indexOfBackSlash + 1));

            creds = new NTCredentials(userPart, password.toCharArray(), getLocalHostName(), domainPart);

        } else {
            // BasicAuth
            creds = new UsernamePasswordCredentials(username, password.toCharArray());

            // pre-emptive
            try {
                BasicScheme basicScheme = new BasicScheme();
                BasicAuthCache authCache = new BasicAuthCache();
                HttpHost targetHost = new HttpHost(httpRequest.getUri().getHost(), httpRequest.getUri().getPort());
                authCache.put(targetHost, basicScheme);
                
                HttpClientContext context = HttpClientContext.create();
                context.setCredentialsProvider(_credentialsProvider);
                context.setAuthCache(authCache);
                
                httpRequest.setHeader(new BasicHeader("Authorization", "Basic " + 
                          java.util.Base64.getEncoder().encodeToString((username + ":" + password).getBytes())));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        try {
            _credentialsProvider.setCredentials(
                    new AuthScope(httpRequest.getUri().getHost(), httpRequest.getUri().getPort()), 
                    creds);
        } catch (Exception e) {
            // Handle URI exceptions gracefully
            throw new RuntimeException("Error setting credentials: " + e.getMessage(), e);
        }
    }    

    /**
     * Helper method to set SSL context on a builder with reflection to handle different API versions
     */
    private void setSSLContextForBuilder(Object builder, SSLContext sslContext) {
        // We'll just try the basic SSLContext method that should be available in most builders
        try {
            Method setSSLContextMethod = builder.getClass().getMethod("setSSLContext", SSLContext.class);
            setSSLContextMethod.invoke(builder, sslContext);
        } catch (Exception e) {
            // Log error but don't fail
            System.err.println("Could not set SSL context on builder: " + e.getMessage());
        }
    }
    
    @Override
    public void close() throws IOException {
        synchronized (_lock) {
            // Close the synchronous client
            if (_httpClient != null) {
                _httpClient.close();
                _httpClient = null;
            }
            
            // Close the asynchronous client with a graceful shutdown
            if (_httpAsyncClient != null) {
                _httpAsyncClient.close(CloseMode.GRACEFUL);
                _httpAsyncClient = null;
            }
            
            // Shutdown the executor service
            if (_executor != null) {
                _executor.shutdown();
                try {
                    // Wait for pending tasks to complete
                    if (!_executor.awaitTermination(5, TimeUnit.SECONDS)) {
                        _executor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    _executor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
                _executor = null;
            }
        }
    }
    
    // static convenience methods
    
    /**
     * Returns the local host name (does not throw exceptions).
     */
    private static String getLocalHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException exc) {
            throw new UnexpectedIOException(exc);
        }
    }
    
    // convenience instances
    
    /**
     * Ignores all SSL issues
     */
    private static X509TrustManager IGNORE_SSL_TRUSTMANAGER = new X509TrustManager() {
        
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException { }
        
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException { }
        
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
        
    };
    
    /**
     * Is injected into stream to count bytes (vs characters)
     */
    private static class SafeCounter implements SharableMeasurementProvider {

        private long _counter;

        @Override
        public long getMeasurement() {
            return _counter;
        }

        @Override
        public void set(long value) {
            _counter = value;

            if (_counter > MAX_ALLOWED)
                throw new UnexpectedIOException("Too big - HTTP response over " + MAX_ALLOWED + " bytes is not allowed");
        }

        @Override
        public void add(long value) {
            _counter += value;
            
            if (_counter > MAX_ALLOWED)
                throw new UnexpectedIOException("Too big - HTTP response over " + MAX_ALLOWED + " bytes is not allowed");
        }

        @Override
        public void incr() {
            _counter++;
            
            if (_counter > MAX_ALLOWED)
                throw new UnexpectedIOException("Too big - HTTP response over " + MAX_ALLOWED + " bytes is not allowed");
        }

        @Override
        public void decr() {
            _counter--;
        }

    }

    /**
     *  A redirect strategy used to ignore all redirect directives i.e. will be manually handled
     */
    private static DefaultRedirectStrategy IGNORE_ALL_REDIRECTS = new DefaultRedirectStrategy() {

        @Override
        public boolean isRedirected(HttpRequest request, HttpResponse response, HttpContext context) {
            return false;
        }

    };
    
}