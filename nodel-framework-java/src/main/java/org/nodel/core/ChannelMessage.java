package org.nodel.core;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import org.nodel.reflection.Serialisation;
import org.nodel.reflection.Value;

/**
 * Represents a nodel message within a nodel channel. 
 */
public class ChannelMessage {
    
    /**
     * FROM SERVER: The node this message is being addressed to.
     * FROM CLIENT: The node this message originated from.
     */
    @Value(order = 1)
    public String node;
    
    /**
     * FROM SERVER: The node's events registered to listen to in the channel.
     * FROM CLIENT: The node's events registered to listen to in the channel.
     */
    @Value(order = 4)
    public String[] events;
    
    /**
     * FROM CLIENT: Registering for interests (actions)
     * FROM SERVER: The actions
     */
    @Value(order = 3)    
    public String[] actions;
    
    /**
     * The action to call.
     * (client req.)
     */
    @Value(order = 6)
    public String action;
    
    /**
     * A provide argument
     * (client req. or server resp.)
     */
    @Value(order = 7)
    public Object arg;
    
    /**
     * The event that occurred
     * (server resp.)
     */
    @Value(order = 8)
    public String event;
    
    public enum Announcement {
        Moved
    }
    
    /**
     * General purpose (non error) announcements e.g. "Moved"
     */
    @Value(order = 8.5)
    public Announcement announcement;
    
    /**
     * A short error category.
     * (server resp.)
     */
    @Value(order = 9)
    public String error;
    
    /**
     * Reveals all nodes within this channel.
     * e.g. '*' reveal all 
     * (client req.)
     */
    @Value(order = 12)
    public String[] reveal;
    
    /**
     * (used by 'toString()')
     */
    private String string;
    
    /**
     * Returns a JSON-formatted version of this message.
     */
    public String toString() {
        if (this.string == null)
            this.string = Serialisation.serialise(this);
        
        return this.string;
    } // (method)

} // (class)
