package org.nodel.logging;

import org.nodel.reflection.EnumTitle;
import org.slf4j.spi.LocationAwareLogger;

public enum Level {
    
    TRACE("trace", LocationAwareLogger.TRACE_INT), 
    DEBUG("debug", LocationAwareLogger.DEBUG_INT),
    INFO("info", LocationAwareLogger.INFO_INT), 
    WARN("warn", LocationAwareLogger.WARN_INT),
    ERROR("error", LocationAwareLogger.ERROR_INT);
    
    private int intLevel;
    
    @EnumTitle
    public String name;

    Level(String name, int intLevel) {
        this.name = name;
        this.intLevel = intLevel;
    }
    
    public String getName() {
        return this.name;
    }
    
    public int intLevel() {
        return this.intLevel;
    }
    
    public boolean isMoreSpecificThan(Level other) {
        return this.intLevel > other.intLevel;
    }
    
}