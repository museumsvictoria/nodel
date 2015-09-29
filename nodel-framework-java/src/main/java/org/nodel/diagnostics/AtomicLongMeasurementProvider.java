package org.nodel.diagnostics;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.util.concurrent.atomic.AtomicLong;

/**
 * Binds to an atomic counter for measurement providing purposes.
 */
public class AtomicLongMeasurementProvider implements MeasurementProvider {

    protected AtomicLong atomicLong;
    
    public AtomicLongMeasurementProvider(AtomicLong atomicLong) {
        this.atomicLong = atomicLong;
    }

    @Override
    public long getMeasurement() {
        return this.atomicLong.get();
    }

} // (class)
