package org.nodel.http.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.Map.Entry;

import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolException;
import org.apache.http.StatusLine;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.NTCredentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.RedirectStrategy;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpTrace;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.HttpContext;
import org.nodel.Strings;
import org.nodel.Version;
import org.nodel.io.Stream;
import org.nodel.io.UnexpectedIOException;
import org.nodel.net.HTTPSimpleResponse;
import org.nodel.net.NodelHTTPClient;

public class ApacheNodelHttpClient extends NodelHTTPClient {
    
    private Object _lock = new Object();
    
    /**
     * The Apache Http Client
     * (created lazily)
     */
    private CloseableHttpClient _httpClient;
    
    /**
     * Required with 'applySecurity'
     */
    private CredentialsProvider _credentialsProvider;

    /**
     * Mainly used for adjusting timeouts
     */
    private RequestConfig _requestConfig;
    
    /**
     * This needs to be done lazily because proxy can only be set up once
     * 
     * (uses double-check singleton)
     */
    private void lazyInit() {
        if (_httpClient == null) {
            synchronized (_lock) {
                if (_httpClient != null)
                    return;
                
                HttpClientBuilder builder = HttpClients.custom()
                        .setUserAgent("Nodel/" + Version.shared().version)
                        
                        // need to reference this later
                        .setDefaultCredentialsProvider(_credentialsProvider = new BasicCredentialsProvider())
                        
                        // unrestricted connections
                        .setMaxConnTotal(1000)
                        .setMaxConnPerRoute(1000)
                       
                        // default timeouts
                        .setDefaultRequestConfig(_requestConfig = RequestConfig.custom()
                                .setConnectTimeout(DEFAULT_CONNECTTIMEOUT)
                                .setSocketTimeout(DEFAULT_READTIMEOUT)
                                .build());
                
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
        }
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
            proxyHost = proxyAddress.substring(0, lastIndexOfColon - 1);
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
                _credentialsProvider.setCredentials(authScope, new UsernamePasswordCredentials(proxyUsername, proxyPassword));
            } else {
                // normally used with NTLM
                _credentialsProvider.setCredentials(authScope, new NTCredentials(userPart, proxyPassword, getLocalHostName(), domainPart));
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
            builder.setSSLContext(sslContext);
            
            SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(sslContext, new NoopHostnameVerifier());
            builder.setSSLSocketFactory(sslSocketFactory);
            
            Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
                    .register("http", PlainConnectionSocketFactory.getSocketFactory())
                    .register("https", sslSocketFactory)
                    .build();
            
            PoolingHttpClientConnectionManager connMgr = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
            builder.setConnectionManager(connMgr);
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

        // (out of scope for clean up purposes)
        InputStream inputStream = null;
        
        boolean executed = false;
        
        try {
            // GET, POST or other
            HttpRequestBase request = selectHTTPbyMethod(method, body, fullURL);
            
            // if username is supplied, apply security
            if (!Strings.isBlank(username))
                applySecurity(request, username, !Strings.isEmpty(password) ? password : "");            
            
            // set 'Content-Type' header
            if (!Strings.isBlank(contentType))
                request.setHeader("Content-Type", contentType);

            // add (or override) any request headers
            if (headers != null) {
                for (Entry<String, String> entry : headers.entrySet())
                    request.setHeader(entry.getKey(), entry.getValue());
            }
            
            if (!Strings.isEmpty(body)) {
                if (!(request instanceof HttpEntityEnclosingRequest))
                    throw new IllegalArgumentException("The HTTP method does not accept a body - " + method);
                
                HttpEntityEnclosingRequest httpWithBody = (HttpEntityEnclosingRequest) request;
                httpWithBody.setEntity(new StringEntity(body));
            }
            
            // set any timeouts that apply
            if (connectTimeout != null || readTimeout != null) {
                int actualConnTimeout = connectTimeout != null ? connectTimeout : DEFAULT_CONNECTTIMEOUT;
                int actualReadTimeout = readTimeout != null ? readTimeout : DEFAULT_READTIMEOUT;

                request.setConfig(RequestConfig.copy(_requestConfig)
                        .setConnectTimeout(actualConnTimeout)
                        .setSocketTimeout(actualReadTimeout)
                        .build());
            }
            
            // perform the request 
            
            // (and count it)
            executed = true;
            s_activeConnections.incrementAndGet();
            
            HttpResponse httpResponse = _httpClient.execute(request);
            
            // count the post now
            if (!Strings.isEmpty(body))
                s_sendRate.addAndGet(body.length());
            
            // safely get the response (regardless of response code for now)
            
            // safely get the content encoding
            HttpEntity entity = httpResponse.getEntity();
            Header contentEncodingHeader = entity.getContentEncoding();
            String contentEncoding = null;
            if (contentEncodingHeader != null)
                contentEncoding = contentEncodingHeader.getValue();
            
            inputStream = entity.getContent();
            
            // deals with encoding
            InputStreamReader isr = null;
            
            // try using the given encoding
            if (!Strings.isBlank(contentEncoding)) {
                // any unknown content encodings will cause an exception to propagate
                isr = new InputStreamReader(inputStream,  contentEncoding);
            } else {
                isr = new InputStreamReader(inputStream);
            }
            
            String content = Stream.readFully(isr);
            if (content != null)
                s_receiveRate.addAndGet(content.length());
            
            StatusLine statusLine = httpResponse.getStatusLine();
            
            HTTPSimpleResponse result = new HTTPSimpleResponse();
            result.content = content;
            result.statusCode = statusLine.getStatusCode();
            result.reasonPhrase = statusLine.getReasonPhrase();
            
            for (Header header : httpResponse.getAllHeaders())
                result.addHeader(header.getName(), header.getValue());
            
            return result;
            
        } catch (IOException exc) {
            throw new UnexpectedIOException(exc);
            
        } finally {
            Stream.safeClose(inputStream);
            
            if (executed)
                s_activeConnections.decrementAndGet();
        }
    }
    
    /**
     * (convenience function)
     */
    private static HttpRequestBase selectHTTPbyMethod(String method, String body, String url) {
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
        
        else
            throw new IllegalArgumentException("Unknown HTTP method - " + method);
    }    
    
    /**
     * Applies security for a given HTTP request.
     * @throws AuthenticationException 
     */
    private void applySecurity(HttpRequestBase httpRequest, String username, String password) {
        Credentials creds;

        // in case of NTLM, check for '\' in username
        String userPart = username;
        String domainPart = null;
        int indexOfBackSlash = username.indexOf('\\');
        if (indexOfBackSlash > 0) {
            // NTLM
            domainPart = username.substring(0, indexOfBackSlash);
            userPart = username.substring(Math.min(username.length() - 1, indexOfBackSlash + 1));

            creds = new NTCredentials(userPart, password, getLocalHostName(), domainPart);

        } else {
            // BasicAuth
            creds = new UsernamePasswordCredentials(username, password);

            // pre-emptive
            try {
                httpRequest.setHeader(new BasicScheme().authenticate(creds, httpRequest, null));
            } catch (AuthenticationException e) {
                throw new RuntimeException(e);
            }
        }

        _credentialsProvider.setCredentials(new AuthScope(httpRequest.getURI().getHost(), httpRequest.getURI().getPort()), creds);
    }    

    @Override
    public void close() throws IOException {
        Stream.safeClose(_httpClient);
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
     *  A redirect strategy used to ignore all redirect directives i.e. will be manually handled
     */
    private static RedirectStrategy IGNORE_ALL_REDIRECTS = new RedirectStrategy() {

        @Override
        public boolean isRedirected(HttpRequest request, HttpResponse response, HttpContext context) throws ProtocolException {
            return false;
        }

        @Override
        public HttpUriRequest getRedirect(HttpRequest request, HttpResponse response, HttpContext context) throws ProtocolException {
            return null;
        }
        
    };
    
}