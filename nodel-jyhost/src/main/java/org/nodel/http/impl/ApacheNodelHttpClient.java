package org.nodel.http.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.NTCredentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.nodel.Strings;
import org.nodel.io.Stream;
import org.nodel.io.UnexpectedIOException;
import org.nodel.json.JSONObject;
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
    private void tryInitHttpClient(String proxyAddress, String proxyUsername, String proxyPassword) {
        if (_httpClient == null) {
            synchronized (_lock) {
                if (_httpClient != null)
                    return;
                
                HttpClientBuilder httpClientBuilder = HttpClients.custom()
                        
                        // need to reference this later
                        .setDefaultCredentialsProvider(_credentialsProvider = new BasicCredentialsProvider())
                        
                        // default timeouts
                        .setDefaultRequestConfig(_requestConfig = RequestConfig.custom()
                                .setConnectTimeout(DEFAULT_CONNECTTIMEOUT)
                                .setSocketTimeout(DEFAULT_READTIMEOUT)
                                .build());
                
                // using a proxy?
                if (proxyAddress != null) {
                    String[] proxyAddressParts = proxyAddress.split(":");
                    if (proxyAddressParts.length != 2)
                        throw new IllegalArgumentException("Proxy address is not in form host:port");
                    
                    String proxyHost = proxyAddressParts[0];
                    int proxyPort;
                    try {
                        proxyPort = Integer.parseInt(proxyAddressParts[1]);
                        
                    } catch (Exception exc) {
                        throw new IllegalArgumentException("Proxy port specified was not a number.");
                    }
                    
                    HttpHost proxy = new HttpHost(proxyHost, proxyPort);
                    
                    // set proxy credentials if provided
                    if (proxyUsername != null) {
                        if (proxyPassword == null)
                            throw new IllegalArgumentException("Proxy user");
                        
                        _credentialsProvider.setCredentials(new AuthScope(proxy),
                                new UsernamePasswordCredentials(proxyUsername, proxyPassword));
                    }
                    
                    httpClientBuilder.setRoutePlanner(new DefaultProxyRoutePlanner(proxy));
                }
                
                _httpClient = httpClientBuilder.build();
            }
        }
    }
    
    @Override
    public String makeRequest(String urlStr, Map<String, String> query, 
                         String username, String password, 
                         Map<String, String> headers, String reference, String contentType, 
                         String post, 
                         Integer connectTimeout, Integer readTimeout,
                         String proxyAddress, String proxyUsername, String proxyPassword) {
        
        // sets up proxy *only* if it's the first the request
        tryInitHttpClient(proxyAddress, proxyUsername, proxyPassword);
        
        // record rate of new connections
        s_attemptRate.incrementAndGet();
        
        // construct the full URL (includes query string)
        String fullURL = buildQueryString(urlStr, query);

        // (out of scope for clean up purposes)
        InputStream inputStream = null;
        
        URI uri;
        try {
            uri = new URI(fullURL);
            
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        
        try {
            s_activeConnections.incrementAndGet();
            
            // if username is supplied, apply security
            if (!Strings.isBlank(username))
                applySecurity(uri, username, !Strings.isEmpty(password) ? password : "");
            
            // 'get' or 'post'?
            HttpRequestBase request = (post == null ? new HttpGet(uri) : new HttpPost(uri));
            
            // set 'Content-Type' header
            if (!Strings.isBlank(contentType))
                request.setHeader("Content-Type", contentType);

            // add (or override) any request headers
            if (headers != null) {
                for (Entry<String, String> entry : headers.entrySet())
                    request.setHeader(entry.getKey(), entry.getValue());
            }
            
            if (!Strings.isEmpty(post)) {
                HttpPost httpPost = (HttpPost) request;
                httpPost.setEntity(new StringEntity(post));
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
            HttpResponse httpResponse;
            httpResponse = _httpClient.execute(request);
            
            // count the post now
            if (!Strings.isEmpty(post))
                s_sendRate.addAndGet(post.length());
            
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
            
            if (statusLine.getStatusCode() == HttpURLConnection.HTTP_OK) {
                // 'OK' response
                return content;
            } else {
                // raise an exception including the content
                throw new IOException(String.format("Server returned '%s' with content %s", 
                        statusLine, Strings.isEmpty(content) ? "<empty>" : JSONObject.quote(content)));
            }
        } catch (IOException exc) {
            throw new UnexpectedIOException(exc);
            
        } finally {
            Stream.safeClose(inputStream);
            
            s_activeConnections.decrementAndGet();
        }
    }

    /**
     * Builds up query string if args given, e.g. ...?name=My%20Name&surname=My%20Surname
     */
    private static String buildQueryString(String urlStr, Map<String, String> query) {
        StringBuilder queryArg = null;
        if (query != null) {
            StringBuilder sb = new StringBuilder();

            for (Entry<String, String> entry : query.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();

                // 'key' must have length, 'value' doesn't have to
                if (Strings.isEmpty(key) || value == null)
                    continue;

                if (sb.length() > 0)
                    sb.append('&');

                sb.append(urlEncode(key))
                  .append('=')
                  .append(urlEncode(value));
            }
            
            if (sb.length() > 0)
                queryArg = sb;
        }

        String fullURL;
        if (queryArg == null)
            fullURL = urlStr;
        else
            fullURL = String.format("%s?%s", urlStr, queryArg);
        return fullURL;
    }
    
    /**
     * (exception-less, convenience function)
     */
    private static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }
    
    /**
     * Applies security for a given HTTP request.
     */
    private void applySecurity(URI location, String username, String password) {
        // in case of NTLM, check for "\" in username
        String userPart = username;
        String domainPart = null;
        int indexOfBackSlash = username.indexOf('\\');
        if (indexOfBackSlash > 0) {
            domainPart = username.substring(0, indexOfBackSlash);
            userPart = username.substring(Math.min(username.length() - 1, indexOfBackSlash + 1));
        }

        if (domainPart == null) {
            UsernamePasswordCredentials creds = new UsernamePasswordCredentials(username, password);

            // basic or digest or whatever
            _credentialsProvider.setCredentials(new AuthScope(location.getHost(), location.getPort(), AuthScope.ANY_SCHEME), creds);

            // TODO: REMOVE IF UNNCESSARY
            // httpRequest.addHeader(BasicScheme.authenticate(creds, "US-ASCII", false));
        } else {
            // normally used with NTLM
            _credentialsProvider.setCredentials(new AuthScope(location.getHost(), location.getPort(), AuthScope.ANY_SCHEME), 
                    new NTCredentials(userPart, password, "WORKSTATION", domainPart));
        }
    }    

    @Override
    public void close() throws IOException {
        _httpClient.close();
    }        
    
}