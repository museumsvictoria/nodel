package org.nodel.discovery;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import org.nodel.diagnostics.AtomicLongMeasurementProvider;
import org.nodel.threading.ThreadPool;
import org.nodel.threading.Timers;

/**
 * Contains convenience constants and services for this package.
 */
public class Discovery {
    
    /**
     * IPv4 multicast group
     */
    public static final InetAddress MDNS_GROUP = parseNumericalIPAddress("224.0.0.252");

    /**
     * IPv6 multicast group (not used here but reserved)
     */
    public static final String MDNS_GROUP_IPV6 = "FF02::FB";
    
    /**
     * Multicast port
     */
    public static final int MDNS_PORT = 5354;
    
    /**
     * (as an InetSocketAddress (with port); will never be null)
     */
    public static final InetSocketAddress GROUP_SOCKET_ADDRESS = new InetSocketAddress(MDNS_GROUP, Discovery.MDNS_PORT);
    
    /**
     * (convenience)
     */
    private Random _random = new Random();
    
    /**
     * Convenience package function.
     */
    public static Random random() {
        return instance()._random;
    }

    /**
     * (see public method)
     */
    private Timers _timerThread = new Timers("Discovery");

    /**
     * This package's timer-thread.
     */
    public static Timers timerThread() {
        return instance()._timerThread;
    }

    /**
     * (see public method)
     */
    private ThreadPool _threadPool = new ThreadPool("Discovery", 24);

    /**
     * This package's shared thread-pool
     */
    public static ThreadPool threadPool() {
        return instance()._threadPool;
    }

    /**
     * (private constructor)
     */
    private Discovery() {
    }

    /**
     * (singleton, thread-safe, non-blocking)
     */
    private static class Instance {

        private static final Discovery INSTANCE = new Discovery();

    }

    /**
     * Returns the singleton instance of this class.
     */
    private static Discovery instance() {
        return Instance.INSTANCE;
    }
    
    /**
     * (instrumentation)
     */
    static AtomicLong s_multicastOutOps = new AtomicLong();
    
    /**
     * (instrumentation)
     */
    private static AtomicLongMeasurementProvider s_multicastOutOpsMeasurement = new AtomicLongMeasurementProvider(s_multicastOutOps);
    
    /**
     * Multicast in operations.
     */
    public static AtomicLongMeasurementProvider MulticastOutOpsMeasurement() {
        return s_multicastOutOpsMeasurement;
    }    
    
    /**
     * (instrumentation)
     */
    static AtomicLong s_multicastOutData = new AtomicLong();
    
    /**
     * (instrumentation)
     */
    private static AtomicLongMeasurementProvider s_multicastOutDataMeasurement = new AtomicLongMeasurementProvider(s_multicastOutData);
    
    /**
     * Multicast out data.
     */
    public static AtomicLongMeasurementProvider MulticastOutDataMeasurement() {
        return s_multicastOutDataMeasurement;
    }    
    
    /**
     * (instrumentation)
     */
    private static AtomicLong s_multicastInOps = new AtomicLong();
    
    /**
     * (instrumentation)
     */
    private static AtomicLongMeasurementProvider s_multicastInOpsMeasurement = new AtomicLongMeasurementProvider(s_multicastInOps);
    
    /**
     * Multicast in operations.
     */
    public static AtomicLongMeasurementProvider MulticastInOpsMeasurement() {
        return s_multicastInOpsMeasurement;
    }
    
    /**
     * (instrumentation)
     */
    private static AtomicLong s_multicastInData = new AtomicLong();
    
    /**
     * (instrumentation)
     */
    private static AtomicLongMeasurementProvider s_multicastInDataMeasurement = new AtomicLongMeasurementProvider(s_multicastInData);
    
    /**
     * Multicast in data.
     */
    public static AtomicLongMeasurementProvider MulticastInDataMeasurement() {
        return s_multicastInDataMeasurement;
    }
    
    /**
     * (instrumentation)
     */
    private static AtomicLong s_unicastOutOps = new AtomicLong();
    
    /**
     * (instrumentation)
     */
    private static AtomicLongMeasurementProvider s_unicastOutOpsMeasurement = new AtomicLongMeasurementProvider(s_unicastOutOps);
    
    /**
     * Unicast in operations.
     */
    public static AtomicLongMeasurementProvider UnicastOutOpsMeasurement() {
        return s_unicastOutOpsMeasurement;
    }    
    
    /**
     * (instrumentation)
     */
    private static AtomicLong s_unicastOutData = new AtomicLong();
    
    /**
     * (instrumentation)
     */
    private static AtomicLongMeasurementProvider s_unicastOutDataMeasurement = new AtomicLongMeasurementProvider(s_unicastOutData);
    
    /**
     * Unicast out data.
     */
    public static AtomicLongMeasurementProvider UnicastOutDataMeasurement() {
        return s_unicastOutDataMeasurement;
    }    
    
    /**
     * (instrumentation)
     */
    private static AtomicLong s_unicastInOps = new AtomicLong();
    
    /**
     * (instrumentation)
     */
    private static AtomicLongMeasurementProvider s_unicastInOpsMeasurement = new AtomicLongMeasurementProvider(s_unicastInOps);
    
    /**
     * Unicast in operations.
     */
    public static AtomicLongMeasurementProvider UnicastInOpsMeasurement() {
        return s_unicastInOpsMeasurement;
    }
    
    /**
     * (instrumentation)
     */
    private static AtomicLong s_unicastInData = new AtomicLong();
    
    /**
     * (instrumentation)
     */
    private static AtomicLongMeasurementProvider s_unicastInDataMeasurement = new AtomicLongMeasurementProvider(s_unicastInData);

    /**
     * Unicast in data.
     */
    public static AtomicLongMeasurementProvider UnicastInDataMeasurement() {
        return s_unicastInDataMeasurement;
    }
    
    /**
     * (diagnostics)
     */
    public static void countIncomingPacket(DatagramPacket dp, boolean assumeMulticast) {
        if (assumeMulticast || dp.getAddress().isMulticastAddress()) {
            s_multicastInData.addAndGet(dp.getLength());
            s_multicastInOps.incrementAndGet();
        } else {
            s_unicastInData.addAndGet(dp.getLength());
            s_unicastInOps.incrementAndGet();
        }
    }

    /**
     * (diagnostics)
     */
    public static void countOutgoingPacket(DatagramPacket dp) {
        if (dp.getAddress().isMulticastAddress()) {
            s_multicastOutData.addAndGet(dp.getLength());
            s_multicastOutOps.incrementAndGet();
        } else {
            s_unicastOutData.addAndGet(dp.getLength());
            s_unicastOutOps.incrementAndGet();
        }
    }

    /**
     * Parses a dotted numerical IP address without throwing any exceptions.
     * (convenience function)
     */
    public static InetAddress parseNumericalIPAddress(String dottedNumerical) {
        try {
            return InetAddress.getByName(dottedNumerical);
            
        } catch (Exception exc) {
            throw new Error("Failed to resolve dotted numerical address - " + dottedNumerical);
        }
    }

}
