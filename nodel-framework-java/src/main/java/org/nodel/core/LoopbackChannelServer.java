package org.nodel.core;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

/**
 * When nodes live in the same runtime, this class is used for radpid node messaging instead
 * of a networking stack.
 */
public class LoopbackChannelServer extends ChannelServer {
    
    private static LoopbackChannelServer staticInstance;
    
    public LoopbackChannelServer(NodelServers nodelServer) {
        super(nodelServer);
        
        staticInstance = this;
    }

    @Override
    protected void sendMessage(final ChannelMessage message) {
        LoopbackChannelClient.instance().receiveMessage(message);
    }
    
    /**
     * Used by peer LoopbackChannelClient.
     */
    public void receiveMessage(ChannelMessage message) {
        super.handleMessage(message);
    }

    @Override
    public void start() {
        // nothing required here
    }

    /**
     * Returns an instance which can be shared. This method is likely to block.
     */
    public static LoopbackChannelServer instance() {
        return staticInstance;
    } // (method)      

} // (class)
