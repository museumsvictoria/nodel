package org.nodel.snmp;

import java.io.IOException;

import org.nodel.io.UnexpectedIOException;
import org.snmp4j.Snmp;

/**
 * Convenience class for nodes to share and save resources.
 */
public class NodelSnmp {
    
    /**
     * (exception-less convenience)
     */
    private static NodelUdpTransportMapping tryCreate() {
        try {
            NodelUdpTransportMapping result = new NodelUdpTransportMapping();
            result.listen();
            
            return result;
            
        } catch (IOException exc) {
            throw new UnexpectedIOException("Failed to create UDP transport for SNMP", exc);
        }
    }
    
    /**
     * (singleton, thread-safe, non-blocking)
     */
    private static class Instances {
        
        private static final Snmp INSTANCE = new Snmp(tryCreate());
        
    }
    
    /**
     * Returns a shared SNMP instance with UDP transport.
     */
    public static Snmp shared() {
        return Instances.INSTANCE;
    }
    
}
