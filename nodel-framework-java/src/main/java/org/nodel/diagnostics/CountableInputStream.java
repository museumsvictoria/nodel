package org.nodel.diagnostics;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.io.IOException;
import java.io.InputStream;

import org.nodel.io.Stream;

/**
 * Allows for data counting of underlying stream.
 */
public class CountableInputStream extends InputStream {
    
    private SharableMeasurementProvider total;

    private SharableMeasurementProvider ops;
    
    private InputStream base;
    
    public CountableInputStream(InputStream base, SharableMeasurementProvider ops, SharableMeasurementProvider total) {
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
    public InputStream getBase() {
        return this.base;
    }    
    
    /**
     * {@inheritDoc}
     */
    @Override
    public int read() throws IOException {
        this.ops.incr();
        
        int b = base.read(); 
        
        if (b >= 0)
            this.total.add(8);

        return b;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public int read(byte b[]) throws IOException {
        this.ops.incr();
                
        int count = base.read(b);
        
        if (count > 0)
            this.total.add(count);
        
        return count;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public int read(byte b[], int off, int len) throws IOException {
        this.ops.incr();

        int count = base.read(b, off, len);
        
        if (count > 0)
            this.total.add(count * 8);
        
        return count;
    }
    
    /**
     * {@inheritDoc}
     */    
    @Override
    public void close() throws IOException {
        Stream.safeClose(base);
    }

} // (class)
