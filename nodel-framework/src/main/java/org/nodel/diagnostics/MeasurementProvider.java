package org.nodel.diagnostics;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

/**
 * Returns a measurement that is used within the diagnostics package. 
 */
public interface MeasurementProvider {
    
    public long getMeasurement();

} // (method)
