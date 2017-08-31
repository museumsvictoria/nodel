package org.nodel.discovery;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Collection;

import org.nodel.SimpleName;
import org.nodel.Strings;
import org.nodel.core.NodeAddress;
import org.nodel.reflection.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is used to provide the discovery services. By default, the NodelAutoDNS class is used
 * however 3rd party ones can be swapped in instead.
 */
public abstract class AutoDNS implements Closeable {
    
    private static Logger s_logger = LoggerFactory.getLogger(AutoDNS.class);
    
    public final static String IMPL_SYSTEMPROP = "org.nodel.discovery.impl";
    
    public final static String IMPL_DEFAULT_METHOD = "instance";
    
    /**
     * The port to advertise.
     */
    protected int _port = -1;
    
    /**
     * Load an implementation (build-in or otherwise) using an optional system property 'org.nodel.discovery.impl':
     * e.g. org.nodel.discovery.impl = "org.nodel.discovery.AutoDNS"
     *                                 "org.nodel.discovery.AutoDNS;instance"
     *                                 "org.nodel.discovery.JMDNSAutoDNS;create"
     */
    private static AutoDNS loadImpl() {
        AutoDNS result = null;
        
        String impl = System.getProperty(IMPL_SYSTEMPROP);

        if (!Strings.isNullOrEmpty(impl)) {
            // attempt to use alternative implementation
            try {
                String[] parts = impl.split(";");

                // class name 
                String className = parts[0].trim();

                if (Strings.isNullOrEmpty(className))
                    throw new RuntimeException("No class-name was provided");
                
                // access method
                String methodName = (parts.length > 1 ? parts[1].trim() : null);

                if (Strings.isNullOrEmpty(methodName))
                    methodName = IMPL_DEFAULT_METHOD;

                // resolve class
                Class<?> clazz = Class.forName(className);

                // resolve method
                Method method = clazz.getMethod(methodName);
                
                // invoke method
                result = (AutoDNS) method.invoke(null);
                
            } catch (Exception exc) {
                s_logger.warn("Could not load alternative Discovery implementation based on system property 'org.nodel.discovery.impl'; built-in will be used instead.", exc);
            }
        }
        
        if (result == null) {
            // use standard implementation
            result = NodelAutoDNS.create();
        }
        
        return result;
    }
    
    /**
     * The port to use when advertising.
     */
    public void setAdvertisementPort(int value) {
        _port = value;
    } // (method)
    
    /**
     * @see setAdvertisementPort()
     */
    public int getAdvertisementPort() {
        return _port;
    } // (method)        
    
    /**
     * Resolves a node into a node address.
     * (non-blocking)
     */
    public abstract NodeAddress resolveNodeAddress(SimpleName node);
    
    /**
     * Creates a simple name advertisement.
     * (non-blocking)
     */
    public abstract void registerService(SimpleName node);
    
    /**
     * Lists all the registered nodes.
     * (non-blocking) 
     */
    @Service(name = "list", title = "List", desc = "Retrieves the list of Node advertiseds.")
    public abstract Collection<AdvertisementInfo> list();
    
    /**
     * Resolves a node into full adverisement info.
     */
    public abstract AdvertisementInfo resolve(SimpleName node);
    
    /**
     * Ensures a advertisement is pulled down.
     */
    public abstract void unregisterService(SimpleName node);
    
    @Override
    public abstract void close() throws IOException;
    
    
    /**
     * (singleton, thread-safe, non-blocking)
     */
    private static class Instance {

		private static final AutoDNS INSTANCE = loadImpl();

	}
    
    /**
     * Returns the singleton instance of this class.
     */
    public static AutoDNS instance() {
        return Instance.INSTANCE;
    }

} // (class)
