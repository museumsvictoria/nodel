package org.nodel.logging;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Allows for data counting of underlying stream.
 */
public class CountableInputStream extends InputStream {
    
    private AtomicLong total;

    private AtomicLong ops;
    
    private InputStream base;
    
    public CountableInputStream(InputStream base, AtomicLong ops, AtomicLong total) {
        this.base = base;
        this.ops = ops;
        this.total = total;
    } // (init)
    
    /**
     * The total amount of data written.
     */    
    public long getTotal() {
        return this.total.get();
    }
    
    /**
     * Records the number of write operations
     */
    public long getOps() {
        return this.ops.get();
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
        this.ops.incrementAndGet();
        this.total.incrementAndGet();        
        
        return base.read();
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public int read(byte b[]) throws IOException {
        this.ops.incrementAndGet();
        this.total.addAndGet(b.length);        
        
        return base.read(b);
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public int read(byte b[], int off, int len) throws IOException {
        this.ops.incrementAndGet();
        this.total.addAndGet(b.length);

        return base.read(b, off, len);
    }

} // (class)
