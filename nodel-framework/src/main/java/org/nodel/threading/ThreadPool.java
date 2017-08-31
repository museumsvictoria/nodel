package org.nodel.threading;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import org.nodel.Threads;
import org.nodel.diagnostics.AtomicIntegerMeasurementProvider;
import org.nodel.diagnostics.AtomicLongMeasurementProvider;
import org.nodel.diagnostics.Diagnostics;
import org.nodel.diagnostics.MeasurementProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Contains thread-pool related utilities for the Nodel environment.
 */
public class ThreadPool {
    
    /**
     * The default.
     */
    private final static int DEFAULT_MAXTHREADS = 128;
    
    /**
     * (class-level lock)
     */
    private final static Object s_lock = new Object();
    
    /**
     * (See related methods)
     */
    public static int staticMaxThreads = DEFAULT_MAXTHREADS;
    
    /**
     * (logging)
     */
    private Logger logger = LoggerFactory.getLogger(this.getClass().getName());
    
    /**
     * The name of this thread-pool.
     */
    private String name;
    
    /**
     * The thread-count cap for this pool.
     */
    private int maxThreads;
    
    /**
     * The idle before before threads retire themselves.
     */
    private int timeout = -1;
    
    /**
     * The number of available threads.
     */
    private AtomicInteger availableThreads = new AtomicInteger();
    
    /**
     * The total number of threads.
     */
    private AtomicInteger totalThreads = new AtomicInteger();    
    
    /**
     * The number of threads actually in use.
     */
    private AtomicInteger threadsInUse = new AtomicInteger();
    
    /**
     * (read-only version)
     */
    private MeasurementProvider readOnlyInUse = new AtomicIntegerMeasurementProvider(this.threadsInUse);    
    
    /**
     * Low water mark.
     */
    private AtomicInteger threadsInUse_low = new AtomicInteger(Integer.MAX_VALUE);
    
    /**
     * High water mark.
     */
    private AtomicInteger threadsInUse_high = new AtomicInteger(Integer.MIN_VALUE);
    
    /**
     * Number of operations completed (stats)
     */
    private AtomicLong operations = new AtomicLong();
    
    /**
     * Read-only version of operations completed (stats)
     */
    private MeasurementProvider readOnlyOperations = new AtomicLongMeasurementProvider(this.operations);
    
    /**
     * Holds all the work items.
     */
    private Queue<QueueItem> workQueue = new LinkedList<QueueItem>();
    
    /**s
     * For growth operations.
     */
    private ReentrantLock growLock = new ReentrantLock();
    
    /**
     * Used to signal when threads have been created.
     */
    private Object creationSignal = new Object();
    
    /**
     * Constructs an independent thread-pool.
     */
    public ThreadPool(String name, int size, int timeout) {
        init(name, size, timeout);
    } // (init)
    
    /**
     * Constructs an independent thread-pool.
     */
    public ThreadPool(String name, int size) {
        init(name, size, -1);
    } // (init)    
    
    private void init(String name, int size, int timeout) {
        this.name = name;
        
        this.maxThreads = size;
        
        this.timeout = timeout;
        
        Diagnostics.shared().registerCounter(this.name + " thread-pool.Ops", this.readOnlyOperations, true);
        Diagnostics.shared().registerCounter(this.name + " thread-pool.Active threads", this.readOnlyInUse, false);
    }
    
    /**
     * The maximum number of threads this pool will use.
     */
    public int getMaxThreads() {
       return this.maxThreads;
    }
    
    /**
     * Stored in the queue.
     */
    private class QueueItem {
        
        public Runnable runnable;
        
        public long timestamp;
        
        public QueueItem(Runnable runnable) {
            this.runnable = runnable;
            this.timestamp = System.nanoTime();
        }
        
    } // (class)
    
    /**
     * Executes a task within this thread-pool, growing the thread-pool
     * conservatively.
     */
    public void execute(Runnable runnable) {
        if (runnable == null)
            throw new NullPointerException();
        
        QueueItem item = new QueueItem(runnable);

        synchronized (this.workQueue) {
            this.workQueue.add(item);

            this.workQueue.notify();
        }

        if (this.availableThreads.get() == 0)
            tryGrow();
    } // (method)
    
    /**
     * Whether or not the info log has been logged to avoid excessive logging.
     */
    private boolean logged = false;

    /**
     * Grows the thread-pool synchronously
     */
    private void tryGrow() {
        if (this.growLock.tryLock()) {
            try {
                // make sure the cap hasn't been  exceeded
                if (this.totalThreads.get() >= this.maxThreads) {
                    if (!this.logged) {
                        this.logged = true;
                        this.logger.info("Reached thread cap of {} for thread pool '{}'.", this.maxThreads, this.name);
                    }
                    
                    // let the existing threads deal with it
                    return;
                }
                
                // create the new thread
                Thread thread = new Thread(new Runnable() {
                    
                    @Override
                    public void run() {
                        threadMain();
                    }
                    
                });
                
                int total = this.totalThreads.incrementAndGet();
                
                this.threadsInUse.incrementAndGet();
                
                thread.setName("pool_" + this.name + "_" + (total - 1));
                thread.setDaemon(true);
                
                synchronized(this.creationSignal) {
                    // kick off the new thread
                    thread.start();
                    
                    // wait until it has actually started
                    Threads.wait(this.creationSignal);
                }
                
            } finally {
                this.growLock.unlock();
            }
        }
    } // (method)

    /**
     * (entry-point for threads) 
     */
    private void threadMain() {
        // signal the creation thread so it can continue
        synchronized(this.creationSignal) {
            this.creationSignal.notifyAll();
        }
        
        // record its availability
        int available = this.availableThreads.incrementAndGet();
        
        // record that it's not in use
        this.threadsInUse.decrementAndGet();
        
        for (;;) {
            // holds the runnable
            QueueItem item;
            
            synchronized (this.workQueue) {
                while (this.workQueue.size() == 0) {
                    if (this.timeout < 0) {
                        Threads.waitOnSync(this.workQueue);
                    } else {
                        Threads.waitOnSync(this.workQueue, this.timeout);

                        if (this.workQueue.size() == 0) {
                            // thread has been idle a while bring it down
                            this.availableThreads.decrementAndGet();

                            this.totalThreads.decrementAndGet();

                            this.logger.debug("This idle thread has been retired from its pool.");

                            this.logged = false;

                            return;
                        }
                    }
                } // (while)
                
                // grab the item available
                item = this.workQueue.remove();
            }
            
            // grow the queue if anything has been sitting in it for more than 1 second
            long timeInQueue = System.nanoTime() - item.timestamp;
            
            // or if there are no threads available
            available = this.availableThreads.decrementAndGet();
            
            if (available <= 0 || timeInQueue > 1000 * 1000000)
                // need to be growing
                tryGrow();
            
            // record that it's in use
            int threadsInUse = this.threadsInUse.incrementAndGet();
            
            Atomic.atomicMoreThanAndSet(threadsInUse, this.threadsInUse_high);
            
            // count the operation *before* actual execution
            this.operations.incrementAndGet();
            
            try {
                item.runnable.run();
                
            } catch (Exception exc) {
                this.logger.warn("An unhandled exception occurred within a thread-pool", exc);
            }
            
            // record it's not in use
            this.threadsInUse.decrementAndGet();
            
            Atomic.atomicLessThanAndSet(threadsInUse, this.threadsInUse_low);
            
            // record its availability
            this.availableThreads.incrementAndGet();
            
            // continue...
            
        } // (for)
        
    } // (method)
    
    /**
     * Holds the back-ground thread-pool.
     */
    private static ThreadPool s_background;

    /**
     * Background thread-pool for low-priority tasks.
     * (singleton) 
     */
    public static ThreadPool background() {
        if (s_background == null) {
            synchronized(s_lock) {
                if (s_background == null)
                    s_background = new ThreadPool("Background", staticMaxThreads);
            }
        }
        return s_background;
    }

}
