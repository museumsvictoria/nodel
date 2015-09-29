package org.nodel.diagnostics;

/**
 * Measurement provider that can be altered by a 3rd-party.
 */
public interface SharableMeasurementProvider extends MeasurementProvider {
    
    public void set(long value);
    
    public void add(long value);
    
    public void incr();
    
    public void decr();
    
    /**
     * For convenience. 
     */
    public class Null implements SharableMeasurementProvider {
        
        /**
         * A sharable instance of a 'null' provider.
         */
        public final static SharableMeasurementProvider INSTANCE = new Null();
        
        @Override
        public long getMeasurement() {
            return 0;
        }

        @Override
        public void set(long value) {
        }

        @Override
        public void add(long value) {
        }

        @Override
        public void incr() {
        }
        
        @Override
        public void decr() {
        }
        
    }

}
