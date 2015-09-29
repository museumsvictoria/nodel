package org.nodel.diagnostics;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.joda.time.DateTime;
import org.nodel.Environment;
import org.nodel.Threads;
import org.nodel.core.Nodel;
import org.nodel.discovery.NodelAutoDNS;
import org.nodel.reflection.Serialisation;
import org.nodel.reflection.Service;
import org.nodel.reflection.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds framework methods and statistics. 
 */
public class Diagnostics {
    
    /**
     * How much stats history to keep.
     */
    private final static int HISTORY_SIZE = 100;
    
    /**
     * The stats period.
     */
    private final static int PERIOD = 2500;
    
    /**
     * Start time.
     */
    private DateTime _startTime = DateTime.now();
    
    /**
     * The permanent non-daemon thread to keep track of the counters.
     * (may not be started in thread-restricted environments)
     */
    private Thread _thread;
    
    /**
     * The last time stamp
     */
    private long _lastTime = System.nanoTime();
    
    @Service(name = "measurements", title = "Measurements", desc = "The list of performance measurement data.", genericClassA = MeasurementHistory.class)
    public Queue<MeasurementHistory> _measurements = new ConcurrentLinkedQueue<MeasurementHistory>();
    
    /**
     * (logging)
     */
    private Logger _logger = LoggerFactory.getLogger(this.getClass().getName());
    
    /**
     * A lookup map for counter names.
     */
    private Set<String> _counterNames = new HashSet<String>();
    
    /**
     * (use 'instance()' for public use)
     */
    private Diagnostics() {
        // register a few standard counters
        this.registerCounter("Java runtime.Free bytes", new MeasurementProvider() {
            
            @Override
            public long getMeasurement() {
                return freeMemory();
            }
            
        }, false);
        
        // use a completely independent thread to avoid debugging clashes
        
        // (don't bother if we can't create thread, e.g. Google App Engine environment)
        SecurityManager securityManager = System.getSecurityManager();
        
        try {
            if (securityManager != null)
                securityManager.checkPermission(new RuntimePermission("modifyThreadGroup"));

            _thread = new Thread(new Runnable() {

                @Override
                public void run() {
                    // continually record the stats
                    for (;;) {
                        recordStats();
                        Threads.sleep(PERIOD);
                    }
                }

            });
            _thread.setName("nodel.diagnostics");
            _thread.setDaemon(true);
            _thread.start();
        } catch (Exception exc) {
            String suffix = (securityManager == null ? " (no security manager was present to test against but tried anyway)" : " (tested against a security manager)"); 
                    
            _logger.warn("This runtime did not allow the creation of threads; this may or may not present a problem. Will continue..." + suffix);
        }

        this.registerCounter("Discovery multicast.Receives", NodelAutoDNS.MulticastInOpsMeasurement(), true);
        this.registerCounter("Discovery multicast.Receive bytes", NodelAutoDNS.MulticastInDataMeasurement(), true);
        this.registerCounter("Discovery multicast.Sends", NodelAutoDNS.MulticastOutOpsMeasurement(), true);
        this.registerCounter("Discovery multicast.Send bytes", NodelAutoDNS.MulticastOutDataMeasurement(), true);
        this.registerCounter("Discovery unicast.Receives", NodelAutoDNS.UnicastInOpsMeasurement(), true);
        this.registerCounter("Discovery unicast.Receive bytes", NodelAutoDNS.UnicastInDataMeasurement(), true);
        this.registerCounter("Discovery unicast.Sends", NodelAutoDNS.UnicastOutOpsMeasurement(), true);
        this.registerCounter("Discovery unicast.Send bytes", NodelAutoDNS.UnicastOutDataMeasurement(), true);

        // dump the environment
        _logger.info("Environment dump: {}", Serialisation.serialise(this));
    } // (init)
    
    @Value(name="startTime", title = "Start time", desc = "When this service was started.")
    public DateTime startTime() {
        return _startTime;
    }
    
    @Value(name = "systemProperties", title = "System properties", desc = "The list of built-in system properties.")
    public Properties properties() {
        return System.getProperties();
    }
    
    @Value(name = "vmArgs", title = "VM arguments", desc = "The arguments supplied to this instance of the VM.")
    public List<String> vmArgs() {
        return Environment.getVMArgs();
    }
    
    @Value(name = "availableProcessors", title = "Available processors", desc = "The number of available processors.")
    public int availableProcessors() {
        return Runtime.getRuntime().availableProcessors();
    }
    
    @Value(name = "freeMemory", title = "Free memory", desc = "Free memory (in bytes) as reported by Java; not necessarily stable.")
    public long freeMemory() {
        return Runtime.getRuntime().freeMemory();
    }
    
    @Value(name = "maxMemory", title = "Maximum memory", desc = "Maximum memory (in bytes) as reported by Java; not necessarily stable.")
    public long maxMemory() {
        return Runtime.getRuntime().maxMemory();
    }
    
    @Value(name = "totalMemory", title = "Total memory", desc = "Total memory (in bytes) as reported by Java; not necessarily stable.")
    public long totalMemory() {
        return Runtime.getRuntime().totalMemory();
    }
    
    @Value(name = "agent", title = "Agent", desc = "The nodel agent.")
    public String agent() {
        return Nodel.getAgent();
    }    
    
    @Value(name = "hostname", title = "Hostname", desc = "The name of the host.")
    public String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName().toUpperCase();
        } catch (UnknownHostException e) {
            return "UNKNOWN";
        }
    }
    
    @Value(name = "httpAddress", title = "HTTP address", desc = "The address of the HTTP server.")
    public String httpAddress() {
        return Nodel.getHTTPAddress();
    }
    
    @Service(name = "gc", title = "Garbage collect", desc = "Perform a 'garbage collect' operation; not necessarily stable.")
    public void gc() {
        System.gc();
    } // (method)
    
    /**
     * Registers a new counter to be tracked.
     */
    public void registerCounter(String name, MeasurementProvider provider, boolean isRate) {
        synchronized (_counterNames) {
            if (_counterNames.contains(name))
                throw new IllegalStateException(name + " is already present");
            
            register0(name, provider, isRate);
        }
    }
    
    /**
     * (must be pre-checked)
     */
    private void register0(String name, MeasurementProvider provider, boolean isRate) {
        _counterNames.add(name);

        MeasurementHistory counter = new MeasurementHistory(name, provider, HISTORY_SIZE, isRate);

        _measurements.add(counter);
    }
    
    /**
     * Returns an existing counter to uses or a newly registered one.
     * (Performs queue scanning, so reduce hits where possible.)
     */
    public SharableMeasurementProvider registerSharableCounter(String name, boolean isRate) {
        synchronized (_counterNames) {
            if (_counterNames.contains(name)) {
                for (MeasurementHistory history : _measurements) {
                    if (history.getName().equals(name)) {
                        MeasurementProvider provider = history.getMeasurementProvider();
                        if (provider instanceof SharableMeasurementProvider)
                            return (SharableMeasurementProvider) provider;
                        else
                            throw new IllegalStateException("Counter '" + name + "' is registered but not sharable.");
                    
                    }
                }
                // could only get here if 'counterNames' is out of sync with 'measurements', which
                // should not be possible
                throw new IllegalStateException("Counter '" + name + "' is unexpectedly missing.");
                
            } else {
                LongSharableMeasurementProvider provider = new LongSharableMeasurementProvider();
                register0(name, provider, isRate);
                return provider;
            }
        }
    }
    
    /**
     * Records stats periodically.
     * (timer entry-point)
     */
    private void recordStats() {
        long now = System.nanoTime();
        
        long timeDiff = now - _lastTime;
        
        Iterator<MeasurementHistory> iterator = _measurements.iterator();
        while(iterator.hasNext()) {
            MeasurementHistory measurement = iterator.next();
            
            measurement.recordMeasurement(timeDiff);
        } // (while)
        
        _lastTime = now;
    }
    
    /**
     * (singleton, thread-safe, lazy init)
     */
    private static class Instance {
        
        private static final Diagnostics SHARED = new Diagnostics();
        
    } // (class)
    
    /**
     * Returns an instance which can be shared.
     */
    public static Diagnostics shared() {
        return Instance.SHARED;
    } // (method)

} // (class)
