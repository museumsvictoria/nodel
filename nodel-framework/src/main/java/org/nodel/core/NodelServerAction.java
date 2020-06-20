package org.nodel.core;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.io.Closeable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.joda.time.DateTime;
import org.nodel.Handler;
import org.nodel.SimpleName;
import org.nodel.Strings;
import org.nodel.Handler.H1;
import org.nodel.host.Binding;
import org.nodel.reflection.Param;
import org.nodel.reflection.Service;
import org.nodel.reflection.Value;
import org.nodel.threading.CallbackQueue;

public class NodelServerAction implements Closeable {
    
    protected SimpleName _node;

    protected SimpleName _action;
    
    private String _title;

    private String _desc;

    private String _group;
    
    private double _order;

    private String _caution;

    private Map<String, Object> _argSchema;
    
    private Map<String, Object> _fullSchema;
    
    /**
     * The last value (linked to 'seqNum')
     */
    private AtomicReference<Object> _argValue = new AtomicReference<Object>();
    
    /**
     * Holds an safely incrementing sequence number relating to the 
     * value of the argument (state)
     */
    private long _seqNum = 0;

    /**
     * Holds the latest timestamp.
     */
    private AtomicReference<DateTime> _timestamp = new AtomicReference<DateTime>();    
    
    @Service(name = "schema", title = "Schema", genericClassA = String.class, genericClassB = Object.class, desc = "Prepares a filtered schema for this action.")
    public Map<String, Object> getFullSchema() {
        return _fullSchema;
    }

    protected NodelPoint _actionPoint;

    /**
     * To match threading of wild environment.
     */
    private CallbackQueue _callbackQueue;
    
    /**
     * To match threading of wild environment.
     */
    private Handler.H0 _threadStateHandler;
    
    /**
     * To match threading of wild environment.
     */
    private H1<Exception> _exceptionHandler;
    
    /**
     * For other in-process 'call' handlers that may be interested in this action.
     */
    private AtomicReference<Handler.H1<Object>[]> _callHandlers = new AtomicReference<Handler.H1<Object>[]>();
    
    /**
     * Allow a call filter which can trap and alter the value of the arg
     */
    private Handler.F1<Object, Object> _callFilter = null;

    private boolean _closed;

    public NodelServerAction(String node, String action, Binding metadata) {
        if (Strings.isBlank(node) || Strings.isBlank(action))
            throw new IllegalArgumentException("Names cannot be null or empty.");
        
        init(new SimpleName(node), new SimpleName(action), metadata);
    }
    
    public NodelServerAction(SimpleName node, SimpleName action, Binding metadata) {
        if (node == null || action == null)
            throw new IllegalArgumentException("Names cannot null.");
        
        init(node, action, metadata);
    }
    
    private void init(SimpleName node, SimpleName action, Binding metadata) {
        _node = node;
        _action = action;
        _actionPoint = NodelPoint.create(node, action);
        if (metadata == null)
            metadata = Binding.Blank;
        
        _title = metadata.title;
        _desc = metadata.desc;
        _group = metadata.group;
        _caution = metadata.caution;
        _order = metadata.order;
        _argSchema = metadata.schema;
        
        _fullSchema = prepareFullSchema();        
    }
    
    /**
     * Sets fields which control the threading environment.
     */
    public void setThreadingEnvironment(CallbackQueue callbackQueue, Handler.H0 threadStateHandler, Handler.H1<Exception> exceptionHandler) {
        _callbackQueue = callbackQueue;
        _threadStateHandler = threadStateHandler;
        _exceptionHandler = exceptionHandler;
    }    
    
    private Map<String, Object> prepareFullSchema() {
        Map<String, Object> schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        schema.put("properties", properties);

        Map<String, Object> argSchema;

        if (_argSchema == null) {
            argSchema = new LinkedHashMap<String, Object>();
            argSchema.put("type", "null");
        } else {
            argSchema = _argSchema;
        }

        properties.put("arg", argSchema);
        
        return schema;
    }
    
    /**
     * Gets the node.
     */
    public SimpleName getNode() {
        return _node;
    }
    
    /**
     * Gets the action.
     */
    @Value(name = "name", title = "Name", desc = "A simple name, alias or ID.")    
    public SimpleName getAction() {
        return _action;
    }
    
    /**
     * The action schema
     */
    @Value(name = "schema", title = "Schema", genericClassA = String.class, genericClassB = Object.class, desc = "The raw stand-alone schema.")
    public Map<String, Object> getSchema() {
        return _argSchema;
    }
    
    /**
     * The composite Nodel point.
     */
    public NodelPoint getNodelPoint() {
        return _actionPoint;
    }
    
    @Value(name = "title", title = "Title", desc = "A short title.")    
    public String getTitle() {
        return _title;
    }
    
    @Value(name = "desc", title = "Description", desc = "A short description.")
    public String getDesc() {
        return _desc;
    }

    @Value(name = "group", title = "Group", desc = "A group name.")
    public String getGroup() {
        return _group;
    }
    
    @Value(name = "order", title = "Order", desc = "Relative order.")
    public double getOrder() {
        return _order;
    }    
    
    @Value(name = "caution", title = "Caution", desc = "A caution message associated with this action.")
    public String getCaution() {
        return _caution;
    }
    
    public DateTime getTimestamp() {
        return _timestamp.get();
    }     
    
    @Value(name = "seq", title = "Sequence number")
    public long getSeqNum() {
        return _seqNum;
    }
    
    @Value(name = "arg", title = "Argument value")
    public Object getArg() {
        return _argValue.get();
    }
    
    /**
     * Gets request handling object.
     */
    public ActionRequestHandler getHandler() {
        return _handlerWithExtraCallHandlers;
    }    
    
    /**
     * Holds the original handler (minus the interception logic)
     */
    private ActionRequestHandler _handler;
    
    /**
     * Registers an action.
     */
    public void registerAction(final ActionRequestHandler handler) {
        if (handler == null)
            throw new IllegalArgumentException();
        
        _handler = handler;
        
        // 'handlerWithExtraCallHandlers' is used as main callback
        
        NodelServers.instance().registerAction(this);
    } // (method)
    
    /**
     * Internal framework use to trigger the handling of the action request.
     */
    protected void handleActionRequest(Object arg) {
        _handlerWithExtraCallHandlers.handleActionRequest(arg);        
    }
    
    /**
     * Holds the interception logic and processes additional call handlers
     */
    private ActionRequestHandler _handlerWithExtraCallHandlers = new ActionRequestHandler() {
        
        @Override
        public void handleActionRequest(Object arg) {
            if (_callFilter != null) {
                // arg = _emitFilter.handle(arg);
                if (_callbackQueue != null) {
                    try {
                        arg = _callbackQueue.handle(_callFilter, arg);
                    } catch (Exception e) {
                        // handle gracefully ...
                        _exceptionHandler.handle(e);
                        // ... and keep going with arg regardless
                    }
                } else {
                    try {
                        arg = _callFilter.handle(arg);
                    } catch (Exception exc) {
                        throw new RuntimeException("Emit filter", exc);
                    }
                }
            }
            
            final Object finalArg = arg;
            
            _argValue.set(arg);
            _timestamp.set(DateTime.now());
            
            // seq must be set last
            _seqNum = Nodel.getNextSeq();
            
            _handler.handleActionRequest(finalArg);
            
            // snap-shot of handlers
            final H1<Object>[] handlers = _callHandlers.get();
            
            // if there are some handlers, use the Channel Client thread-pool (treat as though remote events)
            if (handlers != null) {
                ChannelClient.getThreadPool().execute(new Runnable() {
                    
                    @Override
                    public void run() {
                        // set up thread state
                        if (_threadStateHandler != null)
                            _threadStateHandler.handle();
                        
                        Exception lastExc = null;
                        
                        // call handlers one after the other
                        for (Handler.H1<Object> handler : handlers) {
                            if (_callbackQueue != null)
                                _callbackQueue.handle(handler, finalArg, _exceptionHandler);
                            else {
                                try {
                                    Handler.tryHandle(handler, finalArg);
                                } catch (Exception exc) {
                                    lastExc = exc;
                                }
                            }
                        } // (for)
                        
                        if (lastExc != null) {
                            // let the thread-pool exception handler deal with it
                            throw new RuntimeException("Action call handler", lastExc);
                        }
                    }
                    
                });
            }
        }
        
    };

    /**
     * (without any argument)
     */
    public void call() {
        call(null);
    }
    
    @Service(name = "call", title = "Call", desc = "Invokes this action.")
    public void call(@Param(name = "arg", title = "Argument") final Object arg) {
        if (_handler == null)
            return; // gracefully ignore
            
        _handlerWithExtraCallHandlers.handleActionRequest(arg);
    }
    
    /**
     * Attaches an in-process call handler (will arrive on same thread as .call() call)
     */
    @SuppressWarnings("unchecked")
    public void addCallHandler(Handler.H1<Object> handler) {
        if (handler == null)
            throw new IllegalArgumentException("No call handler given.");
        
        H1<Object>[] current = _callHandlers.get();

        // grow array 'lock-lessly'
        for (;;) {
            H1<Object>[] value;
            if (current == null) {
                value = (H1<Object>[]) new Handler.H1<?>[] { handler };
            } else {
                // make space for one more
                int currentSize = current.length;
                value = (H1<Object>[]) new Handler.H1<?>[currentSize + 1];

                // copy all and set the last one
                System.arraycopy(current, 0, value, 0, currentSize);
                value[current.length] = handler;
            }
            
            if(_callHandlers.compareAndSet(current, value))
                break;
            
            // otherwise keep trying
        }
    }
    
    /**
     * Filtering the arg emitted 
     */
    public void addCallFilter(Handler.F1<Object, Object> filter) {
        _callFilter = filter;
    }    

    /**
     * Releases this action from the Nodel framework.
     */
    public void close() {
        if (_closed)
            return;
        
        _closed = true;
        
        NodelServers.instance().unregisterAction(this);
    }
    
} // (class)
