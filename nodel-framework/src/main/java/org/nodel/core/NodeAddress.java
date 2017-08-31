package org.nodel.core;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

/**
 * Deals with equality, etc. for Node addresses for use in Map, Lists, etc.
 * 
 * 'InetSocketAddress' was not suitable as it deals with address resolution which
 * is dealt with in other parts of Nodel. 
 */
public class NodeAddress {
    
    /**
     * Represents an in-process node.
     */
    public final static NodeAddress IN_PROCESS = new NodeAddress("internal", 0);
    
    /**
     * (see 'getHost()')
     */
    private String _host;
    
    /**
     * (see 'getPort()')
     */
    private int _port;
    
    /**
     * (see 'getPort()')
     */
    private String _reducedHost;
    
    /**
     * (private constructor)
     */
    private NodeAddress(String host, int port) {
        if (host == null)
            throw new IllegalArgumentException("Host cannot be null");
        
        if (port < 0 || port > 0xFFFF)
            throw new IllegalArgumentException("Port out of range - " + port);
        
        _host = host;
        _port = port;
        
        _reducedHost = host.trim().toLowerCase();
    }
    
    /**
     * Creates a new Nodel address.
     */
    public static NodeAddress create(String host, int port) {
        return new NodeAddress(host, port);
    } // (method)
    
    /**
     * Returns the host part.
     */
    public String getHost() {
        return _host;
    }
    
    /**
     * Returns the port part.
     */
    public int getPort() {
        return _port;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof NodeAddress))
            return false;
        
        NodeAddress other = (NodeAddress) obj;

        return _reducedHost.equals(other._reducedHost) && _port == other._port;
    } // (method)
    
    @Override
    public int hashCode() {
        return _reducedHost.hashCode() ^ _port;
    } // (method)
    
    /**
     * Returns the reduced version of the name. 
     */
    public String toString() {
        return String.format("\"%s:%s\"", _reducedHost, _port);
    }
    
} // (class)
