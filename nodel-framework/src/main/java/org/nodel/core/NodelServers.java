package org.nodel.core;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.nodel.Handler;
import org.nodel.Handlers;
import org.nodel.SimpleName;
import org.nodel.discovery.AutoDNS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides Nodel server-specific services to the platform.
 */
public class NodelServers {

    /**
     * (logging related)
     */
    private static AtomicLong s_instanceCounter = new AtomicLong();

    /**
     * (logging related)
     */
    private Logger logger = LoggerFactory.getLogger(String.format("%s.instance%d", NodelServers.class.getName(), s_instanceCounter.getAndIncrement()));
    
    /**
     * Instance signal / lock.
     */
    private Object _signal = new Object();
    
    /**
     * Whether or not the Nodel client has actually started i.e. not necessarily
     * just had event handlers attached.
     * (locked around 'signal')
     */
    private boolean _started = false;    
    
    /**
     * (init. in 'start')
     */
    private ChannelServerSocket _channelServerSocket;
    
    /**
     * Holds the list of active channel servers.
     * (not thread-safe)
     */
    private ArrayList<ChannelServer> _channelServers = new ArrayList<ChannelServer>();
    
    /**
     * (used by 'nodeEntriesByNodeName')
     */
    private class NodeEntry {
        
        /**
         * (will never be null)
         */
        public SimpleName node;
        
        public NodeEntry(SimpleName node) {
            this.node = node;
        }
        
    } // (class)
    
    /**
     * Holds node information related to ensuring they're advertised.
     */
    private Map<SimpleName, NodeEntry> _nodeEntriesByNodeName = new HashMap<SimpleName, NodeEntry>();    
    
    /**
     * The node action handler registry.
     * (not thread-safe)
     */
    private Map<NodelPoint, NodelServerAction> _nodeActionHandlers = new HashMap<NodelPoint, NodelServerAction>();
    
    /**
     * The node actions registry (key:Node -> value:Action list)
     */
    private Map<SimpleName, List<SimpleName>> _nodeActions = new HashMap<SimpleName, List<SimpleName>>();  
    
    /**
     * The node event registry.
     * (not thread-safe)
     */
    private Map<SimpleName, List<SimpleName>> _nodeEvents = new HashMap<SimpleName, List<SimpleName>>();
    
    /**
     * Node-event bindings registry. Runs "parallel" to 'nodeEvents'
     * (not thread-safe)
     */
    private Map<NodelPoint, NodelServerEvent> _nodeEventBindings = new HashMap<NodelPoint, NodelServerEvent>();
    
    /**
     * Holds all channel-servers interested in specific events.
     * (not thread-safe)
     */
    private Map<SimpleName, List<ChannelServer>> _interestedChannels = new HashMap<SimpleName, List<ChannelServer>>();
    
    /**
     * Events handler(s) for when a crippling failure occurs.
     */
    protected Handlers.H1<Throwable> _onFailure = new Handlers.H1<Throwable>();
    
    /**
     * @see instance() method.
     */
    private static NodelServers _instance = new NodelServers();
    
    /**
     * Safely gets shared instance of the nodel server.
     */
    public static NodelServers instance() {
        return _instance;
    }
    
    /**
     * (private constructor)
     */
    private NodelServers() {
        _instance = this;
    }
    
    /**
     * Will start background initialisation of the Nodel server.
     * @return 'true' if first time to actually perform initialisation, 
     *         'false' if it initialisation has already been requested.
     */
    public boolean start() {
        synchronized(_signal) {
            return tryInitAndStart();
        }
    } // (method)
    
    /**
     * Performs any initialisation that's required and starts up the client if
     * if initialisation has not been started. Returns immediately.
     * (assumes locked)
     * @return 'true' if first time it's started, 'false' if it has already been started. 
     */
    private boolean tryInitAndStart() {
        if (_started)
            return false;
        
        // load the permanent loopback server
        final ChannelServer loopbackChannelServer = new LoopbackChannelServer(this);

        // attach the failure handler
        loopbackChannelServer.attachFailureHandler(new Handler.H1<Throwable>() {
            
            @Override
            public void handle(Throwable value) {
                handleChannelServerFailure(loopbackChannelServer, value);
            }
            
        });

        synchronized (_signal) {
            _channelServers.add(loopbackChannelServer);
        }

        // everything's attached, can now start
        loopbackChannelServer.start();        
        
		// start up an channel server socket on all interfaces and any port.

        int requestedPort = Nodel.getMessagingPort();
        _channelServerSocket = new ChannelServerSocket(requestedPort);
        _channelServerSocket.attachChannelServerHandler(new Handler.H1<Socket>() {
            
            @Override
            public void handle(Socket socket) {
                handleNewConnection(socket);
            }
            
        });

        _channelServerSocket.setStartedHandler(new Handler.H1<Integer>() {

            @Override
            public void handle(Integer port) {
                onStarted(port);
            }

        });

		_channelServerSocket.start();
        
        _started = true;

        return true;
    } // (method)
    
    /**
     * (started callback)
     */
    protected void onStarted(final Integer port) {
        logger.info("Channel server created. port:{}", port);

        Nodel.updateTCPPort(port);
    }

    /**
     * Attaches a failure handler. (multicast event, delegate must not block)
     */
    public boolean attachFailureHandler(Handler.H1<Throwable> handler) {
        synchronized (_signal) {
            if (handler == null)
                throw new IllegalArgumentException("Handler cannot be null.");

            return _onFailure.addHandler(handler);
        }
    } // (method)
    
    /**
     * Removes a failure handler. 
     */
    public boolean detachFailureHandler(Handler.H1<Throwable> handler) {
        synchronized (_signal) {
            if (handler == null)
                throw new IllegalArgumentException("Handler cannot be null.");

            return _onFailure.removeHandler(handler);
        }
    } // (method)
    
    /**
     * Returns whether a particular node is being hosted by this Nodel layer.
     */
    public boolean isHosted(SimpleName node) {
        synchronized (_signal) {
            return _nodeEntriesByNodeName.containsKey(node);
        }
    } // (method)    
    
    /**
     * Registers a node's action.
     */
    public void registerAction(NodelServerAction actionBinding) {
        synchronized (_signal) {
            tryInitAndStart();
            
            NodelPoint key = actionBinding._actionPoint;
            if (_nodeActionHandlers.containsKey(key))
                throw new NodelException("Already bound - " + key);
            
            SimpleName node = key.getNode();
            
            _nodeActionHandlers.put(key, actionBinding);
            
            List<SimpleName> actionList = _nodeActions.get(node);
            if (actionList == null) {
                actionList = new ArrayList<SimpleName>();
                _nodeActions.put(node, actionList);
            }

            if (!actionList.contains(key.getPoint()))
                actionList.add(key.getPoint());
            
            advertiseIfNecessary(node);
        }
    } // (method)
    
    public void unregisterAction(NodelServerAction actionBinding) {
        synchronized (_signal) {
            NodelPoint key = actionBinding._actionPoint;
            
            if (!_nodeActionHandlers.containsKey(key))
                throw new NodelException("Binding not found - " + key);
            
            _nodeActionHandlers.remove(key);
            
            SimpleName node = key.getNode();
            
            List<SimpleName> actionList = _nodeActions.get(node);
            if (!actionList.remove(key.getPoint()))
                throw new NodelException("Action not found - " + key.getPoint());
            
            tryCleanup(node);
        }
    } // (method)
    
    /**
     * (Assumes locked, args checked.)
     */
    private void tryCleanup(SimpleName node) {
        // check the action list count
        List<SimpleName> actionList = _nodeActions.get(node);
        
        // check the event list count
        List<SimpleName> eventList = _nodeEvents.get(node);
        
        if ((actionList == null || actionList.size() == 0) && (eventList == null || eventList.size() == 0)) {
            this.logger.info("No more references to " + node + " left; cleaning up its resources.");
            
            NodeEntry nodeEntry = _nodeEntriesByNodeName.remove(node);
            assert nodeEntry != null;
            
            // unregister immediately
            AutoDNS.instance().unregisterService(nodeEntry.node);
            
            // send down an announcement
            
            // look up all channels that are interested in the given node
            List<ChannelServer> channels = _interestedChannels.get(node);
            if (channels != null && channels.size() > 0) {
                // send the event out through all the interested channels
                for (ChannelServer channel : channels) {
                    // indicate this node isn't here any more
                    channel.sendInterestsResponse(node.getReducedName(), new String[0], new String[0]);

                    // POSSIBLE ALTERNATIVE
                    // channel.sendMovedMessage(node);
                } // (for)
            }
        }
    } // (method)

    /**
     * Advertises a node if it hasn't already been ask to.
     * (assumes locked)
     */
    private void advertiseIfNecessary(SimpleName node) {
        NodeEntry nodeEntry = _nodeEntriesByNodeName.get(node);
        if (nodeEntry == null) {
            nodeEntry = new NodeEntry(node);
            
            _nodeEntriesByNodeName.put(node, nodeEntry);
            
            AutoDNS.instance().registerService(node);
        }
    } // (method)
    
    /**
     * Returns the list of registered actions for a given node.
     */
    protected SimpleName[] getRegisteredActions(SimpleName node) {
        synchronized (_signal) {
            List<SimpleName> actionList = _nodeActions.get(node);
            
            if (actionList == null)
                return new SimpleName[0];
            
            return actionList.toArray(new SimpleName[actionList.size()]);
        }
    } // (method)
    
    /**
     * Gets an action handler for a given node and action or null if one hasn't been registered. 
     */
    protected NodelServerAction getActionRequestHandler(String nodeName, String action) {
        synchronized (_signal) {
            NodelPoint key = NodelPoint.create(nodeName, action);
            
            return _nodeActionHandlers.get(key);
        }
    } // (method)
    
    /**
     * Registers a node's event.
     */
    public void registerEvent(NodelServerEvent eventBinding) {
        synchronized (_signal) {
            tryInitAndStart();
            
            List<SimpleName> events = _nodeEvents.get(eventBinding._node);
            
            if (events == null) {
                events = new LinkedList<SimpleName>();
                _nodeEvents.put(eventBinding._node, events);
            }
            
            if (events.contains(eventBinding._event))
                // already register 
                throw new NodelException("Already bound - " + eventBinding._eventPoint);
            
            events.add(eventBinding._event);
            
            _nodeEventBindings.put(eventBinding._eventPoint, eventBinding);
            
            advertiseIfNecessary(eventBinding._node);
            
            return;
        }       
    } // (method)
    
    /**
     * Unregisters an event binding.
     */
    public void unregisterEvent(NodelServerEvent eventBinding) {
        synchronized (_signal) {
            List<SimpleName> events = _nodeEvents.get(eventBinding._node);
            
            if (events == null)
                throw new NodelException("Event binding not registered.");
            
            boolean removedEvent = events.remove(eventBinding._event);
            assert removedEvent : "Event should have been present.";
            
            boolean removedBinding = _nodeEventBindings.remove(eventBinding._eventPoint) != null;
            assert removedBinding : "Binding should have been present.";
            
            tryCleanup(eventBinding._node);
        }
    } // (method)    
    
    /**
     * Used by channels to register their interest in a Nodel event. No need to worry
     * about clean up (is done by this class if any channel fault occurs).
     * @return true if interest has been newly registered, false otherwise.
     * (exception-less)
     */
    protected void registerInterest(ChannelServer channel, String nodeName) {
        synchronized (_signal) {
            SimpleName node = new SimpleName(nodeName);
            
            List<ChannelServer> channels = _interestedChannels.get(node);
            
            if (channels == null) {
                // is first time, so initialise
                channels = new LinkedList<ChannelServer>();
                _interestedChannels.put(node, channels);
            }
            
            if (channels.contains(channel))
                // already registered, no need to do anything
                return;
                
            channels.add(channel);
            
            return;
        }
    } // (method)
    
    /**
     * Returns all the events that have been registered or null
     * if no matching node is found.
     */
    protected SimpleName[] getRegisteredEvents(SimpleName node) {
        synchronized(_signal) {
            List<SimpleName> events = _nodeEvents.get(node);
            if (events == null)
                return new SimpleName[0];
            
            return events.toArray(new SimpleName[events.size()]);
        }
    } // (method)
    
    /**
     * Completely unregisters all interests.
     */
    private void cleanupInterest(ChannelServer channel) {
        synchronized (_signal) {
            // keep a separate list of those that need to be removed
            ArrayList<SimpleName> toRemove = new ArrayList<SimpleName>();

            // go through the entire map, removing any references to the channel
            for (SimpleName key : _interestedChannels.keySet()) {
                List<ChannelServer> channels = _interestedChannels.get(key);

                // ('channels' will never be null)

                if (channels.remove(channel)) {
                    // check if there's anything left
                    if (channels.size() == 0)
                        toRemove.add(key);
                }
            }

            // remove all the entries containing empty lists
            for (SimpleName key : toRemove) {
                _interestedChannels.remove(key);
            }
            
            int removed = toRemove.size();
            this.logger.info("Cleaned up " + removed + " channel server reference" + (removed == 1 ? "" : "s") + ".");
        }
    } // (method)
    
    /**
     * Called when an event has occurred. Must have previously been registered. 
     */    
    public void emitEvent(NodelServerEvent eventBinding, Object arg) {
        emitEvent(eventBinding._node.getReducedName(), eventBinding._event.getReducedName(), arg);
    }
    
    /**
     * (Used by Channel Server) 
     */
    protected void emitEvent(String nodeName, String eventName, Object arg) {
        synchronized (_signal) {
            SimpleName node = new SimpleName(nodeName);
            
            if (!_nodeEvents.containsKey(node))
                throw new NodelException("A node must be registered before firing any events.");
            
            // look up all channels that are interested in the given node
            List<ChannelServer> channels = _interestedChannels.get(node);
            if (channels == null || channels.size() == 0) {
                // no one's interested so don't have to do anything 
                return;
            }
            
            // send the event out through all the interested channels
            for(ChannelServer channel : channels) {
                channel.sendEventMessage(nodeName, eventName, arg);
            } // (for)
        }
    } // (method)
    
    /**
     * Started or not.
     */
    public boolean isStarted() {
    	return _started;
    }
    
    /**
     * Gets the port this nodel server is bound to.
     */
    public int getPort() {
        synchronized (_signal) {
            if (!_started)
                throw new IllegalStateException("Not started.");

            return _channelServerSocket.getPort();
        }
    } // (method)
    
    /**
     * When a new connection occurs.
     */
    private void handleNewConnection(Socket socket) {
        final ChannelServer tcpChannelServer = new TCPChannelServer(this, socket);

        // attach the failure handler
        tcpChannelServer.attachFailureHandler(new Handler.H1<Throwable>() {
        	
            @Override
            public void handle(Throwable value) {
                handleChannelServerFailure(tcpChannelServer, value);
            }
            
        });

        synchronized (_signal) {
            _channelServers.add(tcpChannelServer);
        }

        // everything's attached, can now start
        tcpChannelServer.start();
    } // (method)
    
    /**
     * When a channel server becomes crippled.
     */
    private void handleChannelServerFailure(ChannelServer channelServer, Throwable value) {
        // remove this from the list of channel servers
        synchronized (_signal) {
            // event may be of interest so log as 'info'
            this.logger.info("Failure on a channel server; this may be natural. Removing it. (Error was " + value.toString() + ")");

            _channelServers.remove(channelServer);
            
            cleanupInterest(channelServer);
        }
    } // (method)
    
    /**
     * Permanently shuts down this Nodel Server
     */
    public void shutdown() {
        try {
            AutoDNS.instance().close();
        } catch (Exception exc) {
            // ignore
        }
    } // (method)    

} // (class)
