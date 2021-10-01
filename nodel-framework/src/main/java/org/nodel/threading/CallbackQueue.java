package org.nodel.threading;

import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReentrantLock;

import org.nodel.Handler;
import org.nodel.Handler.H0;
import org.nodel.Handler.H1;
import org.nodel.Handler.H2;
import org.nodel.Handler.H3;
import org.nodel.Handler.H4;
import org.nodel.Handler.H5;

/**
 * A callback handler that using a fair ordering policy, with safe exception handling.
 */
public class CallbackQueue {
    
    private ReentrantLock _fairLock = new ReentrantLock(true);
    
    /**
     * Creates a safe callback handler 
     */
    public CallbackQueue() {
    }
    
    /**
     * (convenience method)
     */
    private void doHandle(Runnable runnable) {
        try {
            _fairLock.lock();
            
            runnable.run();
            
        } catch (Exception exc) {
            // (ignore)
            // it up to the callback creator to manage exceptions
            
        } finally {
            _fairLock.unlock();
        }        
    }
    
    /**
     * For synchronous functions.
     */
    public <T> T handle(Callable<T> func) throws Exception {
        try {
            _fairLock.lock();
            
            return func.call();
            
        } finally {
            _fairLock.unlock();
        }
    }
    
    /**
     * For synchronous functions.
     */
    public <R, T> R handle(Handler.F1<R, T> func, T arg) throws Exception {
        try {
            _fairLock.lock();
            
            return func.handle(arg);
            
        } finally {
            _fairLock.unlock();
        }
    }    
        
    /**
     * Creates a callback instance.
     */
    public void handle(final H0 callback, final H1<Exception> errorHandler) {
        doHandle(new Runnable() {

            @Override
            public void run() {
                Handler.tryHandle(callback, errorHandler);
            }

        });
    }
    
    /**
     * Creates a callback instance.
     */
    public <T> void handle(final H1<T> callback, final T value0, final H1<Exception> errorHandler) {
        doHandle(new Runnable() {

            @Override
            public void run() {
                Handler.tryHandle(callback, value0, errorHandler);
            }
            
        });
    }
    
    /**
     * Creates a callback instance.
     */
    public <T0, T1, T2> void handle(final H2<T0, T1> callback, final T0 value0, final T1 value1, final H1<Exception> errorHandler) {
        doHandle(new Runnable() {

            @Override
            public void run() {
                Handler.tryHandle(callback, value0, value1, errorHandler);
            }

        });
    }     
    
    /**
     * Creates a callback instance.
     */
    public <T0, T1, T2> void handle(final H3<T0, T1, T2> callback, final T0 value0, final T1 value1, final T2 value2, final H1<Exception> errorHandler) {
        doHandle(new Runnable() {

            @Override
            public void run() {
                Handler.tryHandle(callback, value0, value1, value2, errorHandler);
            }

        });
    }
    
    /**
     * Creates a callback instance.
     */
    public <T0, T1, T2, T3> void handle(final H4<T0, T1, T2, T3> callback, final T0 value0, final T1 value1, final T2 value2, final T3 value3, final H1<Exception> errorHandler) {
        doHandle(new Runnable() {

            @Override
            public void run() {
                Handler.tryHandle(callback, value0, value1, value2, value3, errorHandler);
            }

        });
    }
    
    /**
     * Creates a callback instance.
     */
    public <T0, T1, T2, T3, T4> void handle(final H5<T0, T1, T2, T3, T4> callback, final T0 value0, final T1 value1, final T2 value2, final T3 value3, final T4 value4, final H1<Exception> errorHandler) {
        doHandle(new Runnable() {

            @Override
            public void run() {
                Handler.tryHandle(callback, value0, value1, value2, value3, value4, errorHandler);
            }

        });
    }
    
}
