package org.nodel.logging;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.util.concurrent.ArrayBlockingQueue;

import org.nodel.reflection.Value;

public class MeasurementHistory {
    
    /**
     * Scaling to apply to rates to gain extra precision without using non-integers.
     */
    private final static int RATE_SCALE = 10;
    
    /**
     * A short-form name.
     */
    @Value(name = "name", title = "Name", desc = "Short name, alias, code for this measurement.")
    private String name;
    
    /**
     * The actual measurement provider.
     */
    private MeasurementProvider measurementProvider;
    
    /**
     * Represents a rate counter or an instantaneous measurement.
     */
    @Value(name = "isRate", title = "Is rate?", desc = "If this measurement represents a rate (delta) or just an absolute value.")
    private boolean isRate;
    
    @Value(name = "capacity", title = "Capacity", desc = "Measurement data capacity.")
    private int capacity;
    
    /**
     * Holds all the data-points.
     */
    private ArrayBlockingQueue<Number> values;
    
    /**
     * Holds the values.
     */
    @Value(name = "values", title = "Values", desc = "The measurement values.", genericClassA = Number.class)
    public Iterable<Number> values() {
        return this.values;
    }
    
    /**
     * The last measurement added.
     */
    private long lastMeasurement;
    
    /**
     * Constructs a new measurement object.
     */
    public MeasurementHistory(String name, MeasurementProvider measurementProvider, int size, boolean isRate) {
        this.name = name;
        this.measurementProvider = measurementProvider;
        this.capacity = size;
        this.isRate = isRate;
        
        this.values = new ArrayBlockingQueue<Number>(this.capacity);
        
        // load the array
        for (int a = 0; a < this.capacity; a++)
            this.values.add(0);
        
    } // (init)
    
    /**
     * The given name.
     */
    public String getName() {
        return this.name;
    }
    
    /**
     * Returns whether this is a rate-based or instantaneous measurement.
     */
    public boolean isRate() {
        return this.isRate;
    } // (method)
    
    /**
     * The measurement provider.
     */
    public MeasurementProvider getMeasurementProvider() {
        return this.measurementProvider;
    }

    /**
     * The last measurement added.
     */
    public long getLastMeasurement() {
        return this.lastMeasurement;
    }

    /**
     * Makes and records a measurement.
     * 
     * @timeDiff The time difference since previous measurement (nanos)
     */
    public void recordMeasurement(long timeDiff) {
        long value = this.measurementProvider.getMeasurement();

        Number dataPoint;

        if (isRate()) {
            long valueDiff = value - this.lastMeasurement;

            // use 'int' value to save space
            dataPoint = (int) (valueDiff * 1000000000L * RATE_SCALE / timeDiff);
        } else {
            // use 'long'
            dataPoint = value;
        }

        if (this.values.remainingCapacity() <= 0)
            this.values.remove();

        this.values.add(dataPoint);

        this.lastMeasurement = value;
    } // (method)
    
} // (class)