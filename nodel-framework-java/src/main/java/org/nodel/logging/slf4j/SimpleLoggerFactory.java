package org.nodel.logging.slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;

public class SimpleLoggerFactory implements ILoggerFactory {
    
    /**
     * (private to enforce singleton useage)
     */
    private SimpleLoggerFactory() {
    }
    
    private static Map<String, SimpleLogger> _loggers = new HashMap<String, SimpleLogger>();

    @Override
    public Logger getLogger(String name) {
        synchronized(_loggers) {
            
            SimpleLogger existing = _loggers.get(name);
            
            if (existing == null) {
                existing = new SimpleLogger(name);
                _loggers.put(name, existing);
            }
            
            return existing;
        }
    }
    
    /**
     *  Returns a thread-safe snapshot of all the loggers.
     */
    public ArrayList<SimpleLogger> getLoggers() {
        synchronized(_loggers) {
            return new ArrayList<>(_loggers.values());
        }
    }
    
    /**
     * Triggers a maintenance request on all the loggers
     */
    public void requestMaintenance() {
        synchronized (_loggers) {
            for (SimpleLogger logger : _loggers.values()) {
                logger.requestMaintenance();
            }
        }
    }
    
    /**
     * (singleton, thread-safe, non-blocking)
     */
    private static class Instance {
        private static final SimpleLoggerFactory INSTANCE = new SimpleLoggerFactory();
    }

    /**
     * A convenience method for the Nodel framework to return the shared instance (instead of via factory)
     */
    public static SimpleLoggerFactory shared() {
        return Instance.INSTANCE;
    }
}
