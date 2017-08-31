package org.nodel.core;

import org.nodel.diagnostics.Diagnostics;
import org.nodel.diagnostics.LongSharableMeasurementProvider;
import org.nodel.diagnostics.SharableMeasurementProvider;

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
    
    /**
     * Only need one of these for all send/receive client/server combinations as they're connected symmetrically.
     * (diagnostics)
     */    
    private static SharableMeasurementProvider s_counterMessages = new LongSharableMeasurementProvider();
    
    /**
     * (diagnostics)
     */
    static {
        Diagnostics.shared().registerCounter("Nodel loopback channel.Messages", s_counterMessages, true);
        
        // measuring data rate is not needed as only references are passed around, not the data itself. 
    }    
    
    private static LoopbackChannelServer staticInstance;
    
    public LoopbackChannelServer(NodelServers nodelServer) {
        super(nodelServer);
        
        staticInstance = this;
    }

    @Override
    protected void sendMessage(final ChannelMessage message) {
        s_counterMessages.incr();

        // use thread-pool to avoid deep dive into client-side stack
        s_threadPool.execute(new Runnable() {

            @Override
            public void run() {
                LoopbackChannelClient.instance().receiveMessage(message);
            }

        });
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
