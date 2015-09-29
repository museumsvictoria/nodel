package org.nodel.core;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.nodel.Handler;
import org.nodel.SimpleName;
import org.nodel.threading.ThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for class acting as a Nodel channel server.
 */
public abstract class ChannelServer {
    
    /**
     * (logging)
     */
    private static AtomicLong s_instanceCounter = new AtomicLong();
    
    /**
     * (threading)
     */
    private static ThreadPool s_threadPool = new ThreadPool("Nodel channel-servers", 128);

    /**
     * (logging)
     */
    protected long _instance = s_instanceCounter.getAndIncrement();

    /**
     * (logging)
     */
    protected Logger _logger = LoggerFactory.getLogger(String.format("%s.%s_%d", ChannelServer.class.getName(), this.getClass().getSimpleName(), _instance));

    /**
     * Instance signal / lock.
     */
    protected Object _signal = new Object();

    /**
     * Can only be enabled once. (not thread safe)
     */
    protected boolean _enabled = false;

    /**
     * Delegate to call when a crippling failure occurs.
     */
    protected Handler.H1<Throwable> _onFailure;

    /**
     * The nodel server used by this channel server.
     */
    private NodelServers _nodelServer;

    /**
     * Holds the events filter list.
     */
    private Map<SimpleName, List<String>> _eventFiltersByNode = new HashMap<SimpleName, List<String>>();

    /**
     * Holds the action filter list.
     */
    private Map<SimpleName, List<String>> _actionFiltersByNode = new HashMap<SimpleName, List<String>>();

    public ChannelServer(NodelServers nodelServer) {
        _nodelServer = nodelServer;
    }

    /**
     * Attaches / detaches the failure handler. 
     * (unicast delegate, delegate must not block)
     * 
     * @param handler null to clear.
     */
    public void attachFailureHandler(Handler.H1<Throwable> handler) {
        synchronized (_signal) {
            if (_onFailure != null && handler != null)
                throw new IllegalArgumentException("Handler is already set; must be cleared first using 'null'.");

            _onFailure = handler;
        }
    } // (method)

    /**
     * Sends a message down the channel. (exception free, non-blocking)
     */
    protected abstract void sendMessage(ChannelMessage message);

    /**
     * Sends an event message down the channel, applying any 'interest'
     * filtering. (exception free, non-blocking)
     */
    protected void sendEventMessage(String nodeName, String originalEvent, Object arg) {
        SimpleName node = new SimpleName(nodeName);
        String reducedEvent = Nodel.reduceToLower(originalEvent);

        synchronized (_signal) {
            List<String> eventFilters = _eventFiltersByNode.get(node);
            if (eventFilters == null)
                return;
            
            // find the first matching event filter and use it
            boolean found = false;
            for (String eventFilter : eventFilters) {
                if (Nodel.filterMatch(reducedEvent, eventFilter)) {
                    found = true;
                    break;
                }
            } // (for)

            if (!found)
                return;
        }

        ChannelMessage message = new ChannelMessage();
        message.node = nodeName;
        message.event = originalEvent;
        message.arg = arg;

        sendMessage(message);
    } // (method)
    
    /**
     * (RESERVED)
     */
    protected void sendMovedMessage(SimpleName node) {
        ChannelMessage message = new ChannelMessage();
        message.node = node.getReducedName();
        message.announcement = ChannelMessage.Announcement.Moved;

        sendMessage(message);
    } // (method)

    /**
     * Sends a response to an "interests" request. (exception free,
     * non-blocking)
     */
    protected void sendInterestsResponse(String nodeName, String[] actions, String[] events) {
        ChannelMessage response = new ChannelMessage();
        response.node = nodeName;
        response.events = events;
        response.actions = actions;

        sendMessage(response);
    } // (method)

    /**
     * Sends a response to an "invoke" request. (exception free, non-blocking)
     */
    private void sendInvokeResponseLookupFailure(String nodeName, String action) {
        ChannelMessage response = new ChannelMessage();
        response.node = nodeName;
        response.error = "Action not found";
        response.action = action;

        sendMessage(response);
    } // (method)

    /**
     * Starts processing. (may briefly block)
     */
    public abstract void start();

    /**
     * Processes incoming messages.
     */
    protected void handleMessage(final ChannelMessage message) {
        _logger.info("Server: message arrived: " + message);

        // 'interests' request
        if (message.node != null && (message.events != null || message.actions != null)) {
            SimpleName node = new SimpleName(message.node);

            // register interest in the node
            _nodelServer.registerInterest(this, message.node);

            synchronized (_signal) {
                // go through events
                if (message.events != null) {
                    for (String event : message.events)
                        doAddEventFilter(node, event);
                }

                // go through actions
                if (message.actions != null) {
                    for (String action : message.actions)
                        doAddActionFilter(node, action);
                }
            }

            // determine all interests that have been matched
            SimpleName[] allEvents = _nodelServer.getRegisteredEvents(node);
            SimpleName[] allActions = _nodelServer.getRegisteredActions(node);

            // filter out the events and actions

            List<SimpleName> matchedEvents;
            List<SimpleName> matchedActions;
            
            synchronized (_signal) {
                matchedEvents = new ArrayList<SimpleName>();
                for (SimpleName event : allEvents) {
                    List<String> eventFilters = _eventFiltersByNode.get(node);
                    if (eventFilters != null && containsMatchingFilter(node, event.getReducedForMatchingName(), eventFilters))
                        matchedEvents.add(event);
                }

                matchedActions = new ArrayList<SimpleName>();
                for (SimpleName action : allActions) {
                    List<String> actionFilters = _actionFiltersByNode.get(node);
                    if (actionFilters != null && containsMatchingFilter(node, action.getReducedForMatchingName(), actionFilters))
                        matchedActions.add(action);
                }
            }

            // respond
            sendInterestsResponse(message.node, SimpleName.intoOriginals(matchedActions), SimpleName.intoOriginals(matchedEvents));
            return;
        }

        // 'invoke' request
        if (message.node != null && message.action != null) {
            final NodelServerAction handler = _nodelServer.getActionRequestHandler(message.node, message.action);

            if (handler == null) {
                sendInvokeResponseLookupFailure(message.node, message.action);
                return;
            }

            // invoke on a separate thread
            s_threadPool.execute(new Runnable() {

                @Override
                public void run() {
                    try {
                        // call the action
                    	handler._handler.handleActionRequest(message.arg);

                    } catch (Exception exc) {
                        // ignore exception
                    }
                }

            });

            return;
        }
    } // (method)

    /**
     * (assumes locked)
     */
    private void doAddEventFilter(SimpleName node, String eventFilter) {
        String reducedEvent = Nodel.reduceFilter(eventFilter);

        List<String> eventsList = _eventFiltersByNode.get(node);
        if (eventsList == null) {
            eventsList = new ArrayList<String>();
            _eventFiltersByNode.put(node, eventsList);
        }

        if (!eventsList.contains(reducedEvent))
            eventsList.add(reducedEvent);
    } // (method)

    /**
     * (assumes locked)
     */
    private void doAddActionFilter(SimpleName node, String actionFilter) {
        String reducedAction = Nodel.reduceFilter(actionFilter);

        List<String> actionsList = _actionFiltersByNode.get(node);
        if (actionsList == null) {
            actionsList = new ArrayList<String>();
            _actionFiltersByNode.put(node, actionsList);
        }

        if (!actionsList.contains(reducedAction))
            actionsList.add(reducedAction);
    } // (method)

    /**
     * (args prechecked)
     */
    private boolean containsMatchingFilter(SimpleName node, String value, List<String> filters) {
        // find the first matching event filter
        for (String eventFilter : filters) {
            if (Nodel.filterMatch(value, eventFilter))
                return true;
        } // (for)

        return false;
    } // (method)

    /**
     * When a serious permanent failure occurs.
     */
    protected void handleFailure(Exception exc) {
        // complete clean up

        if (_onFailure != null)
            _onFailure.handle(exc);
    }

} // (class)
