package org.nodel.threading;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.util.Date;
import java.util.Timer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.nodel.Strings;
import org.nodel.diagnostics.AtomicLongMeasurementProvider;
import org.nodel.diagnostics.Diagnostics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A specialised timer class that efficient use of the package's thread-pooling, minimising the number of
 * threads in use.
 */
public class Timers {
    
    /**
     * The number of CPUs
     */
    private static int staticCores = Runtime.getRuntime().availableProcessors();
    
    /**
     * Will have one timer class per CPU.
     */
    private static Timer[] staticTimerThreads;
    
    /**
     * A counter use to distribute timer use.
     */
    private static AtomicInteger staticCounter = new AtomicInteger(0);
    
    static {
        staticTimerThreads = new Timer[staticCores];

        for (int a = 0; a < staticCores; a++)
            staticTimerThreads[a] = new Timer("nodel_timer_" + a, true);
    } // (static)    
    
    /**
     * (logging)
     */
    private Logger logger = LoggerFactory.getLogger(Timers.class);
    
    /**
     * The name of this timer thread category.
     */
    private String name;
    
    /**
     * Number of operations completed (stats)
     */
    private AtomicLong operations = new AtomicLong();
    
    /**
     * Constructs a new timer thread.
     */
    public Timers(String name) {
        if (Strings.isNullOrEmpty(name))
            throw new IllegalArgumentException("The timer name cannot be empty; prefix with '_' to avoid registering with diagnostics framework.");

        this.name = name;
        
        // to avoid a class loading stack overflow, ignore call from Framework class.
        if (name != null && !name.startsWith("_") && Diagnostics.shared() != null)
            Diagnostics.shared().registerCounter(name + " timer.Ops", new AtomicLongMeasurementProvider(this.operations), true);
    } // (init)
    
    /**
     * Returns the name of the timer.
     */
    public String getName() {
        return this.name;
    }
    
    /**
     * Uses a shared timer for rapidly completing timer tasks.
     * One timer (and respective timer thread) is allocated per CPU.
     */
    private static java.util.Timer sharedTimer() {
        return staticTimerThreads[Atomic.atomicIncrementAndWrap(staticCounter, staticCores)];
    } // (method)
    
    /**
     * Use for a one-off timer.
     */
    public TimerTask schedule(TimerTask task, long delay) {
        sharedTimer().schedule(createWrapper(task, null), delay);
        
        return task;
    } // (method)
    
    /**
     * A one-off timer whose task could be blocking so thread-pool can be used.
     */
    public TimerTask schedule(ThreadPool threadPool, TimerTask task, long delay) {
        sharedTimer().schedule(createWrapper(task, threadPool), delay);
        
        return task;        
    } // (method)

    /**
     * Use for a one-off timer.
     */
    public TimerTask schedule(TimerTask task, Date time) {
        sharedTimer().schedule(createWrapper(task, null), time);
        
        return task;
    } // (method)
    
    /**
     * Use for a repeating timer.
     */
    public TimerTask schedule(TimerTask task, long delay, long period) {
        sharedTimer().schedule(createWrapper(task, null), delay, period);
        
        return task;
    }
    
    /**
     * Use for a repeating timer.
     */
    public TimerTask schedule(ThreadPool threadPool, TimerTask task, long delay, long period) {
        sharedTimer().schedule(createWrapper(task, threadPool), delay, period);
        
        return task;
    }    
    
    /**
     * Use for a repeating timer.
     */
    public TimerTask schedule(TimerTask task, Date firstTime, long period) {
        sharedTimer().schedule(createWrapper(task, null), firstTime, period);
        
        return task;
    } // (method)

    /**
     * Use for a repeating timer.
     */    
    public TimerTask scheduleAtFixedRate(TimerTask task, long delay, long period) {
        sharedTimer().scheduleAtFixedRate(createWrapper(task, null), delay, period);
        
        return task;
    } // (method)
    
    /**
     * Use for a repeating timer.
     */    
    public TimerTask scheduleAtFixedRate(TimerTask task, Date firstTime, long period) {
        sharedTimer().scheduleAtFixedRate(createWrapper(task, null), firstTime, period);
        
        return task;
    } // (method)
    
    /**
     * A convenience method to ensure unhandled exceptions are caught.
     */
    private java.util.TimerTask createWrapper(final TimerTask task, final ThreadPool threadPool) {
        // set up the task entry-point at schedule time
        final Runnable runnable = new Runnable() {
            
            @Override
            public void run() {
                task.run();
            }
            
        };
        
        // create a wrapper that offloads onto the thread-pool at 
        // time of execution
        java.util.TimerTask nativeTimerTask = new java.util.TimerTask() {
            
            @Override
            public void run() {
                try {
                    // for ops counting
                    operations.incrementAndGet();
                    
                    if (threadPool == null)
                        runnable.run();
                    else
                        threadPool.execute(runnable);
                    
                } catch (Exception exc) {
                    // make sure unhandled exceptions don't pull down the whole thread
                    
                    logger.warn("An unhandled exception occurred within this timer's thread.", exc);
                }
                
            } // (method)
            
        };
        
        task.nativeTimerTask = nativeTimerTask;
        
        return nativeTimerTask;
    } // (method)
    
} // (class)
