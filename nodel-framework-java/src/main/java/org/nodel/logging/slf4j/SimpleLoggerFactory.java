package org.nodel.logging.slf4j;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;

public class SimpleLoggerFactory implements ILoggerFactory {
    
    private static Map<String, SimpleLogger> s_loggers = new HashMap<String, SimpleLogger>();

    @Override
    public Logger getLogger(String name) {
        synchronized(s_loggers) {
            
            SimpleLogger existing = s_loggers.get(name);
            
            if (existing == null) {
                existing = new SimpleLogger(name);
                s_loggers.put(name, existing);
            }
            
            return existing;
        }
    }
    
    /**
     * Triggers a maintenance request on all the loggers
     */
    public static void requestMaintenance() {
        synchronized (s_loggers) {
            for (SimpleLogger logger : s_loggers.values()) {
                logger.requestMaintenance();
            }
        }
    }

}
