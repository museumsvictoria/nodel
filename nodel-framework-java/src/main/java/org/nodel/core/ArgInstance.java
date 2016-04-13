package org.nodel.core;

import org.joda.time.DateTime;
import org.nodel.reflection.Value;

/**
 * Value and timestamp composite value.
 */
public class ArgInstance {
    
    public static ArgInstance NULL = new ArgInstance();

    @Value(name = "timestamp")
    public DateTime timestamp;

    @Value(name = "arg")
    public Object arg;
    
    @Value(name = "seq")
    public long seqNum;

}