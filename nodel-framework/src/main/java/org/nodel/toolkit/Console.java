package org.nodel.toolkit;

public class Console {
    
    public interface Interface {
        
        public void info(Object obj);
        
        public void log(Object obj);
        
        public void error(Object obj);
        
        public void warn(Object obj);
    }
    
    /**
     * Null console, never throws exceptions.
     */
    private static class Null implements Interface {
        
        @Override
        public void info(Object obj) {
        }

        @Override
        public void log(Object obj) {
        }

        @Override
        public void error(Object obj) {
        }

        @Override
        public void warn(Object obj) {
        }
        
    }
    
    private static class LazyHolder {
        private static final Interface INSTANCE = new Null();
    }

    public static Interface NullConsole() {
        return LazyHolder.INSTANCE;
    }    
    
}