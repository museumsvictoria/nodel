package org.nodel.core;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;
import org.nodel.Environment;
import org.nodel.Threads;
import org.nodel.discovery.NodelAutoDNS;
import org.nodel.logging.MeasurementHistory;
import org.nodel.logging.MeasurementProvider;
import org.nodel.reflection.Serialisation;
import org.nodel.reflection.Service;
import org.nodel.reflection.Value;

/**
 * Holds framework methods and statistics. 
 */
public class Framework {
    
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
    private Logger _logger = LogManager.getLogger(this.getClass().getName());
    
    /**
     * (use 'instance()' for public use)
     */
    private Framework() {
        // register a few standard counters
        this.registerCounter("system_freememory", new MeasurementProvider() {
            
            @Override
            public long getMeasurement() {
                return freeMemory();
            }
            
        }, false);
        
        // use a completely independent thread to avoid debugging clashes
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
        _thread.setName("frameworkcounters");
        _thread.setDaemon(true);
        _thread.start();        
        
        this.registerCounter("multicast_in_ops", NodelAutoDNS.MulticastInOpsMeasurement(), true);
        this.registerCounter("multicast_in_data", NodelAutoDNS.MulticastInDataMeasurement(), true);
        this.registerCounter("multicast_out_ops", NodelAutoDNS.MulticastOutOpsMeasurement(), true);
        this.registerCounter("multicast_out_data",NodelAutoDNS.MulticastOutDataMeasurement(), true);
        this.registerCounter("unicast_in_ops", NodelAutoDNS.UnicastInOpsMeasurement(), true);
        this.registerCounter("unicast_in_data", NodelAutoDNS.UnicastInDataMeasurement(), true);
        this.registerCounter("unicast_out_ops", NodelAutoDNS.UnicastOutOpsMeasurement(), true);
        this.registerCounter("unicast_out_data",NodelAutoDNS.UnicastOutDataMeasurement(), true);        
        
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
    
    @Service(name = "gc", title = "Garbage collect", desc = "Perform a 'garbage collect' operation; not necessarily stable.")
    public void gc() {
        System.gc();
    } // (method)
    
    private Set<String> counterNames = new HashSet<String>();
    
    /**
     * Registers a new counter to be tracked.
     */
    public void registerCounter(String name, MeasurementProvider provider, boolean isRate) {
        if (this.counterNames.contains(name))
            throw new IllegalStateException(name + " is already present");
        
        this.counterNames.add(name);
        
        MeasurementHistory counter = new MeasurementHistory(name, provider, HISTORY_SIZE, isRate);
        
        _measurements.add(counter);
    } // (method)
    
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
    } // (method)
    
    /**
     * (singleton, thread-safe, lazy init)
     */
    private static class Instance {
        
        private static final Framework SHARED = new Framework();
        
    } // (class)
    
    /**
     * Returns an instance which can be shared.
     */
    public static Framework shared() {
        return Instance.SHARED;
    } // (method)

} // (class)
