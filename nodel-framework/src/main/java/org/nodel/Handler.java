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
     * (function with no args)
     */
    public interface F0<R> {
        public R handle();
    }

    /**
     * For 1 argument/parameter handlers.
     */
    public interface H1<T> {
        public void handle(T value);
    }
    
    /**
     * (function with 1 arg)
     */
    public interface F1<R, T> {
        public R handle(T value);
    }

    /**
     * For 2 argument/parameter handlers.
     */
    public interface H2<T0, T1> {
        public void handle(T0 value0, T1 value1);
    }
    
    /**
     * (function with 2 arg)
     */
    public interface F2<R, T0, T1> {
        public R handle(T0 value0, T1 value1);
    }

    /**
     * For 3 argument/parameter handlers.
     */
    public interface H3<T0, T1, T2> {
        public void handle(T0 value0, T1 value1, T2 value2);
    }
    
    /**
     * (function with 3 arg)
     */
    public interface F3<R, T0, T1, T2> {
        public R handle(T0 value0, T1 value1, T2 value2);
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
     * (thread-safe convenience)
     */
    public static <R> R handle(F0<R> handler) {
        return handler != null ? handler.handle() : null;
    }        

    /**
     * Thread-safe convenience method.
     */
    public static <T0> void handle(H1<T0> handler, T0 value0) {
        if (handler != null)
            handler.handle(value0);
    }
    
    /**
     * (thread-safe convenience)
     */
    public static <R, T0> R handle(F1<R, T0> handler, T0 value0) {
        return handler != null ? handler.handle(value0) : null;
    }

    /**
     * Thread-safe convenience method.
     */
    public static <T0, T1> void handle(H2<T0, T1> handler, T0 value0, T1 value1) {
        if (handler != null)
            handler.handle(value0, value1);
    }
    
    /**
     * (thread-safe convenience)
     */
    public static <R, T0, T1> R handle(F2<R, T0, T1> handler, T0 value0, T1 value1) {
        return handler != null ? handler.handle(value0, value1) : null;
    }

    /**
     * Thread-safe convenience method.
     */
    public static <T0, T1, T2> void handle(H3<T0, T1, T2> handler, T0 value0, T1 value1, T2 value2) {
        if (handler != null)
            handler.handle(value0, value1, value2);
    }

    /**
     * Thread-safe convenience method.
     */
    public static <T0, T1, T2, T3> void handle(H4<T0, T1, T2, T3> handler, T0 value0, T1 value1, T2 value2, T3 value3) {
        if (handler != null)
            handler.handle(value0, value1, value2, value3);
    }
    
    /**
     * Thread-safe convenience method.
     */
    public static <T0, T1, T2, T3, T4> void handle(H5<T0, T1, T2, T3, T4> handler, T0 value0, T1 value1, T2 value2, T3 value3, T4 value4) {
        if (handler != null)
            handler.handle(value0, value1, value2, value3, value4);
    }
    
    /**
     * Thread-safe convenience method.
     */
    public static void tryHandle(H0 handler) {
        try {
            if (handler != null)
                handler.handle();
        } catch (Exception exc) {
            // ignore
        }
    }

    /**
     * (thread-safe convenience)
     */
    public static <R> R tryHandle(F0<R> handler) {
        try {
            return handler != null ? handler.handle() : null;
        } catch (Exception exc) {
            return null;
        }
    }

    /**
     * Thread-safe convenience method.
     */
    public static <T0> void tryHandle(H1<T0> handler, T0 value0) {
        try {
            if (handler != null)
                handler.handle(value0);
        } catch (Exception exc) {
            // ignore
        }
    }
    
    /**
     * (thread-safe convenience)
     */
    public static <R, T0> R tryHandle(F1<R, T0> handler, T0 value0) {
        try {
            return handler != null ? handler.handle(value0) : null;
        } catch (Exception exc) {
            return null;
        }
    }    

    /**
     * Thread-safe convenience method.
     */
    public static <T0, T1> void tryHandle(H2<T0, T1> handler, T0 value0, T1 value1) {
        try {
            if (handler != null)
                handler.handle(value0, value1);
        } catch (Exception exc) {
            // ignore
        }
    }
    
    /**
     * (thread-safe convenience)
     */
    public static <R, T0, T1> R tryHandle(F2<R, T0, T1> handler, T0 value0, T1 value1) {
        try {
            return handler != null ? handler.handle(value0, value1) : null;
        } catch (Exception exc) {
            return null;
        }
    }      

    /**
     * Thread-safe convenience method.
     */
    public static <T0, T1, T2> void tryHandle(H3<T0, T1, T2> handler, T0 value0, T1 value1, T2 value2) {
        try {
            if (handler != null)
                handler.handle(value0, value1, value2);
        } catch (Exception exc) {
            // ignore
        }
    }

    /**
     * Thread-safe convenience method.
     */
    public static <T0, T1, T2, T3> void tryHandle(H4<T0, T1, T2, T3> handler, T0 value0, T1 value1, T2 value2, T3 value3) {
        try {
            if (handler != null)
                handler.handle(value0, value1, value2, value3);
        } catch (Exception exc) {
            // ignore
        }
    }

    /**
     * Thread-safe convenience method.
     */
    public static <T0, T1, T2, T3, T4> void tryHandle(H5<T0, T1, T2, T3, T4> handler, T0 value0, T1 value1, T2 value2, T3 value3, T4 value4) {
        try {
            if (handler != null)
                handler.handle(value0, value1, value2, value3, value4);
        } catch (Exception exc) {
            // ignore
        }
    }
    
    /**
     * Thread-safe convenience method.
     */
    public static void tryHandle(H0 handler, H1<Exception> excHandler) {
        try {
            if (handler != null)
                handler.handle();
        } catch (Exception exc) {
            tryHandle(excHandler, exc);
        }
    }

    /**
     * Thread-safe convenience method.
     */
    public static <T0> void tryHandle(H1<T0> handler, T0 value0, H1<Exception> excHandler) {
        try {
            if (handler != null)
                handler.handle(value0);
        } catch (Exception exc) {
            tryHandle(excHandler, exc);
        }
    }

    /**
     * Thread-safe convenience method.
     */
    public static <T0, T1> void tryHandle(H2<T0, T1> handler, T0 value0, T1 value1, H1<Exception> excHandler) {
        try {
            if (handler != null)
                handler.handle(value0, value1);
        } catch (Exception exc) {
            tryHandle(excHandler, exc);
        }
    }

    /**
     * Thread-safe convenience method.
     */
    public static <T0, T1, T2> void tryHandle(H3<T0, T1, T2> handler, T0 value0, T1 value1, T2 value2, H1<Exception> excHandler) {
        try {
            if (handler != null)
                handler.handle(value0, value1, value2);
        } catch (Exception exc) {
            tryHandle(excHandler, exc);
        }
    }

    /**
     * Thread-safe convenience method.
     */
    public static <T0, T1, T2, T3> void tryHandle(H4<T0, T1, T2, T3> handler, T0 value0, T1 value1, T2 value2, T3 value3, H1<Exception> excHandler) {
        try {
            if (handler != null)
                handler.handle(value0, value1, value2, value3);
        } catch (Exception exc) {
            tryHandle(excHandler, exc);
        }
    }

    /**
     * Thread-safe convenience method.
     */
    public static <T0, T1, T2, T3, T4> void tryHandle(H5<T0, T1, T2, T3, T4> handler, T0 value0, T1 value1, T2 value2, T3 value3, T4 value4, H1<Exception> excHandler) {
        try {
            if (handler != null)
                handler.handle(value0, value1, value2, value3, value4);
        } catch (Exception exc) {
            tryHandle(excHandler, exc);
        }
    }

} // (class)