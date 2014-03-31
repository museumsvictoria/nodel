package org.nodel;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.util.ArrayList;
import java.util.List;

/**
 * (Based on the Observable pattern.)
 */
public class Handlers {
    
    /**
     * For handlers that don't take any arguments.
     */
    public static class H0 {

        /**
         * Instance signal / lock.
         */
        private Object signal = new Object();

        /**
         * The list of handlers.
         */
        private List<Handler.H0> handlers = new ArrayList<Handler.H0>();

        /**
         * Adds a handler. The handler must not block or throw exceptions.
         */
        public void addHandler(Handler.H0 handler) {
            if (handler == null)
                throw new IllegalArgumentException("Handler cannot be null.");
            
            synchronized (this.signal) {
                this.handlers.add(handler);
            }
        }
        
        /**
         * Updates all handlers.
         */
        public void updateAll() {
            synchronized (this.signal) {
                for (Handler.H0 handler : handlers) {
                    handler.handle();
                } // (for)
            }
        } // (method)
        
        /**
         * Removes a handler.
         */
        public void removeHandler(Handler.H0 handler) {
            synchronized (this.signal) {
                this.handlers.remove(handler);
            }        
        } // (class)
        
    } // (class)
    
    /**
     * For handlers that take one argument.
     */
    public static class H1<T> {

        /**
         * Instance signal / lock.
         */
        private Object signal = new Object();

        /**
         * The list of handlers.
         */
        private List<Handler.H1<T>> handlers = new ArrayList<Handler.H1<T>>();

        /**
         * Adds a handler. The handler must not block or throw exceptions.
         */
        public boolean addHandler(Handler.H1<T> handler) {
            if (handler == null)
                throw new IllegalArgumentException("Handler cannot be null.");
            
            synchronized (this.signal) {
                return this.handlers.add(handler);
            }
        } // (method)
        
        /**
         * Updates all handlers.
         */
        public void updateAll(T value) {
            synchronized (this.signal) {
                for (Handler.H1<T> handler : handlers) {
                    handler.handle(value);
                } // (for)
            }
        } // (method)
        
        /**
         * Removes a handler.
         */
        public boolean removeHandler(Handler.H1<T> handler) {
            synchronized (this.signal) {
                return this.handlers.remove(handler);
            }        
        } // (method)
        
    } // (class)
    
    /**
     * For handlers that take two arguments.
     */
    public static class H2<T0, T1> {

        /**
         * Instance signal / lock.
         */
        private Object signal = new Object();

        /**
         * The list of handlers.
         */
        private List<Handler.H2<T0, T1>> handlers = new ArrayList<Handler.H2<T0, T1>>();

        /**
         * Adds a handler. The handler must not block or throw exceptions.
         */
        public boolean addHandler(Handler.H2<T0, T1> handler) {
            if (handler == null)
                throw new IllegalArgumentException("Handler cannot be null.");

            synchronized (this.signal) {
                return this.handlers.add(handler);
            }
        } // (method)
        
        /**
         * Updates all handlers.
         */
        public void updateAll(T0 value0, T1 value1) {
            synchronized (this.signal) {
                for (Handler.H2<T0, T1> handler : handlers) {
                    handler.handle(value0, value1);
                } // (for)
            }
        } // (method)
        
        /**
         * Removes a handler.
         */
        public boolean removeHandler(Handler.H2<T0, T1> handler) {
            synchronized (this.signal) {
                return this.handlers.remove(handler);
            }        
        } // (method)
        
    } // (class)
    
    /**
     * For handlers that take three arguments.
     */
    public static class H3<T0, T1, T2> {

        /**
         * Instance signal / lock.
         */
        private Object signal = new Object();

        /**
         * The list of handlers.
         */
        private List<Handler.H3<T0, T1, T2>> handlers = new ArrayList<Handler.H3<T0, T1, T2>>();

        /**
         * Adds a handler. The handler must not block or throw exceptions.
         */
        public boolean addHandler(Handler.H3<T0, T1, T2> handler) {
            if (handler == null)
                throw new IllegalArgumentException("Handler cannot be null.");

            synchronized (this.signal) {
                return this.handlers.add(handler);
            }
        } // (method)
        
        /**
         * Updates all handlers.
         */
        public void updateAll(T0 value0, T1 value1, T2 value2) {
            synchronized (this.signal) {
                for (Handler.H3<T0, T1, T2> handler : handlers) {
                    handler.handle(value0, value1, value2);
                } // (for)
            }
        } // (method)
        
        /**
         * Removes a handler.
         */
        public boolean removeHandler(Handler.H3<T0, T1, T2> handler) {
            synchronized (this.signal) {
                return this.handlers.remove(handler);
            }        
        } // (method)
        
    } // (class)    
    
} // (class)
