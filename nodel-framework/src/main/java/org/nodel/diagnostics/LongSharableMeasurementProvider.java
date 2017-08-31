package org.nodel.diagnostics;

import java.util.concurrent.atomic.AtomicLong;

public class LongSharableMeasurementProvider implements SharableMeasurementProvider {
    
    private AtomicLong _base = new AtomicLong();

    @Override
    public long getMeasurement() {
        return _base.get();
    }

    @Override
    public void set(long value) {
        _base.set(value);
    }

    @Override
    public void add(long delta) {
        _base.addAndGet(delta);
    }

    @Override
    public void incr() {
        _base.incrementAndGet();
    }
    
    @Override
    public void decr() {
        _base.decrementAndGet();
    }

}
