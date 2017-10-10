package org.nodel.net;

import java.io.Closeable;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.nodel.diagnostics.AtomicIntegerMeasurementProvider;
import org.nodel.diagnostics.AtomicLongMeasurementProvider;
import org.nodel.diagnostics.Diagnostics;
import org.nodel.diagnostics.MeasurementProvider;

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
        Diagnostics.shared().registerCounter("HTTP client.Send chars", s_roSendRate, true);
        Diagnostics.shared().registerCounter("HTTP client.Receive chars", s_roReceiveRate, true);
    }
    
    
    // Safe URL timeouts are optimised for servers that are likely available and responsive.

    protected final static int DEFAULT_CONNECTTIMEOUT = 10000;
    
    protected final static int DEFAULT_READTIMEOUT = 15000;
    
    /**
     * A very simple URL getter. queryArgs, contentType, postData are all optional.
     * 
     * Safe timeouts are used to avoid non-responsive servers being able to hold up connections indefinitely.
     */
    public abstract String makeRequest(String urlStr, Map<String, String> query, 
                         String username, String password, 
                         Map<String, String> headers, String reference, String contentType, 
                         String post, 
                         Integer connectTimeout, Integer readTimeout,
                         String proxyAddress, String proxyUsername, String proxyPassword);
    
}