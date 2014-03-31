package org.nodel.logging;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Allows for data counting of underlying stream.
 */
public class CountableOutputStream extends OutputStream {

    private AtomicLong total;

    private AtomicLong ops;
    
    private OutputStream base;
    
    public CountableOutputStream(OutputStream base, AtomicLong ops, AtomicLong total) {
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
    public OutputStream getBase() {
        return this.base;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void write(int b) throws IOException {
        this.ops.incrementAndGet();
        this.total.incrementAndGet();
        
        this.base.write(b);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void write(byte b[]) throws IOException {
        this.ops.incrementAndGet();
        this.total.addAndGet(b.length);
        
        this.base.write(b);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void write(byte b[], int off, int len) throws IOException {
        this.ops.incrementAndGet();
        this.total.addAndGet(len);

        this.base.write(b, off, len);
    }

} // (class)
