package org.nodel.logging;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Binds to an atomic counter for measurement providing purposes.
 */
public class AtomicIntegerMeasurementProvider implements MeasurementProvider {

    private AtomicInteger atomicInteger;
    
    public AtomicIntegerMeasurementProvider(AtomicInteger atomicInteger) {
        this.atomicInteger = atomicInteger;
    } // (init)

    @Override
    public long getMeasurement() {
        return this.atomicInteger.get();
    }

} // (class)
