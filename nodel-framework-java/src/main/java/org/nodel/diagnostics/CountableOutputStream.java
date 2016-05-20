package org.nodel.diagnostics;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.io.IOException;
import java.io.OutputStream;

import org.nodel.io.Stream;

/**
 * Allows for data counting of underlying stream.
 */
public class CountableOutputStream extends OutputStream {

    private SharableMeasurementProvider total;

    private SharableMeasurementProvider ops;
    
    private OutputStream base;
    
    public CountableOutputStream(OutputStream base, SharableMeasurementProvider ops, SharableMeasurementProvider total) {
        this.base = base;
        this.ops = ops;
        this.total = total;
    } // (init)
    
    /**
     * The total amount of data written.
     */    
    public long getTotal() {
        return this.total.getMeasurement();
    }
    
    /**
     * Records the number of write operations
     */
    public long getOps() {
        return this.ops.getMeasurement();
    }
    
    /**
     * The underlying stream.
     */
    public OutputStream getBase() {
        return this.base;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void write(int b) throws IOException {
        this.ops.incr();
        this.total.add(8);
        
        this.base.write(b);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void write(byte b[]) throws IOException {
        this.ops.incr();
        this.total.add(b.length * 8);
        
        this.base.write(b);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void write(byte b[], int off, int len) throws IOException {
        this.ops.incr();
        this.total.add(len * 8);

        this.base.write(b, off, len);
    }
    
    @Override
    public void flush() throws IOException {
        this.base.flush();
    }
    
    /**
     * {@inheritDoc}
     */    
    @Override
    public void close() throws IOException {
        Stream.safeClose(base);
    }

} // (class)
