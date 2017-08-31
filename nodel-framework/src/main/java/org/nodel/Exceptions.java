package org.nodel;

import org.nodel.reflection.Serialisation;
import org.nodel.reflection.Value;

/*
 * Convenience functions for dealing with Exceptions particularly in logs, etc.
 */
public class Exceptions {
    
    /**
     * Forms part of an exception graph.
     */
    public static class ExceptionSummary {
        
        @Value(name = "name", order = 1)
        public String name;
        
        @Value(name = "msg", order = 2)
        public String msg;
        
        @Value(name = "cause", order = 3)
        public ExceptionSummary cause;

    }
    
    /**
     * Produces an exception graph that can be used to format a stack-trace. 
     */
    public static ExceptionSummary getExceptionGraph(Throwable th) {
        if (th == null)
            throw new IllegalArgumentException("An exception must be provided.");
        
        ExceptionSummary summary = new ExceptionSummary();
        
        Throwable currentThrowable = th;
        ExceptionSummary currentSummary = summary;
        
        // (avoid stack overflow)
        int level = 0;
        
        while(currentThrowable != null) {
            currentSummary.name = currentThrowable.getClass().getName();
            currentSummary.msg = currentThrowable.getMessage();
            
            if (currentThrowable.getCause() == null || level > 128)
                break;
            
            currentThrowable = currentThrowable.getCause();
            currentSummary.cause = new ExceptionSummary();
            
            currentSummary = currentSummary.cause;
        } // (while)
        
        return summary;
    }
    
    /**
     * A string representation of 'getExceptionGraph()'
     */
    public static String formatExceptionGraph(Throwable th) {
        ExceptionSummary result = getExceptionGraph(th);
        return Serialisation.serialise(result);
    }

}
