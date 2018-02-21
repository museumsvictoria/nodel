package org.slf4j.impl;

import java.io.InputStream;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;

import org.nodel.io.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Funnels all legacy Java logging (java.util.logging) through to the SLF logger.
 */
public class JDK14LoggingHandler extends Handler {
    
    FileHandler _fileHandler;
    
    private static Logger s_logger = LoggerFactory.getLogger("jdk14_logging");
    
    public JDK14LoggingHandler() {
        s_logger.info("java.util.logging framework activity present; DEBUG level filtering");
    }

    @Override
    public void publish(LogRecord record) {
        // must be DEBUG or finer
        if (!s_logger.isDebugEnabled())
            return;
        
        Throwable th = record.getThrown();
        
        Level level = record.getLevel();
        
        String comment = String.format("%s [%s] %s", level, record.getLoggerName(), record.getMessage());
        
        int intLevel = level.intValue();
        
        if (intLevel <= Level.FINE.intValue()) {
            if (th == null)
                s_logger.debug(comment);
            else
                s_logger.debug(comment, th);
        } else if (intLevel <= Level.INFO.intValue()) {
            if (th == null)
                s_logger.info(comment);
            else
                s_logger.info(comment, th);            
        } else if (intLevel <= Level.WARNING.intValue()) {
            if (th == null)
                s_logger.warn(comment);
            else
                s_logger.warn(comment, th);            
        } else {
            if (th == null)
                s_logger.error(comment);
            else
                s_logger.error(comment, th);            
        }
    }

    @Override
    public void flush() {
        // nothing to do
    }

    @Override
    public void close() throws SecurityException {
        // nothing to do
    }
    
    /**
     * Relates to java.util.logging framework
     */
    public static void init() {
        InputStream is = null;
        
        try {
            is = JDK14LoggingHandler.class.getResourceAsStream("jdk14_logging.properties");
            LogManager.getLogManager().readConfiguration(is);
            
        } catch (Exception exc) {
            s_logger.warn("Could not initialise java.util.logging framework", exc);
            
        } finally {
            Stream.safeClose(is);
        }
    }       
    
}
