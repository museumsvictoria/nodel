package org.nodel;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

/**
 * Represents general purpose generic "handler" that can take optional argument(s).
 */
public class Handler {
    
    /**
     * For handlers not requiring any parameters / arguments.
     */
    public interface H0 {
        public void handle();
    }
    
    /**
     * For 1 argument/parameter handlers.
     */
    public interface H1<T> {
        public void handle(T value);
    }
    
    /**
     * For 2 argument/parameter handlers.
     */
    public interface H2<T0, T1> {
        public void handle(T0 value0, T1 value1);
    }
    
    /**
     * For 3 argument/parameter handlers.
     */
    public interface H3<T0, T1, T2> {
        public void handle(T0 value0, T1 value1, T2 value2);
    }
    
    /**
     * For 4 argument/parameter handlers.
     */
    public interface H4<T0, T1, T2, T3> {
        public void handle(T0 value0, T1 value1, T2 value2, T3 value3);
    }
    
    /**
     * For 5 argument/parameter handlers.
     */
    public interface H5<T0, T1, T2, T3, T4> {
        public void handle(T0 value0, T1 value1, T2 value2, T3 value3, T4 value4);
    }
    
    /**
     * Thread-safe convenience method.
     */
    public static void handle(H0 handler) {
        if (handler != null)
            handler.handle();
    }
    
    /**
     * Thread-safe convenience method.
     */
    public static <T0> void handle(H1<T0> handler, T0 value0) {
        if (handler != null)
            handler.handle(value0);
    }
    
    /**
     * Thread-safe convenience method.
     */
    public static <T0, T1> void handle(H2<T0, T1> handler, T0 value0, T1 value1) {
        if (handler != null)
            handler.handle(value0, value1);
    }
    
    /**
     * Thread-safe convenience method.
     */
    public static <T0, T1, T2> void handle(H3<T0, T1, T2> handler, T0 value0, T1 value1, T2 value2) {
        if (handler != null)
            handler.handle(value0, value1, value2);
    }    

} // (class)