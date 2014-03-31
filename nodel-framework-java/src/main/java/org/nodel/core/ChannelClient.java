package org.nodel.core;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nodel.Handler;
import org.nodel.SimpleName;
import org.nodel.Tuple;
import org.nodel.reflection.Serialisation;
import org.nodel.reflection.Value;
import org.nodel.threading.ThreadPool;
import org.nodel.threading.TimerTask;
import org.nodel.threading.Timers;

/**
 * Manages a channel client, including connection, etc.
 */
public abstract class ChannelClient {
    
    /**
     * (logging related)
     */
    private static AtomicLong s_instance = new AtomicLong();
    
    protected static ThreadPool s_threadPool = new ThreadPool("channel_client", 128);
    
    protected static Timers s_timerThread = new Timers("channel_client");
    
    /**
     * (logging related)
     */
    @Value
    protected long _instance = s_instance.getAndIncrement();

    /**
     * (logging related)
     */
    protected Logger _logger = LogManager.getLogger(String.format("%s.%s_%d", ChannelClient.class.getName(), this.getClass().getSimpleName(), _instance));
    
    /**
     * Instance signal / lock.
     */
    protected Object _signal = new Object();    
    
    /**
     * Whether enabled or not.
     */
    @Value(name = "enabled")
    protected boolean _enabled = true;
    
    /**
     * The address this channel client is responsible for.
     */
    @Value(name = "address")
    protected NodeAddress _address;
    
    /**
     * Is used to check wiring every 45s or so.
     */
    private static long TIMER_INTERVAL = 45000; 
    
    public interface ChannelEventHandler {
        
        public void handle(NodelPoint point, Object arg);
        
    }
    
    /**
     * (used within 'eventHandlers')
     */
    private class EventHandlersEntry {
        
        /**
         * (will never be null)
         */
        public NodelPoint key;
        
        /**
         * (will never be null)
         */
        List<ChannelEventHandler> handlers = new ArrayList<ChannelEventHandler>();
        
        @Value
        public boolean beenRegistered = false;
        
        public EventHandlersEntry(NodelPoint key) {
            this.key = key;
        }
        
        public void addHandler(ChannelEventHandler handler) {
            this.handlers.add(handler);
        }
        
        @Override
        public String toString() {
            return Serialisation.serialise(this);
        }
        
    } // (class)
    
    /**
     * Holds the registered event handlers.
     */
    @Value
    private Map<NodelPoint, EventHandlersEntry> eventHandlers = new HashMap<NodelPoint, EventHandlersEntry>();
    
    private class ActionPointEntry {
        
        /**
         * (will never be null)
         */
        public NodelPoint point;
        
        public boolean beenRegistered = false;
        
        public ActionPointEntry(NodelPoint point) {
            this.point = point;
        }
        
        @Override
        public String toString() {
            return Serialisation.serialise(this);
        }
        
    }
    
    /**
     * Holds the registered action interests.
     */
    private Map<NodelPoint, ActionPointEntry> _actionPoints = new HashMap<NodelPoint, ActionPointEntry>();
    
    /**
     * When the channel is connected.
     * (locked around 'signal')
     */
    private Handler.H0 _connectedHandler = null;
    
    /**
     * When wiring has been a confirmed success
     */
    private Handler.H3<SimpleName, Set<SimpleName>, Set<SimpleName>> _wiringSuccessHandler = null;    
    
    /**
     * When a major connection fault is detected.
     */
    private Handler.H1<Exception> _connectionFaultHandler;     
    
    /**
     * When a "wiring-fault" is detected i.e. action or event missing at server.
     */
    private Handler.H3<SimpleName, Set<SimpleName>, Set<SimpleName>> _wiringFaultHandler = null;
    
    /**
     * (used in 'wiringPointsByNode')
     */
    private class WiringPointEntry {
        
        /**
         * The time this entry was created.
         * (using 'System.nanoTime')
         */
        public long created;
        
        /**
         * (will never be null)
         */
        public SimpleName node;
        
        public Set<SimpleName> events = new HashSet<SimpleName>();
        
        public Set<SimpleName> actions = new HashSet<SimpleName>();
        
        public WiringPointEntry(SimpleName node) {
            this.node = node;
            this.created = System.nanoTime();
        }
        
        @Override
        public String toString() {
            return "[" + this.node + ", events:" + this.events + ", actions:" + this.actions + "]";
        }
        
    } // (class)
    
    /**
     * Holds the possible wiring points by node
     */
    @Value
    private Map<SimpleName, WiringPointEntry> _wiringPointsByNode = new HashMap<SimpleName, WiringPointEntry>();    
    
    /**
     * Creates a new channel client which is responsible for connection and reconnection.
     * (does not block)
     */
    public ChannelClient(NodeAddress address) {
        _address = address;
        
        _logger.info("ChannelClient started. address='" + address + "'");
        
        s_timerThread.schedule(new TimerTask() {
            
            @Override
            public void run() {
                timerMain();
            }
            
        }, TIMER_INTERVAL);
    } // (constructor)

    /**
     * (timer entry-point)
     */
    protected void timerMain() {
        if (this._enabled) {
            try {
                performWiringCheck();
            } finally {
                s_timerThread.schedule(new TimerTask() {
                    
                    @Override
                    public void run() {
                        timerMain();
                    }
                    
                }, TIMER_INTERVAL);
            }
        }
    } // (method)

    /**
     * This is an important routine that does a general consistency check.
     * 
     * 1) checks whether nodes have moved
     * 2) ...
     * (timer entry-point)
     */
    private void performWiringCheck() {
        // need this list to avoid threading and map concurrency issues
        List<Tuple.T3<SimpleName, Set<SimpleName>, Set<SimpleName>>> faultsToDealWith = new ArrayList<Tuple.T3<SimpleName, Set<SimpleName>, Set<SimpleName>>>();
        
        synchronized (_signal) {
            _logger.entry();
            
            for(SimpleName node : _wiringPointsByNode.keySet()) {
                WiringPointEntry entry = _wiringPointsByNode.get(node);
                
                // only deal with established records
                long age = (System.nanoTime() - entry.created) / 1000000; 
                
                if (age < TIMER_INTERVAL)
                    continue;
                
                if (entry.actions.isEmpty() && entry.events.isEmpty()) {
                    // node does not live there
                    Set<SimpleName> empty = Collections.emptySet();
                    faultsToDealWith.add(new Tuple.T3<SimpleName, Set<SimpleName>, Set<SimpleName>>(node, empty, empty));
                } else {
                    // check for missing wiring points
                    
                    Set<SimpleName> missingEvents = new HashSet<SimpleName>();
                    Set<SimpleName> missingActions = new HashSet<SimpleName>();
                    
                    // ensure events are present
                    for (NodelPoint nodelPoint : this.eventHandlers.keySet()) {
                        if (!entry.events.contains(nodelPoint.getPoint()))
                            missingEvents.add(nodelPoint.getPoint());
                    }
                    
                    // ensure actions are present
                    for (NodelPoint nodelPoint : _actionPoints.keySet()) {
                        if (!entry.actions.contains(nodelPoint.getPoint()))
                            missingActions.add(nodelPoint.getPoint());
                    }
                    
                    if (missingEvents.size() > 0 || missingActions.size() > 0)
                        faultsToDealWith.add(new Tuple.T3<SimpleName, Set<SimpleName>, Set<SimpleName>>(node, missingActions, missingEvents));
                }
                
            } // (for)
        }
        
        for (Tuple.T3<SimpleName, Set<SimpleName>, Set<SimpleName>> action : faultsToDealWith)
            onWiringFault(action.getItem1(), action.getItem2(), action.getItem3());
    } // (for)

    /**
     * Gets the address this channel was responsible for.
     */
    public NodeAddress getAddress() {
        return _address;
    }
    
    /**
     * Starts the channel client. Should only be called after all event handlers are attached.
     */
    protected abstract void start();
    
    /**
     * Registers new interest in a Node point i.e. wires up a handler.
     */
    public void registerEventInterest(NodelPoint eventPoint, ChannelEventHandler handler) {
        synchronized (_signal) {
            EventHandlersEntry eventHandlersEntry = this.eventHandlers.get(eventPoint);
            if (eventHandlersEntry == null) {
                eventHandlersEntry = new EventHandlersEntry(eventPoint);
                this.eventHandlers.put(eventPoint, eventHandlersEntry);
            }

            eventHandlersEntry.addHandler(handler);
            _logger.info("Registering interest in point {} ({} handlers)", eventPoint, eventHandlersEntry.handlers.size());
            
            SimpleName node = eventPoint.getNode();
            
            // set up the wiring points record if necessary
            if (!_wiringPointsByNode.containsKey(node))
                _wiringPointsByNode.put(node, new WiringPointEntry(node));

            if (this.isConnected())
                syncActionAndEventHandlerTable();
        }
    } // (method)
    
    /**
     * Registers an interest in an Action. 
     */
    public void registerActionInterest(NodelPoint actionPoint) {
        synchronized(_signal) {
            _logger.entry();
            
            if (_actionPoints.containsKey(actionPoint)) {
                // only needs to have been registered once
                return;
            }
            
            _actionPoints.put(actionPoint, new ActionPointEntry(actionPoint));
            
            SimpleName node = actionPoint.getNode();
            
            // set up the wiring points record if necessary
            if (!_wiringPointsByNode.containsKey(node))
                _wiringPointsByNode.put(node, new WiringPointEntry(node));
            
            if (this.isConnected())
                syncActionAndEventHandlerTable();
        }
    } // (method)
    
    /**
     * Goes through all the data-structures removing references to the given
     * node.
     */
    public void removeNode(SimpleName node) {
        synchronized (_signal) {
            _logger.info("Removing node {}...", node);
            
            List<NodelPoint> toRemove = new ArrayList<NodelPoint>();

            // actions
            for (NodelPoint actionPoint : _actionPoints.keySet()) {
                if (actionPoint.getNode().equals(node)) {
                    toRemove.add(actionPoint);
                }
            } // (for)
            
            for(NodelPoint point : toRemove)
                _actionPoints.remove(point);
            
            toRemove.clear();
            
            // events
            for (NodelPoint eventPoint : this.eventHandlers.keySet()) {
                if (eventPoint.getNode().equals(node)) {
                    toRemove.add(eventPoint);
                }
            } // (for)
            
            for(NodelPoint point : toRemove)
                this.eventHandlers.remove(point);
            
            // wiring point entry
            _wiringPointsByNode.remove(node);
        }
    } // (method)
    
    /**
     * When the channel first connects.
     */
    protected void onConnected() {
        _logger.entry();
        
        Handler.handle(_connectedHandler);
    } // (method)
    
    /**
     * When a permanent channel fault occurs.
     */
    private void onWiringSuccess(final SimpleName node, final Set<SimpleName> actions, final Set<SimpleName> events) {
        _logger.entry();
        
        Handler.handle(_wiringSuccessHandler, node, actions, events);
    } // (method)    
    
    /**
     * When a permanent channel fault occurs.
     * (assumes locked)
     */
    protected void onConnectionFault(final Exception exc) {
        _logger.entry();
        
        Handler.handle(_connectionFaultHandler, exc);
    } // (method)
    
    /**
     * When a permanent channel fault occurs.
     */
    private void onWiringFault(final SimpleName node, final Set<SimpleName> missingActions, final Set<SimpleName> missingEvents) {
        _logger.entry();
        
        Handler.handle(_wiringFaultHandler, node, missingActions, missingEvents);
    } // (method)
    
    /**
     * Synchronises the event handler table with the channel server.
     * (assumes locked)
     */
    protected void syncActionAndEventHandlerTable() {
        Map<SimpleName, List<SimpleName>> eventListByNode = new HashMap<SimpleName, List<SimpleName>>();

        // go through the list of event points
        for (NodelPoint connectionPoint : this.eventHandlers.keySet()) {
            EventHandlersEntry eventHandlerEntry = this.eventHandlers.get(connectionPoint);

            if (eventHandlerEntry.beenRegistered)
                continue;

            List<SimpleName> eventsList = eventListByNode.get(eventHandlerEntry.key.getNode());
            if (eventsList == null) {
                eventsList = new ArrayList<SimpleName>();
                eventListByNode.put(eventHandlerEntry.key.getNode(), eventsList);
            }
            eventsList.add(eventHandlerEntry.key.getPoint());

            // only do this once
            eventHandlerEntry.beenRegistered = true;
        } // (for)
        
        // go through the list of action points
        Map<SimpleName, List<SimpleName>> actionListByNode = new HashMap<SimpleName, List<SimpleName>>();
        
        for (NodelPoint point : _actionPoints.keySet()) {
            ActionPointEntry actionEntry = _actionPoints.get(point);
            
            if (actionEntry.beenRegistered)
                continue;
            
            List<SimpleName> actionList = actionListByNode.get(actionEntry.point.getNode());
            if (actionList == null) {
                actionList = new ArrayList<SimpleName>();
                actionListByNode.put(actionEntry.point.getNode(), actionList);
            }
            actionList.add(actionEntry.point.getPoint());
            
            // only do this once
            actionEntry.beenRegistered = true;
        } // (for)
        
        HashSet<SimpleName> processedNodes = new HashSet<SimpleName>();

        // send an the interests message for each node
        for (SimpleName node : eventListByNode.keySet()) {
            List<SimpleName> events = eventListByNode.get(node);
            List<SimpleName> actions = actionListByNode.get(node);
            
            sendInterestMessage(node, events, actions);
            
            // make sure it's only done once per node
            processedNodes.add(node);
        } // (for)
        
        for  (SimpleName node : actionListByNode.keySet()) {
            if (processedNodes.contains(node))
                continue;
            
            List<SimpleName> events = eventListByNode.get(node);
            List<SimpleName> actions = actionListByNode.get(node);
            
            sendInterestMessage(node, events, actions);
        } // (for)
        
    } // (method)
    
    /**
     * Instantaneous check whether the channel is connected or not.
     */
    public abstract boolean isConnected();
    
    /**
     * Attaches a handler that is called when client 'connects', not necessarily all wired up though.
     * (unicast delegate)
     * (delegate must not block)
     * 
     * @param handler 'null' to clear otherwise 
     */
    public void attachConnectedHandler(Handler.H0 handler) {
        synchronized (_signal) {
            _logger.entry();
            
            if (_connectedHandler != null && handler != null)
                throw new IllegalArgumentException("Handler is already set; must be cleared first using 'null'.");

            _connectedHandler = handler;
        }
    } // (method)
    
    /**
     * Attaches a handler that is called when wiring has been confirmed across the channel.
     */
    public void attachWiringSuccessHandler(Handler.H3<SimpleName, Set<SimpleName>, Set<SimpleName>> handler) {
        synchronized (_signal) {
            _logger.entry();
            
            if (_wiringSuccessHandler != null && handler != null)
                throw new IllegalArgumentException("Handler is already set; must be cleared first using 'null'.");

            _wiringSuccessHandler = handler;
        }
    } // (method)    
    
    /**
     * Attaches a handler that is called when a connection fault is detected, 
     * i.e. when no connection can be established or the connection is brought down.
     */
    public void attachConnectionFaultHandler(Handler.H1<Exception> handler) {
        synchronized (_signal) {
            _logger.entry();
            
            if (_connectionFaultHandler != null && handler != null)
                throw new IllegalArgumentException("Handler is already set; must be cleared first using 'null'.");

            _connectionFaultHandler = handler;
        }
    } // (method)
    
    /**
     * Attaches a handler that is called when a "wiring" fault is detected, 
     * i.e. when there's interest in an event but it doesn't exist on the other side.
     */
    public void attachWiringFaultHandler(Handler.H3<SimpleName, Set<SimpleName>, Set<SimpleName>> handler) {
        synchronized (_signal) {
            _logger.entry();
            
            if (_wiringFaultHandler != null && handler != null)
                throw new IllegalArgumentException("Handler is already set; must be cleared first using 'null'.");

            _wiringFaultHandler = handler;
        }
    } // (method)

    /**
     * Handles in incoming Channel packet.
     */
    protected void handleMessage(final ChannelMessage message) {
            _logger.entry();
            
            _logger.info("Client: message arrived: " + message);
            
            // received an 'event'
            if (message.node != null && message.event != null) {
                // determine connection point source
                NodelPoint point = NodelPoint.create(message.node, message.event);
                
                handleIncomingEvent(message, point);
            }
            
            // response to an 'interest' request
            else if (message.node != null && (message.events != null || message.actions != null)) {
                handleInterestResponse(message.node, message.events, message.actions);
            }
            
            // response to an 'moved' announcement
            else if (message.node != null && message.announcement != null && message.announcement.equals(ChannelMessage.Announcement.Moved)) {
                // handleMovedAnnouncement(message.node);
            }
    } // (method)

    /**
     * When a server node generates an 'event'.
     */
    private void handleIncomingEvent(ChannelMessage message, NodelPoint point) {
        synchronized (_signal) {
            final EventHandlersEntry eventHandlersEntry = this.eventHandlers.get(point);
            if (eventHandlersEntry == null)
                return;

            final NodelPoint entryKey = eventHandlersEntry.key;
            final Object messageArg = message.arg;

            for (final ChannelEventHandler handler : eventHandlersEntry.handlers) {
                s_threadPool.execute(new Runnable() {

                    @Override
                    public void run() {
                        handler.handle(entryKey, messageArg);
                    }

                });

            } // (for)
        }
    } // (method)
    
    /**
     * When a server node indicates the events and actions being listened to. Keeps
     * track of the reply and triggers a wiring-fault event if it detects a missing
     * action or events.
     */
    private void handleInterestResponse(String source, String[] events, String[] actions) {
        SimpleName node = new SimpleName(source);
        
        // these need to be captured to accommodate threading model
        Set<SimpleName> eventsSet = new HashSet<SimpleName>();
        Set<SimpleName> actionsSet = new HashSet<SimpleName>();

        synchronized (_signal) {
            WiringPointEntry wiringPointEntry = _wiringPointsByNode.get(node);
            if (wiringPointEntry == null) {
                wiringPointEntry = new WiringPointEntry(node);
                _wiringPointsByNode.put(node, wiringPointEntry);
            }

            // clear what's already there
            wiringPointEntry.actions.clear();
            wiringPointEntry.events.clear();
            
            for (String actionName : actions) {
                SimpleName action = new SimpleName(actionName);
                wiringPointEntry.actions.add(action);
                actionsSet.add(action);
            }            

            for (String eventName : events) {
                SimpleName event = new SimpleName(eventName);
                wiringPointEntry.events.add(event);
                eventsSet.add(event);
            }
        }
        
        // notify of wiring status
        if (actionsSet.isEmpty() && eventsSet.isEmpty())
            return;

        onWiringSuccess(node, actionsSet, eventsSet);
        
        // will leave the timer to check for wiring faults in the background
    } // (method)

    /**
     * Sends a 'call' (invoke) message down the channel.
     */
    protected void sendCallMessage(SimpleName node, SimpleName action, Object arg) {
        ChannelMessage message = new ChannelMessage();
        
        message.node = node.getReducedName();
        message.action = action.getReducedName();
        message.arg = arg;
        
        sendMessage(message);
    } // (method)
    
    /**
     * Sends the 'interests' message. 
     */
    private void sendInterestMessage(SimpleName node, List<SimpleName> events, List<SimpleName> actions) {
        ChannelMessage message = new ChannelMessage();

        message.node = node.getReducedName();
        
        if (events != null)
            message.events = SimpleName.intoReduced(events);
        
        if (actions != null)
            message.actions = SimpleName.intoReduced(actions);

        sendMessage(message);
    } // (method)

    /**
     * Asynchronously sends the message down the channel.
     */
    public abstract void sendMessage(ChannelMessage message);
    
    /**
     * Permanently closes this channel.
     */
    public void close() {
        synchronized (_signal) {
            _logger.entry();
            
            this._enabled = false;
        }        
    }

    @Override
    public String toString() {
        return Serialisation.serialise(this);
    }    

} // (class)
