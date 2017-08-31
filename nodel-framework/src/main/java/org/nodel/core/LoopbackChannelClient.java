package org.nodel.core;

/**
 * When nodes live in the same runtime, this class is used for rapid node messaging instead
 * of a networking stack.
 */
public class LoopbackChannelClient extends ChannelClient {
    
    /**
     * Static lock
     */
    private static Object s_lock = new Object();
    
    /**
     * Shared instance (Write locked around 's_lock').
     */
    private static LoopbackChannelClient s_instance;
    
    /**
     * Only one instance can exist.
     */
    private LoopbackChannelClient() {
        super(NodeAddress.IN_PROCESS);
    }

    @Override
    protected void start() {
        // always started
    }

    @Override
    public boolean isConnected() {
        // always connected
        return true;
    }

    @Override
    public void sendMessage(final ChannelMessage message) {
        // use thread-pool to avoid deep dive into server-side stack
        s_threadPool.execute(new Runnable() {

            @Override
            public void run() {
                LoopbackChannelServer.instance().receiveMessage(message);
            }

        });
    }

    /**
     * Called by its peer, LoopbackChannelServer.
     */
    public void receiveMessage(ChannelMessage message) {
        super.handleMessage(message);
    }
    
    /**
     * Returns an instance which can be shared.
     */
    public static LoopbackChannelClient instance() {
        if (s_instance == null) {
            synchronized(s_lock) {
                if (s_instance == null)
                    s_instance = new LoopbackChannelClient();
            }
        }
        
        return s_instance;
    } // (method)     

} // (class)
