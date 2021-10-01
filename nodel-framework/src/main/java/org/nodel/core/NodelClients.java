package org.nodel.core;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.nodel.DateTimes;
import org.nodel.Handler;
import org.nodel.Handlers;
import org.nodel.SimpleName;
import org.nodel.discovery.AdvertisementInfo;
import org.nodel.discovery.AdvertisementInfo.Addresses;
import org.nodel.discovery.AutoDNS;
import org.nodel.reflection.Serialisation;
import org.nodel.reflection.Value;
import org.nodel.threading.ThreadPool;
import org.nodel.threading.TimerTask;
import org.nodel.threading.Timers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides Nodel client-specific services to the platform.
 */
public class NodelClients {
    
    /**
     * A single instance of the 'NodelClient' class.
     */
    private static NodelClients _instance = new NodelClients();
    
    public static NodelClients instance() {
        return _instance;
    }
    
    /**
     * Instance signal / lock.
     */
    private Object _signal = new Object();
    
    /**
     * When this class should be permanently shutdown.
     */
    private boolean _closed = false;
    
    /**
     * (logging related)
     */
    private Logger _logger = LoggerFactory.getLogger(String.format("%s", this.getClass().getName()));
    
    /**
     * (threading)
     */
    private ThreadPool _threadPool = new ThreadPool("Nodel client", 128);
    
    /**
     * Thread pool for the handlers themselves.
     */
    private ThreadPool _threadPoolHandlers = new ThreadPool("Nodel client handlers", 256); 
    
    /**
     * (threading)
     */
    private Timers _timerThread = new Timers("Nodel clients");

    /**
     * (used in 'nodeEntryByNodeName' map)
     */
    private class NodeEntry {
        
        /**
         * (same as key, will never be null)
         */
        public SimpleName node;
        
        public TimerTask schedule;
        
        public ChannelClient channel;
        
        public boolean isBusy = false;
        
        /**
         * Flag indicating there has been a recent connection error.
         */
        public boolean recentConnectionError = false;
        
        /**
         * If this entry has been disposed.
         */
        public boolean disposed;
        
        /**
         * (used in 'eventHandlerEntries' map)
         */
        public class EventHandlerEntry {
            
            /**
             * (will never be null)
             */
            public NodelPoint eventPoint;
            
            /**
             * Whether or not this event handler has been registered
             */
            public boolean isRegistered = false;
            
            /**
             * Holds *ALL* the event bindings for this unique action.
             * (using LinkedList to help rapid 'adding' and 'removing')
             */
            public List<NodelClientEvent> bindings = new LinkedList<NodelClientEvent>();            
            
            public EventHandlerEntry(NodelPoint eventPoint) {
                this.eventPoint = eventPoint;
            }
            
            @Override
            public String toString() {
                return Serialisation.serialise(this);
            }

        } // (class)
        
        /**
         * Holds entry related to unique 'events'
         * NodelName here is the 'Event'
         */
        @Value(name = "eventHandlerEntries")
        public Map<SimpleName, EventHandlerEntry> eventHandlerEntries = new LinkedHashMap<SimpleName, EventHandlerEntry>();
        
        /**
         * (used in 'actionEntries' list)
         */
        public class ActionEntry {
            
            /**
             * (will never be null)
             */
            @Value(name = "actionPoint")
            public NodelPoint actionPoint;
            
            @Value(name = "isRegistered")
            public boolean isRegistered = false;
            
            /**
             * Holds *ALL* the action bindings for this unique action.
             * (using LinkedList to help rapid 'adding' and 'removing')
             */
            @Value(name = "bindings")
            public List<NodelClientAction> bindings = new LinkedList<NodelClientAction>();
            
            public ActionEntry(NodelPoint actionPoint) {
                this.actionPoint = actionPoint;
            }
            
            @Override
            public String toString() {
                return Serialisation.serialise(this);
            }
            
        } // (class)
        
        /**
         * Holds entry related to unique 'actions'
         * NodelName here is the 'Action'
         */
        @Value(name = "actionEntries")
        public Map<SimpleName, ActionEntry> actionEntries = new LinkedHashMap<SimpleName, ActionEntry>();
        
        public NodeEntry(SimpleName node) {
            this.node = node;
        }
        
        @Override
        public String toString() {
            return Serialisation.serialise(this);
        }
        
    } // (class)    

    /**
     * Holds the list of channel clients by address.
     * (locked around 'signal')
     */
    private Map<SimpleName, NodeEntry> nodeEntriesByNodeName = new HashMap<SimpleName, NodeEntry>();
    
    /**
     * (used in 'channelsByAddress')
     */
    private class ChannelEntry {
        
        /**
         * (will never be null)
         */
        public NodeAddress address;
        
        /**
         * (will never be null)
         */
        public ChannelClient channel;
        
        /**
         * Holds the list of nodes managed by this channel.
         */
        public Set<SimpleName> nodes = new HashSet<SimpleName>();
        
        public ChannelEntry(NodeAddress address, ChannelClient channel) {
            this.address = address;
            this.channel = channel;
        }
        
    } // (class)
    
    /**
     * Holds the channels (mapped by their address).
     */
    private Map<NodeAddress, ChannelEntry> channelsByAddress = new HashMap<NodeAddress, ChannelEntry>();
    
    /**
     * Events handler(s) for when a crippling failure occurs.
     */
    protected Handlers.H1<Throwable> onFailure = new Handlers.H1<Throwable>();
    
    /**
     * Events handler(s) for wiring faults occur.
     */
    protected Handlers.H1<Throwable> onWiringFault = new Handlers.H1<Throwable>();

    /**
     * Private constructor.
     */
    private NodelClients() {
    } // (init)
    
    /**
     * Attaches a failure handler. 
     * (multicast event, delegate must not block)
     */
    public boolean attachFailureHandler(Handler.H1<Throwable> handler) {
        synchronized (_signal) {
            if (handler == null)
                throw new IllegalArgumentException("Handler cannot be null.");

            return this.onFailure.addHandler(handler);
        }
    } // (method)
    
    /**
     * Removes a failure handler. 
     */
    public boolean detachFailureHandler(Handler.H1<Throwable> handler) {
        synchronized (_signal) {
            if (handler == null)
                throw new IllegalArgumentException("Handler cannot be null.");

            return this.onFailure.removeHandler(handler);
        }
    } // (method)
    
    /**
     * Attaches a failure handler. 
     * (multicast event, delegate must not block)
     */
    public boolean attachWiringFaultHandler(Handler.H1<Throwable> handler) {
        synchronized (_signal) {
            if (handler == null)
                throw new IllegalArgumentException("Handler cannot be null.");

            return this.onWiringFault.addHandler(handler);
        }
    } // (method)
    
    /**
     * Removes a failure handler. 
     */
    public boolean detachWiringFaultHandler(Handler.H1<Throwable> handler) {
        synchronized (_signal) {
            if (handler == null)
                throw new IllegalArgumentException("Handler cannot be null.");

            return this.onWiringFault.removeHandler(handler);
        }
    } // (method)     
    
    /**
     * Registers interest in a Node's events. 
     */
    public void registerEventInterest(NodelClientEvent eventBinding) {
        if (eventBinding == null)
            throw new IllegalArgumentException();
        
        synchronized(_signal) {
            NodeEntry nodeEntry = this.nodeEntriesByNodeName.get(eventBinding._node);
            if (nodeEntry == null) {
                nodeEntry = new NodeEntry(eventBinding._node);
                this.nodeEntriesByNodeName.put(eventBinding._node, nodeEntry);
            }
            
            _logger.info("Registering interest in event {}", eventBinding.getNodelPoint());
            
            // register event (only one needs to be registered) by the event point
            NodeEntry.EventHandlerEntry eventEntry = nodeEntry.eventHandlerEntries.get(eventBinding._event);
            if (eventEntry == null) {
                eventEntry = nodeEntry.new EventHandlerEntry(eventBinding._eventPoint);
                nodeEntry.eventHandlerEntries.put(eventBinding._event, eventEntry);
            }
            
            // register every event binding for later cleanup purposes
            eventEntry.bindings.add(eventBinding);

            // init wiring state
            ChannelClient channel = nodeEntry.channel;
            if (channel != null && channel.isWiredEvent(eventBinding._node, eventBinding._event))
                eventBinding.setBindingState(BindingState.Wired);

            tryMaintainNode(nodeEntry);
        }
    } // (method)
    
    /**
     * Releases resources related to an event binding, shutting down
     * channels if necessary.
     */
    protected void release(NodelClientEvent event) {
        synchronized (_signal) {
            NodeEntry nodeEntry = this.nodeEntriesByNodeName.get(event._node);
            if (nodeEntry == null) {
                // can safely ignore
                return;
            }
            
            _logger.info("Releasing interest in event {}", event.getNodelPoint());
            
            NodeEntry.EventHandlerEntry eventEntry = nodeEntry.eventHandlerEntries.get(event._event);

            if (eventEntry == null) {
                // 'registerInterest' is sometimes never called, means nothing more to clean up
                return;
            }
            
            // remove the individual binding
            eventEntry.bindings.remove(event);
            
            if (eventEntry.bindings.size() == 0) {
                // remove the action entry
                nodeEntry.eventHandlerEntries.remove(event._event);
                
                tryCleanup(nodeEntry);
            }
        }
    } // (method)    
    
    /**
     * Performs node maintenance if necessary otherwise quickly returns.
     * (assumes locked)
     */
    private void tryMaintainNode(final NodeEntry nodeEntry) {
        if (nodeEntry.isBusy) {
            // already busy dealing with this node.
            return;
        }

        nodeEntry.isBusy = true;

        _threadPool.execute(new Runnable() {
            
            @Override
            public void run() {
                doMaintainNode(nodeEntry, false);
            }
            
        });
    } // (method)
    
    /**
     * Does any node maintenance, including establishing channels, (re)registering, etc.
     * (thread-pool entry-point)
     */
    private void doMaintainNode(final NodeEntry nodeEntry, boolean isSchedule) {
        synchronized (_signal) {
            if (_closed || nodeEntry.disposed)
                return;
            
            if (isSchedule) {
                // clear the schedule flag regardless
                nodeEntry.schedule = null;
            }
            
            if (nodeEntry.channel == null) {
                
                if (NodelServers.instance().isHosted(nodeEntry.node)) {
                    // it's hosted internally (in process)
                    _logger.info("'{}' is in-process; no connection is required.", nodeEntry.node);
                    handleResolutionComplete(nodeEntry, NodeAddress.IN_PROCESS);
                    
                } else {
                    // it's hosted on the external network
                    NodeAddress address = AutoDNS.instance().resolveNodeAddress(nodeEntry.node);
                    
                    handleResolutionComplete(nodeEntry, address);
                }
            } else {
                completeHandlerRegistration(nodeEntry, nodeEntry.channel);
            }
        }
    } // (method)
    
    
    /**
     *  Registers interest in a Node's actions
     */
    public void registerActionInterest(NodelClientAction actionBinding) {
        if (actionBinding == null)
            throw new IllegalArgumentException();

        synchronized (_signal) {
            NodeEntry nodeEntry = this.nodeEntriesByNodeName.get(actionBinding._node);
            if (nodeEntry == null) {
                nodeEntry = new NodeEntry(actionBinding._node);
                this.nodeEntriesByNodeName.put(actionBinding._node, nodeEntry);
            }
            
            _logger.info("Registering interest in action {}", actionBinding.getNodelPoint());
            
            // register action (only one needs to be registered) by the action point
            NodeEntry.ActionEntry actionEntry = nodeEntry.actionEntries.get(actionBinding._action);
            if (actionEntry == null) {
                actionEntry = nodeEntry.new ActionEntry(actionBinding._nodelPoint);
                nodeEntry.actionEntries.put(actionBinding._action, actionEntry);
            }
            
            // register every action binding for later cleanup purposes
            actionEntry.bindings.add(actionBinding);
            
            // init wiring state
            ChannelClient channel = nodeEntry.channel;
            if (channel != null && channel.isWiredAction(actionBinding._node, actionBinding._action))
                actionBinding.setBindingState(BindingState.Wired);
            
            // this will establish a connection regardless
            tryMaintainNode(nodeEntry);
        }
    } // (method)
    
    /**
     * Releases resources related to an action binding, shutting down
     * channels if necessary.
     */
    protected void release(NodelClientAction action) {
        synchronized (_signal) {
            NodeEntry nodeEntry = this.nodeEntriesByNodeName.get(action._node);
            if (nodeEntry == null) {
                // can safely ignore
                return;
            }
            
            _logger.info("Releasing interest in action {}", action.getNodelPoint());
            
            NodeEntry.ActionEntry actionEntry = nodeEntry.actionEntries.get(action._action);

            if (actionEntry == null) {
                // 'registerInterest' is sometimes never called, means nothing more to clean up
                return;
            }

            // remove the individual binding
            actionEntry.bindings.remove(action);
            
            if (actionEntry.bindings.size() == 0) {
                // remove the action entry
                nodeEntry.actionEntries.remove(action._action);
                
                tryCleanup(nodeEntry);
            }
        }
    } // (method)
    
    /**
     * Called when no bindings are left for a given node entry.
     * (assumes locked)
     */
    private void tryCleanup(NodeEntry nodeEntry) {
        if (nodeEntry.actionEntries.size() > 0 || nodeEntry.eventHandlerEntries.size() > 0) {
            // still got some interest
            return;
        }
        
        // "dispose" the node entry and remove it
        nodeEntry.disposed = true;
        this.nodeEntriesByNodeName.remove(nodeEntry.node);
        
        ChannelClient channel = nodeEntry.channel;
        if (channel == null)
            // done all we need to do
            return;
        
        channel.removeNode(nodeEntry.node);
        
        // get the related channel entry
        ChannelEntry channelEntry = this.channelsByAddress.get(channel.getAddress());
        
        channelEntry.nodes.remove(nodeEntry.node);
        
        if (channelEntry.nodes.size() == 0) {
            // bring down the channel gracefully
            channel.close();
            
            unwireChannel(channel);
            
            this.channelsByAddress.remove(channel.getAddress());
        }
    } // (method)

    private void handleResolutionComplete(final NodeEntry nodeEntry, NodeAddress address) {
        synchronized (_signal) {
            // (also check for any recent connection errors to avoid)

            if (address == null || nodeEntry.recentConnectionError) {
                if (nodeEntry.recentConnectionError) {
                    // clear the flag regardless
                    _logger.info("There has been a recent connection problem, backing off.");
                    
                    nodeEntry.recentConnectionError = false;
                }

                // re-schedule an update 30 seconds into the future if
                // there no future schedule to do so
                _logger.debug("Address was not resolved so rescheduling maintainence...");
                if (nodeEntry.schedule == null) {
                    nodeEntry.schedule = _timerThread.schedule(new TimerTask() {
                        
                        @Override
                        public void run() {
                            doMaintainNode(nodeEntry, true);
                        }
                        
                    }, 30000);
                }
                
                // notify all linked bindings that it could not be resolved
                //  (actions...)
                for (NodeEntry.ActionEntry entry : nodeEntry.actionEntries.values()) {
                    for (NodelClientAction binding : entry.bindings) {
                        binding.setBindingState(BindingState.ResolutionFailure);
                    }
                }
                
                //  (events...)
                for (NodeEntry.EventHandlerEntry entry : nodeEntry.eventHandlerEntries.values()) {
                    for (NodelClientEvent binding : entry.bindings) {
                        binding.setBindingState(BindingState.ResolutionFailure);
                    }
                }              
                
            } else {
                _logger.debug("Address was resolved. Will use a new or established channel. address={}", address);
                
                // notify all linked bindings that it could not be resolved
                //   (actions...)
                for (NodeEntry.ActionEntry entry : nodeEntry.actionEntries.values()) {
                    for (NodelClientAction binding : entry.bindings) {
                        binding.setBindingState(BindingState.Resolved);
                    }
                }

                //   (events...)
                for (NodeEntry.EventHandlerEntry entry : nodeEntry.eventHandlerEntries.values()) {
                    for (NodelClientEvent binding : entry.bindings) {
                        binding.setBindingState(BindingState.Resolved);
                    }
                }

                // if we're here, we have an address and there hasn't been a recent connection error

                // find the channel or establish a new one
                ChannelClient channel;

                ChannelEntry channelEntry = this.channelsByAddress.get(address);
                if (channelEntry == null) {
                    _logger.debug("Establishing a new channel...");

                    // no existing channel exists, so create one
                    if (address.equals(NodeAddress.IN_PROCESS))
                        channel = LoopbackChannelClient.instance();
                    else
                        channel = new TCPChannelClient(address);

                    channelEntry = new ChannelEntry(address, channel);

                    // attach connection handlers
                    final ChannelEntry tmpChannelEntry = channelEntry;
                    channel.attachConnectedHandler(new Handler.H0() {
                        
                        @Override
                        public void handle() {
                            handleChannelConnected(tmpChannelEntry);
                        }
                        
                    });
                    channel.attachWiringSuccessHandler(new Handler.H3<SimpleName, Set<SimpleName>, Set<SimpleName>>() {
                        
                        @Override
                        public void handle(SimpleName node, Set<SimpleName> actions, Set<SimpleName> events) {
                            handleWiringSuccess(node, actions, events);
                        }
                        
                    });
                    channel.attachConnectionFaultHandler(new Handler.H1<Exception>() {
                        
                        @Override
                        public void handle(Exception value) {
                            handleChannelConnectionFault(tmpChannelEntry, value);
                        }
                        
                    });
                    channel.attachWiringFaultHandler(new Handler.H3<SimpleName, Set<SimpleName>, Set<SimpleName>>() {
                        
                        @Override
                        public void handle(SimpleName node, Set<SimpleName> missingActions, Set<SimpleName> missingEvents) {
                            handleWiringFault(tmpChannelEntry, node, missingActions, missingEvents);
                        }
                        
                    });

                    // start up the channel
                    // (is non-blocking)
                    long ts = System.nanoTime();
                    _logger.info("Starting channel...");
                    channel.start();
                    _logger.info("Channel started (took " + DateTimes.formatPeriod(ts) + ")");

                    this.channelsByAddress.put(address, channelEntry);

                } else {
                    _logger.debug("Using an established channel.");

                    // an existing channel already exists
                    channel = channelEntry.channel;
                }

                // update node and channel entries
                nodeEntry.channel = channel;
                channelEntry.nodes.add(nodeEntry.node);

                // channel is either existing or new
                completeHandlerRegistration(nodeEntry, channel);
            }
        }
    } // (method)
    
    /**
     * "Unwires" all ChannelClient event handlers.  
     * (assumes locked)
     */
    private void unwireChannel(ChannelClient channel) {
        if (channel != null) {
            channel.attachConnectedHandler(null);
            channel.attachConnectionFaultHandler(null);
            channel.attachWiringFaultHandler(null);
            channel.attachWiringSuccessHandler(null);
        }
    }

    /**
     * Called when the channel actually connects.
     */
    private void handleChannelConnected(ChannelEntry tmpChannelEntry) {
        
    } // (method)

    /**
     * Completes event-handler registration for a given channel.
     * (assumes locked)
     */
    private void completeHandlerRegistration(final NodeEntry nodeEntry, ChannelClient channel) {
        // go through each handler entry, registering if necessary
        for (final NodeEntry.EventHandlerEntry eventHandlerEntry : nodeEntry.eventHandlerEntries.values()) {
            if (eventHandlerEntry.isRegistered) {
                
                continue;
            }

            _logger.info("Registering interest handler '{}'", eventHandlerEntry.eventPoint);
            channel.registerEventInterest(eventHandlerEntry.eventPoint, new ChannelClient.ChannelEventHandler() {

                @Override
                public void handle(NodelPoint eventPoint, Object arg) {
                    // use the original event point
                    handleChannelEvent(eventHandlerEntry, arg);
                }

            });

            eventHandlerEntry.isRegistered = true;
        } // (for)
        
        // go through each action, registering if necessary
        for(NodeEntry.ActionEntry actionEntry : nodeEntry.actionEntries.values()) {
            if (actionEntry.isRegistered)
                continue;
            
            _logger.trace("Registering action '{}'", actionEntry.actionPoint);
            channel.registerActionInterest(actionEntry.actionPoint);
            
            actionEntry.isRegistered = true;
        } // (for)
        
        // finally clear the busy flag
        nodeEntry.isBusy = false;
    } // (method)
    
    /**
     * Handles events generated from the channel.
     */
    private void handleChannelEvent(final NodeEntry.EventHandlerEntry eventHandlerEntry, final Object arg) {
        _logger.info("Received channel event {}", eventHandlerEntry.eventPoint);
        
        synchronized (_signal) {
            // go through all the registered handlers
            for (final NodelClientEvent handler : eventHandlerEntry.bindings) {

                // handlers may time take to return so needs to be threaded and invoked
                // by thread pool for external handlers
                _threadPoolHandlers.execute(new Runnable() {

                    @Override
                    public void run() {
                        try {
                            handler._handler.handleEvent(eventHandlerEntry.eventPoint.getNode(), eventHandlerEntry.eventPoint.getPoint(), arg);
                        } catch (Exception exc) {
                            // a handler did not take care of an exception
                            _logger.info("An event handler did not take care of an exception; ignoring. Exception was '{}'", exc);
                        }
                    }

                });
            } // (for)
        }
    } // (method)

    /**
     * Calls an action on a remote node.
     */
    public void call(NodelClientAction action, Object arg) {
        synchronized (_signal) {
            NodeEntry nodeEntry = this.nodeEntriesByNodeName.get(action._node);
            if (nodeEntry == null)
                // have never even registered for any events or actions
                // so can't do anything
                return;
            
            if (nodeEntry.channel == null)
                // not linked up yet so
                // can't do anything
                return;

            nodeEntry.channel.sendCallMessage(action._node, action._action, arg);
        }
    } // (method)
    
    /**
     * When a channel faults permanently. 
     */
    private void handleChannelConnectionFault(ChannelEntry channelEntry, Exception value) {
        synchronized (_signal) {
            // got a connection fault so need to reset the channel-related data-structures
            
            // unwire the channel if necessary
            unwireChannel(channelEntry.channel);
            
            // clear the channel entry
            channelEntry.channel = null;
            
            // go through each node-entry and reset everything
            for (SimpleName node : channelEntry.nodes) {
                NodeEntry nodeEntry = this.nodeEntriesByNodeName.get(node);
                
                // used to prevent rapid reconnects
                nodeEntry.recentConnectionError = true;
                
                nodeEntry.channel = null;
                
                // go through each event handler and action entries clearing the channel
                // registration flags
                
                for (NodeEntry.EventHandlerEntry eventHandlerEntry : nodeEntry.eventHandlerEntries.values())
                    eventHandlerEntry.isRegistered = false;
                
                for (NodeEntry.ActionEntry actionEntry : nodeEntry.actionEntries.values())
                    actionEntry.isRegistered = false;
                
                tryMaintainNode(nodeEntry);
            } // (for)
            
            // remove the channel entry altogether
            this.channelsByAddress.remove(channelEntry.address);
        }
    } // (method)
    
    protected void handleWiringSuccess(SimpleName relatedNode, Set<SimpleName> presentActions, Set<SimpleName> presentEvents) {
        synchronized (_signal) {
            _logger.info("Wiring confirmed for node {}. events:{}, actions:{}", relatedNode, presentActions, presentEvents);

            for (NodeEntry nodeEntry : this.nodeEntriesByNodeName.values()) {
                // go through all actions
                for (NodeEntry.ActionEntry actionEntry : nodeEntry.actionEntries.values()) {
                    // go through all the missing actions
                    for (SimpleName presentAction : presentActions) {
                        if (actionEntry.actionPoint.getNode().equals(relatedNode) && actionEntry.actionPoint.getPoint().equals(presentAction)) {
                            // found a match
                            for (NodelClientAction binding : actionEntry.bindings) {
                                binding.setBindingState(BindingState.Wired);
                            }
                        }
                    }
                }

                // go through all actions
                for (NodeEntry.EventHandlerEntry eventEntry : nodeEntry.eventHandlerEntries.values()) {
                    // go through all the missing actions
                    for (SimpleName presentEvent : presentEvents) {
                        if (eventEntry.eventPoint.getNode().equals(relatedNode) && eventEntry.eventPoint.getPoint().equals(presentEvent)) {
                            // found a match
                            for (NodelClientEvent binding : eventEntry.bindings) {
                                binding.setBindingState(BindingState.Wired);
                            }
                        }
                    }
                }
            }
        } // (sync)
    } // (method) 
    
    /**
     * When a wiring fault is actively detected.
     */
    private void handleWiringFault(ChannelEntry channelEntry, SimpleName relatedNode, Set<SimpleName> missingActions, Set<SimpleName> missingEvents) {
        synchronized (_signal) {
            // check whether the node has move or was never there
            if (missingActions.isEmpty() && missingEvents.isEmpty()) {
                _logger.info("Have been told a node has moved or no longer exists '{}'", relatedNode);
                
                // remove the node from the channel
                if (channelEntry.channel != null)
                    channelEntry.channel.removeNode(relatedNode);
                
                channelEntry.nodes.remove(relatedNode);
                
                NodeEntry nodeEntry = this.nodeEntriesByNodeName.get(relatedNode);
                if (nodeEntry == null) {
                    _logger.info("This node has already been released locally, '{}'", relatedNode);
                    
                    // has already been removed from this end
                    return;
                }
                
                nodeEntry.channel = null;

                // go through each event handler and action entries clearing the channel registration flags

                for (NodeEntry.EventHandlerEntry eventHandlerEntry : nodeEntry.eventHandlerEntries.values())
                    eventHandlerEntry.isRegistered = false;

                for (NodeEntry.ActionEntry actionEntry : nodeEntry.actionEntries.values())
                    actionEntry.isRegistered = false;

                tryMaintainNode(nodeEntry);
            } // (if)
        } // (sync)
    } // (method)
    
    public static class NodeURL {
        
        @Value(name = "node")
        public SimpleName node;
        
        @Value(name = "address")
        public String address;

    } // (class)
    
    /**
     * Gets the URLs of *all* the nodes. 
     */
    protected List<NodeURL> getAllNodesURLs() throws IOException {
        return getNodeURLs(null);
    }

    /**
     * Gets the URLs of a node.
     */
    protected List<NodeURL> getNodeURLs(SimpleName name) throws IOException {
        Collection<AdvertisementInfo> list;
        if (name == null) {
            list = AutoDNS.instance().list();
        } else {
            AdvertisementInfo result = AutoDNS.instance().resolve(name);
            list = result != null ? Arrays.asList(result) : Collections.<AdvertisementInfo>emptyList();
        }

        List<NodeURL> nodeURLs = new ArrayList<NodeURL>();

        for (AdvertisementInfo adInfo : list) {
            for (Addresses addresses : adInfo.getAllAddresses()) {
                for (String address : addresses.getAddresses()) {
                    if (address.toLowerCase().startsWith("http://")) {

                        NodeURL nodeURL = new NodeURL();
                        nodeURL.node = addresses.getNode(); // for different names before name reduction 
                        nodeURL.address = address.replace("%NODE%", safeURLEncode(nodeURL.node.getReducedName()));

                        nodeURLs.add(nodeURL);
                    }
                }
            }
        }
        
        // sort them the list
        Collections.sort(nodeURLs, s_nodeURLComparator);
        
        return nodeURLs;
    }
    
    /**
     * Permanently shuts down all resources related to 
     */
    public void shutdown() {
        _closed = true;
        
        try {
            AutoDNS.instance().close();
        } catch (Exception exc) {
        }
    } // (method)
    
    /**
     * (static callback)
     */
    private static Comparator<? super NodeURL> s_nodeURLComparator = new Comparator<NodeURL>() {

        @Override
        public int compare(NodeURL o1, NodeURL o2) {
            return o1.node.getReducedForMatchingName().compareTo(o2.node.getReducedForMatchingName());
        }

    };    
    
    /**
     * Performs URL encoding on a string. (exception-less)
     */
    private final static String safeURLEncode(String text) {
        try {
            // this URL encoder swaps spaces for '+'s, but using more aggressive encoding
            // here to encode spaces to '%' forms
            return URLEncoder.encode(text, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Encoding unexpectedly failed.", e);
        }
    }

} // (class)
