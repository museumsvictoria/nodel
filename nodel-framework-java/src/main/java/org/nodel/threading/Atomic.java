package org.nodel.threading;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Some useful convenience methods for extra atomic operations. 
 */
public class Atomic {
    
    /**
     * Atomic way to set if less than.
     */    
    public static int atomicIncrementAndWrap(AtomicInteger currentValue, int modulus) {
        for (;;) {
            int current = currentValue.get();
            int next = current + 1;

            if (next == modulus)
                next = 0;

            if (currentValue.compareAndSet(current, next))
                return next;
        } // (for)
        
    } // (method)  
    
    /**
     * Atomic way to set if less than.
     */
    public static int atomicLessThanAndSet(int value, AtomicInteger currentValue) {
        for (;;) {
            int current = currentValue.get();
            int next = (value < current ? value : current);
            if (currentValue.compareAndSet(current, next))
                return next;
        } // (for)

    } // (method)
    
    /**
     * Atomic way to set if less than.
     */    
    public static int atomicMoreThanAndSet(int value, AtomicInteger currentValue) {
        for (;;) {
            int current = currentValue.get();
            int next = (value > current ? value : current);
            if (currentValue.compareAndSet(current, next))
                return next;
        } // (for)
        
    } // (method)    

} // (class)
