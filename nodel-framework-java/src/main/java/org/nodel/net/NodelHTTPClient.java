package org.nodel.net;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.StatusLine;
import org.apache.http.auth.AuthSchemeRegistry;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.NTCredentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.params.CookiePolicy;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.params.ConnManagerParams;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;
import org.nodel.Strings;
import org.nodel.core.Nodel;
import org.nodel.diagnostics.AtomicIntegerMeasurementProvider;
import org.nodel.diagnostics.AtomicLongMeasurementProvider;
import org.nodel.diagnostics.Diagnostics;
import org.nodel.diagnostics.MeasurementProvider;
import org.nodel.io.Stream;
import org.nodel.json.JSONObject;

/**
 * An HTTP client with some sensible timeouts, support for NTLM and a multi-threaded connection manager. 
 */
public class NodelHTTPClient extends DefaultHttpClient {
    
    /**
     * (counter)
     */
    private static AtomicInteger s_activeConnections = new AtomicInteger();
    
    /**
     * (read-only version)
     */
    private static MeasurementProvider s_roActiveConnections = new AtomicIntegerMeasurementProvider(s_activeConnections);
    
    /** 
     * (counter)
     */
    private static AtomicLong s_receiveRate = new AtomicLong();

    /**
     * (read-only version)
     */
    private static MeasurementProvider s_roReceiveRate = new AtomicLongMeasurementProvider(s_receiveRate);
    
    /** 
     * (counter)
     */
    private static AtomicLong s_sendRate = new AtomicLong();

    /**
     * (read-only version)
     */
    private static MeasurementProvider s_roSendRate = new AtomicLongMeasurementProvider(s_sendRate);    
    
    
    /** 
     * (counter)
     */
    private static AtomicLong s_attemptRate = new AtomicLong();

    /**
     * (read-only version)
     */
    private static MeasurementProvider s_roAttemptRate = new AtomicLongMeasurementProvider(s_attemptRate);

    /**
     * (private constructor)
     */
    static {
        Diagnostics.shared().registerCounter("HTTP client.Connections", s_roActiveConnections, false);
        Diagnostics.shared().registerCounter("HTTP client.Attempt rate", s_roAttemptRate, true);
        Diagnostics.shared().registerCounter("HTTP client.Send chars", s_roSendRate, true);
        Diagnostics.shared().registerCounter("HTTP client.Receive chars", s_roReceiveRate, true);
    }
    
    @Override
    protected HttpParams createHttpParams() {
        BasicHttpParams params = new BasicHttpParams();
        HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
        HttpProtocolParams.setContentCharset(params, HTTP.UTF_8);
        
        HttpProtocolParams.setUserAgent(params, Nodel.getAgent());

        params.setLongParameter(ConnManagerParams.TIMEOUT, 15000);
        HttpConnectionParams.setConnectionTimeout(params, 15000);
        HttpConnectionParams.setSoTimeout(params, 15000);
        ConnManagerParams.setTimeout(params, 15000);
        
        // this suppresses some excessive logging related to cookies on some sites
        params.setParameter(ClientPNames.COOKIE_POLICY, CookiePolicy.BROWSER_COMPATIBILITY);
        
        return params;
    }
    
    @Override
    protected ClientConnectionManager createClientConnectionManager() {
        SchemeRegistry registry = new SchemeRegistry();
        registry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
        registry.register(new Scheme("https", SSLSocketFactory.getSocketFactory(), 443));
        ThreadSafeClientConnManager connManager = new ThreadSafeClientConnManager(this.getParams(), registry);
        return connManager;
    }
    
    @Override
    protected AuthSchemeRegistry createAuthSchemeRegistry() {
        AuthSchemeRegistry registry = super.createAuthSchemeRegistry();
        
        // add support for NTLM
        registry.register("ntlm", NTLMSchemeFactory.instance());
        
        return registry;
    }
    
    // Safe URL timeouts are optimised for servers that are likely available and responsive.

    private final static int DEFAULT_CONNECTTIMEOUT = 10000;
    
    private final static int DEFAULT_READTIMEOUT = 15000;
    
    /**
     * A very simple URL getter. queryArgs, contentType, postData are all optional.
     * 
     * Safe timeouts are used to avoid non-responsive servers being able to hold up connections indefinitely.
     */
    public String makeRequest(String urlStr, Map<String, String> query, 
                         String username, String password, 
                         Map<String, String> headers, String reference, String contentType, 
                         String post, 
                         Integer connectTimeout, Integer readTimeout,
                         String proxyAddress, String proxyUsername, String proxyPassword) throws IOException {
        // record rate of new connections
        s_attemptRate.incrementAndGet();

        // construct the full URL (includes query string)
        String fullURL = buildQueryString(urlStr, query);

        // (out of scope for clean up purposes)
        InputStream inputStream = null;

        try {
            s_activeConnections.incrementAndGet();
            
            // 'get' or 'post'?
            HttpRequestBase request = (post == null ? new HttpGet(fullURL) : new HttpPost(fullURL));
            
            // used if special parameters will be set up
            // (created only once)
            HttpParams params = null;

            // set 'Content-Type' header
            if (!Strings.isNullOrEmpty(contentType))
                request.setHeader("Content-Type", contentType);

            // add (or override) any request headers
            if (headers != null) {
                for (Entry<String, String> entry : headers.entrySet())
                    request.setHeader(entry.getKey(), entry.getValue());
            }
            
            if (!Strings.isNullOrEmpty(post)) {
                HttpPost httpPost = (HttpPost) request;
                httpPost.setEntity(new StringEntity(post));
            }
            
            // if username is supplied, apply security
            if (!Strings.isNullOrEmpty(username))
                applySecurity(this, request, username, !Strings.isNullOrEmpty(password) ? password : "");
            
            // set any timeouts that apply
            if (connectTimeout != null || readTimeout != null) {
                // copy the params
                if (params == null)
                    params = this.getParams().copy();
                
                int actualConnTimeout = connectTimeout != null ? connectTimeout : DEFAULT_CONNECTTIMEOUT;
                int actualReadTimeout = readTimeout != null ? readTimeout : DEFAULT_READTIMEOUT;

                // there are a few places where timeouts can be set within this framework
                // (this may be more than needed)
                params.setLongParameter(ConnManagerParams.TIMEOUT, actualConnTimeout);
                HttpConnectionParams.setConnectionTimeout(params, actualConnTimeout);
                HttpConnectionParams.setSoTimeout(params, actualReadTimeout);
                ConnManagerParams.setTimeout(params, actualConnTimeout);
            }
            
            // using a proxy?
            if (proxyAddress != null) {
                // copy the params
                if (params == null)
                    params = this.getParams().copy();                
                
                String[] proxyAddressParts = proxyAddress.split(":");
                if (proxyAddressParts.length != 2)
                    throw new IllegalArgumentException("Proxy address is not in form host:port");

                String proxyHost = proxyAddressParts[0];
                int proxyPort;
                try {
                    proxyPort = Integer.parseInt(proxyAddressParts[1]);
                    
                } catch (Exception exc) {
                    throw new IllegalArgumentException("Port specified was not a number.");
                }

                // set proxy credentials if provided
                if (proxyUsername != null) {
                    if (proxyPassword == null)
                        throw new IllegalArgumentException("Proxy user");
                        
                    this.getCredentialsProvider().setCredentials(new AuthScope(proxyHost, proxyPort),
                            new UsernamePasswordCredentials(proxyUsername, proxyPassword));
                }

                // force the proxy
                HttpHost proxy = new HttpHost(proxyHost, proxyPort);
                this.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY, proxy);
            }
            
            // use the new params (based on copy)
            if (params != null)
                request.setParams(params);

            // perform the request
            HttpResponse httpResponse;
            httpResponse = this.execute(request);
            
            // count the post now
            if (!Strings.isNullOrEmpty(post))
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
            if (!Strings.isNullOrEmpty(contentEncoding)) {
                // any unknown content encodings will cause an exception to propagate
                isr = new InputStreamReader(inputStream,  contentEncoding);
            } else {
                isr = new InputStreamReader(inputStream);
            }
            
            String content = Stream.readFully(isr);
            if (content != null)
                s_receiveRate.addAndGet(content.length());
            
            StatusLine statusLine = httpResponse.getStatusLine();
            
            entity.consumeContent();
            
            if (statusLine.getStatusCode() == HttpURLConnection.HTTP_OK) {
                // 'OK' response
                return content;
            } else {
                // raise an exception including the content
                throw new IOException(String.format("Server returned '%s' with content %s", 
                        statusLine, Strings.isNullOrEmpty(content) ? "<empty>" : JSONObject.quote(content)));
            }
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

                if (Strings.isNullOrEmpty(key) || Strings.isNullOrEmpty(value))
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
     * Applies security for a given HTTP request.
     */
    private static void applySecurity(DefaultHttpClient client, HttpRequestBase httpRequest, String username, String password) {
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
            client.getCredentialsProvider().setCredentials(new AuthScope(httpRequest.getURI().getHost(), httpRequest.getURI().getPort(), AuthScope.ANY_SCHEME), creds);

            httpRequest.addHeader(BasicScheme.authenticate(creds, "US-ASCII", false));
        } else {
            // normally used with NTLM
            client.getCredentialsProvider().setCredentials(new AuthScope(httpRequest.getURI().getHost(), httpRequest.getURI().getPort(), AuthScope.ANY_SCHEME), 
                    new NTCredentials(userPart, password, "WORKSTATION", domainPart));
        }
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
    
}