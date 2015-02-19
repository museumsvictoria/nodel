package org.nodel.discovery;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.util.List;

import org.nodel.reflection.Serialisation;
import org.nodel.reflection.Value;

/**
 * The simple packet that is used in multicast discovery.
 */
public class NameServicesChannelMessage {
    
    /**
     * (client to server) 
     * Indicates a discovery is wanting to be made, listing the nodes using pattern matching e.g. '*' for everything.
     */
    @Value(name = "discovery", genericClassA = String.class, order = 1)
    public List<String> discovery;
    
    /**
     * (client to server)
     * Indicates the types requested as part of the discovery.
     */
    @Value(name = "type", genericClassA = String.class, order = 2)
    public List<String> types;
    
    /**
     * (server to client)
     * Indicates the nodes that are present.
     */
    @Value(name = "present", genericClassA = String.class, order = 3)
    public List<String> present;
    
    /**
     * (server to client)
     * Indicates the addresses of all the node listed in 'present' field.
     */
    @Value(name = "addresses", genericClassA = String.class, order = 4)
    public List<String> addresses;
    
    /**
     * Holds the agent in use, e.g. 'Nodel-v2-javawindows'.
     * (optional)
     * (server to client)
     * (client to server)
     */
    @Value(name = "agent", order = 5)
    public String agent;
    
    /**
     * (server to client)
     * Used to stagger responses.
     */
    @Value(name = "delay", order = 6)
    public Integer delay;
    
    @Override
    public String toString() {
        return Serialisation.serialise(this);
    }

} // (class)
