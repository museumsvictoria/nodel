package org.nodel;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

/**
 * Avoids having to create several instances of the Java Random class. 
 */
public class Random {
    
    /**
     * (see getter)
     */
    private java.util.Random _random = new java.util.Random();
    
    /**
     * Returns a reference to this shared java.util.Random instance.
     */
    public java.util.Random random() {
        return _random;
    }
    
    /**
     * (for convenience - see #java.util.Random)
     */
    public int nextInt() {
        return _random.nextInt();
    }
    
    /**
     * (for convenience - see #java.util.Random)
     * @param toExcl from 0 (inclusive) to 'toExcl' (exclusive). 
     */
    public int nextInt(int toExcl) {
        return _random.nextInt(toExcl);
    }

    /**
     * Returns random double from 0 (incl.) to 1 (excl.)
     * (for convenience - see #java.util.Random)
     */
    public double nextDouble() {
        return _random.nextDouble();
    }
    
    /**
     * (hidden)
     */
    private Random() {
    }
    
    /**
     * (singleton)
     */
    private static class LazyHolder {
        private static final Random INSTANCE = new Random();
    }

    /**
     * (singleton)
     */
    public static Random shared() {
        return LazyHolder.INSTANCE;
    }
}
