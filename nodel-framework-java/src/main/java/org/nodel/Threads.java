package org.nodel;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Holds thread-related general purpose or utility methods.
 */
public class Threads {
    
    public final static int THREAD_CAP = 64;
    
    /**
     * (signal / lock)
     */
    private final static Object s_lock = new Object();
    
    /**
     * For thread names
     */
    private final static AtomicLong s_threadNumber = new AtomicLong(1);
    
    /**
     * The number of threads in use.
     * (locked around 'staticLock')
     */
    private static int s_inUse = 0;
    
    /**
     * Exceptionless, 'synchronized' way to sleep 
     */
    public static void safeWait(Object signal, long millis) {
        synchronized (signal) {
            try {
                signal.wait(millis);
            } catch (InterruptedException e) {
                // (ignore)
            }
        }
    } // (method)

    /**
     * Exceptionless, 'synchronized' way to sleep. Must already be 'synchronized'.
     */
    public static void safeWaitOnSync(Object signal, long millis) {
        try {
            signal.wait(millis);
        } catch (InterruptedException e) {
            // (ignore)
        }
    } // (method)
    
    /**
     * Non-'synchronized' way to sleep.
     * @throws UnexpectedInterruptedException
     */
    public static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exc) {
            throw new UnexpectedInterruptedException(exc);
        }
    } // (method)    

    /**
     * Sleeps 'synchronized' throwing UnexpectedInterruptionException if unexpectedly
     * interrupted.
     */
    public static void wait(Object signal, long millis) {
        try {
            synchronized (signal) {
                signal.wait(millis);
            }
        } catch (InterruptedException exc) {
            throw new UnexpectedInterruptedException(exc);
        }
    } // (method)
    
    /**
     * Sleeps 'synchronized' throwing UnexpectedInterruptionException if unexpectedly
     * interrupted.
     */
    public static void wait(Object signal) {
        try {
            synchronized (signal) {
                signal.wait();
            }
        } catch (InterruptedException exc) {
            throw new UnexpectedInterruptedException(exc);
        }
    } // (method)    

    /**
     * Sleeps 'synchronized' throwing UnexpectedInterruptionException if unexpectedly
     * interrupted. Must already be 'synchronized'.
     */
    public static void waitOnSync(Object signal, long millis) {
        try {
            signal.wait(millis);
        } catch (InterruptedException exc) {
            throw new UnexpectedInterruptedException(exc);
        }
    } // (method)
    

    /**
     * Sleeps 'synchronized' throwing UnexpectedInterruptionException if unexpectedly
     * interrupted. Must already be 'synchronized'.
     */
    public static void waitOnSync(Object signal) {
        try {
            signal.wait();
        } catch (InterruptedException exc) {
            throw new UnexpectedInterruptedException(exc);
        }
    } // (method)

    public static class AsyncResult<T> {

        /**
         * Used as lock / signal
         */
        private Object signal = new Object();

        /**
         * Required to support 'null' results
         */
        private boolean complete = false;

        /**
         * The result (if no exception)
         */
        private T result;

        /**
         * The exception.
         */
        public Exception exc;

        /**
         * Sets the value and flag. (assumes locked)
         */
        private void setResult(T result, Exception exc) {
            this.result = result;
            this.exc = exc;

            this.complete = true;

            this.signal.notifyAll();
        }

        /**
         * Returns the result.
         */
        public T getResult() {
            return this.result;
        }

        /**
         * Returns the the exception.
         */
        public Exception getException() {
            return this.exc;
        }

        /**
         * Returns whether this action is complete, i.e. a result or exception
         * occurred.
         */
        public boolean isComplete() {
            return this.complete;
        }

        /**
         * Waits for the operation to complete.
         */
        public void waitForComplete() {
            synchronized (this.signal) {
                while (!this.complete) {
                    Threads.waitOnSync(signal);
                }
            }
        } // (method)
        
        /**
         * Waits for a result or throws the exception that occurred.
         */
        public T waitForResultOrThrowException() throws Exception {
            waitForComplete();
            
            if (this.exc != null)
                throw this.exc;
            
            return this.result;
        } // (method)
        
        /**
         * Waits for a result or throws the exception that occurred.
         */
        public T waitForResultOrThrowRuntimeException() throws RuntimeException {
            waitForComplete();
            
            if (this.exc != null)
                throw new RuntimeException(this.exc);
            
            return this.result;
        } // (method)           

    } // (class)
    
    /**
     * An executor that uses fresh threads each time.
     */
    private static Handler.H1<Runnable> s_freshThreadExecutor = new Handler.H1<Runnable>() {

        @Override
        public void handle(Runnable runnable) {
            Thread thread = new Thread(runnable);
            thread.setName(String.format("nodeloneoff_%d", s_threadNumber.getAndIncrement()));
            thread.setDaemon(true);
            thread.start();
        }
        
    };

    /**
     * Allows a call to be dealt with asynchronously with success for failure dealt with on current thread.
     * (uses a new thread each time)
     **/
    public static <U> AsyncResult<U> executeAsync(final Callable<U> callable) {
        return executeAsync(callable, null, null);
    }

    /**
     * Allows a call to be dealt with asynchronously with success for failure dealt with on current thread.
     */
    public static <U> AsyncResult<U> executeAsync(Handler.H1<Runnable> executor, final Callable<U> callable) {
        return executeAsync(executor, callable, null, null);
    }

    /**
     * Allows a call to be dealt with asynchronously, with separate threaded call-backs for success or failures (exception thrown).
     * (uses a new thread each time)
     */
    public static <T> AsyncResult<T> executeAsync(final Callable<T> callable, final Handler.H1<T> success, final Handler.H1<Exception> failure) {
        return executeAsync(s_freshThreadExecutor, callable, success, failure);
    }

    /**
     * Allows a call to be dealt with asynchronously, with separate threaded call-backs for success or failures (exception thrown).
     */
    public static <T> AsyncResult<T> executeAsync(final Handler.H1<Runnable> executor, final Callable<T> callable, final Handler.H1<T> success, final Handler.H1<Exception> failure) {
        final AsyncResult<T> op = new AsyncResult<T>();

        // enforce upper bound on fresh-thread usage
        if (executor == s_freshThreadExecutor) {
            synchronized (s_lock) {
                while (s_inUse > THREAD_CAP)
                    waitOnSync(s_lock);

                s_inUse++;
            }
        }

        synchronized (op.signal) {
            executor.handle(new Runnable() {

                @Override
                public void run() {
                    synchronized (op.signal) {
                        try {
                            T result = callable.call();

                            op.setResult(result, null);

                            if (success != null)
                                success.handle(result);

                        } catch (Exception exc) {
                            op.setResult(null, exc);

                            if (failure != null)
                                failure.handle(exc);
                        }
                    }

                    if (executor == s_freshThreadExecutor) {
                        synchronized (s_lock) {
                            s_inUse--;

                            s_lock.notifyAll();
                        }
                    }
                } // (method)
                
            });
        }

        return op;
    } // (method)

} // (class)
