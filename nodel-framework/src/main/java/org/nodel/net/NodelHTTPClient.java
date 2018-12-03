package org.nodel.net;

import java.io.Closeable;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URLEncoder;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.nodel.Strings;
import org.nodel.diagnostics.AtomicIntegerMeasurementProvider;
import org.nodel.diagnostics.AtomicLongMeasurementProvider;
import org.nodel.diagnostics.Diagnostics;
import org.nodel.diagnostics.MeasurementProvider;
import org.nodel.json.JSONObject;

/**
 * An HTTP client with some sensible timeouts, support for NTLM and a multi-threaded connection manager. 
 */
public abstract class NodelHTTPClient implements Closeable {
    
    /**
     * (counter)
     */
    protected static AtomicInteger s_activeConnections = new AtomicInteger();
    
    /**
     * (read-only version)
     */
    private static MeasurementProvider s_roActiveConnections = new AtomicIntegerMeasurementProvider(s_activeConnections);
    
    /** 
     * (counter)
     */
    protected static AtomicLong s_receiveRate = new AtomicLong();

    /**
     * (read-only version)
     */
    private static MeasurementProvider s_roReceiveRate = new AtomicLongMeasurementProvider(s_receiveRate);
    
    /** 
     * (counter)
     */
    protected static AtomicLong s_sendRate = new AtomicLong();

    /**
     * (read-only version)
     */
    private static MeasurementProvider s_roSendRate = new AtomicLongMeasurementProvider(s_sendRate);    
    
    
    /** 
     * (counter)
     */
    protected static AtomicLong s_attemptRate = new AtomicLong();

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
        Diagnostics.shared().registerCounter("HTTP client.Send rate", s_roSendRate, true);
        Diagnostics.shared().registerCounter("HTTP client.Receive rate", s_roReceiveRate, true);
    }
    
    
    // Safe URL timeouts are optimised for servers that are likely available and responsive.

    protected final static int DEFAULT_CONNECTTIMEOUT = 10000;
    
    protected final static int DEFAULT_READTIMEOUT = 15000;
    
    /**
     * (see setter)
     */
    protected String _proxyAddress;
    
    /**
     * (see setter)
     */    
    protected String _proxyUsername;
    
    /**
     * (see setter)
     */    
    protected String _proxyPassword;
    
    /**
     * (see setter)
     */
    public void setProxy(String address, String username, String password) {
        _proxyAddress = address;
        _proxyUsername = username;
        _proxyPassword = password;
    }
    
    /**
     * (see setter)
     */
    protected boolean _ignoreSSL = false;
    
    /**
     * Whether or not all SSL related issues should be ignored / allowed.
     */
    public void setIgnoreSSL(boolean value) {
        _ignoreSSL = value;
    }
    
    /**
     * (see setter)
     */
    protected boolean _ignoreRedirects;
    
    /**
     * Manually handle redirects
     */
    public void setIgnoreRedirects(boolean value) {
        _ignoreRedirects = value;
    }    
    
    /**
     * A very simple URL getter. queryArgs, contentType, postData are all optional.
     * 
     * Safe timeouts are used to avoid non-responsive servers being able to hold up connections indefinitely.
     */
    public abstract HTTPSimpleResponse makeRequest(String urlStr, String method, Map<String, String> query, 
                         String username, String password, 
                         Map<String, String> headers, String contentType, 
                         String post, 
                         Integer connectTimeout, Integer readTimeout);
    
    /**
     * Same as above except returns the content on HTTP_OK 
     */
    public String makeSimpleRequest(String urlStr, String method, Map<String, String> query,
                              String username, String password,
                              Map<String, String> headers, String contentType, String post,
                              Integer connectTimeout, Integer readTimeout) {
        HTTPSimpleResponse response = makeRequest(urlStr, method, query, username, password, headers, contentType, post, connectTimeout, readTimeout);

        if (response.statusCode == HttpURLConnection.HTTP_OK) {
            // 'OK' response, just return content
            return response.content;
            
        } else {
            // non-OK, so raise an exception including the content
            throw new RuntimeException(String.format("Server returned '%s' with content %s", 
                    response.statusCode + " " + response.reasonPhrase, 
                    Strings.isEmpty(response.content) ? "<empty>" : JSONObject.quote(response.content)));
        }        
    }
    
    /**
     * Builds up query string if args given, e.g. ...?name=My%20Name&surname=My%20Surname
     */
    public static String urlEncodeQuery(Map<String, String> query) {
        if (query == null)
            return null;
        
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
            return sb.toString();
        else
            return null;
    }
    
    /**
     * (exception-less, convenience function)
     */
    public static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
            
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

}