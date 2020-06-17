package org.nodel.core;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.io.Closeable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.joda.time.DateTime;
import org.nodel.Handler;
import org.nodel.Handler.H1;
import org.nodel.LockFreeList;
import org.nodel.Random;
import org.nodel.SimpleName;
import org.nodel.Strings;
import org.nodel.host.Binding;
import org.nodel.reflection.Objects;
import org.nodel.reflection.Param;
import org.nodel.reflection.Service;
import org.nodel.reflection.Value;
import org.nodel.threading.CallbackQueue;
import org.nodel.threading.ThreadPool;
import org.nodel.threading.TimerTask;
import org.nodel.threading.Timers;

public class NodelServerEvent implements Closeable {
    
    /**
     * (background timers)
     */
    private static Timers s_timers = new Timers("_NodelServerEvent");
    
    protected SimpleName _node;
    
    protected SimpleName _event;
    
    protected NodelPoint _eventPoint;
    
    protected ActionRequestHandler handler;
    
    private boolean _closed;

    /**
     * For monitoring purposes.
     */
    private Handler.H2<DateTime, Object> _monitor;
    
    /**
     * For other in-process 'emit' handlers that may be interested in this event.
     */
    private LockFreeList<Handler.H1<Object>> _emitHandlers = new LockFreeList<>();
    
    /**
     * Allow an emit filter which can trap and alter the value of the arg
     */
    private Handler.F1<Object, Object> _emitFilter = null;

    private String _title;

    private String _desc;

    private String _group;

    private String _caution;
    
    private double _order;
    
    private Map<String, Object> _argSchema;
    
    private Map<String, Object> _fullSchema;
    
    /**
     * The last snap shot of the argument (linked to 'seqNum')
     * (can never be 'null')
     */
    private AtomicReference<ArgInstance> _argInstance = new AtomicReference<ArgInstance>(ArgInstance.NULL);
    
    /**
     * Responsible for persisting the data.
     */
    private Handler.H1<ArgInstance> _persister;
    
    /**
     * The repeating timer to persist the data.
     */
    private TimerTask _persisterTimer;

    /**
     * The last persisted event.
     */
    private ArgInstance _persistedArg;

    /**
     * How often to persist the args.
     * (default 2 hours)
     */
    private long PERSIST_PERIOD = 2 * 3600 * 1000;
        
    @Service(name = "schema", title = "Schema", genericClassA = String.class, genericClassB = Object.class, desc = "Prepares a filtered schema for this action.")
    public Map<String, Object> getFullSchema() {
        return _fullSchema;
    }
    
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
    
    public NodelServerEvent(String node, String event, Binding metadata, boolean v) {
        if (Strings.isBlank(node) || Strings.isBlank(event))
            throw new IllegalArgumentException("Names cannot be null or empty.");
        
        init(new SimpleName(node), new SimpleName(event), metadata);  
    }
    
    public NodelServerEvent(SimpleName node, SimpleName event, Binding metadata) {
        init(node, event, metadata);  
    }
    
    private void init(SimpleName node, SimpleName event, Binding metadata) {
        _eventPoint = NodelPoint.create(node, event);
        
        _node = node;
        _event = event;
        
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
     * Sets fields which control the threading environment.
     */
    public void setThreadingEnvironment(CallbackQueue callbackQueue, Handler.H0 threadStateHandler, Handler.H1<Exception> exceptionHandler) {
        _callbackQueue = callbackQueue;
        _threadStateHandler = threadStateHandler;
        _exceptionHandler = exceptionHandler;
    }
    
    /**
     * Seeds the argument without raising the event itself.
     */
    public void seedAndPersist(ArgInstance instance, Handler.H1<ArgInstance> persister) {
        // use a safe NULL object
        if (instance == null)
            instance = ArgInstance.NULL;
            
        // attach the handler
        _persister = persister;
        
        _persistedArg = instance;

        if (instance != ArgInstance.NULL) {
            _argInstance.set(instance);

            // fire the monitor to indicate past occurrence
            if (_monitor != null)
                _monitor.handle(instance.timestamp, instance.arg);

            // ...but *don't* fire the event itself
        }
        
        // add an ongoing timer to persist (on background thread-pool)
        // (not critical, so default is every 2 hours. Persist will occur on close anyway.)
        _persisterTimer = s_timers.schedule(ThreadPool.background(), new TimerTask() {

            @Override
            public void run() {
                persistNow();
            }

        }, PERSIST_PERIOD + Random.shared().nextInt(60000), PERSIST_PERIOD + Random.shared().nextInt(60000));
    }
    
    @Value(name = "name", title = "Name", desc = "The name.", order = 1)
    public SimpleName getEvent() {
        return _event;
    }
    
    @Value(name = "title", title = "Title", desc = "A title.", order = 2)
    public String getTitle() {
        return _title;
    }
    
    @Value(name = "desc", title = "Description", desc = "A short description.", order = 3)
    public String getDesc() {
        return _desc;
    }
    
    @Value(name = "group", title = "Group", desc = "A group.", order = 4)
    public String getGroup() {
        return _group;
    }
    
    @Value(name = "caution", title = "Caution", desc = "A caution message associated with the generation of this event.", order = 5)
    public String getCaution() {
        return _caution;
    }
    
    @Value(name = "order", title = "Order", desc = "Relative order.", order = 6)
    public double getOrder() {
        return _order;
    }  
    
    @Value(name = "schema", title = "Schema", genericClassA = String.class, genericClassB = Object.class, desc = "The raw stand-alone schema.")
    public Map<String, Object> getArgSchema() {
        return _argSchema;
    }
    
    /**
     * Returns the current value of the event.
     */
    @Value(name = "arg", title = "Argument value")
    public Object getArg() {
        return _argInstance.get().arg;
    }

    @Value(name = "seq", title = "Sequence number")
    public long getSeqNum() {
        return _argInstance.get().seqNum;
    }

    @Value(name = "timestamp", title = "Timestamp")
    public DateTime getTimestamp() {
        return _argInstance.get().timestamp;
    }
    
    public NodelPoint getNodelPoint() {
        return _eventPoint;
    }
    
    public void registerEvent() {
        NodelServers.instance().registerEvent(this);
    }
    
    /**
     * Fires the event (with no argument)
     */
    public void emit() {
        doEmit(null);
    }
    
    /**
     * Only emits the event if new argument (state) is different from the previous one.
     */
    public void emitIfDifferent(Object arg) {
        ArgInstance previous = _argInstance.get();

        if (!Objects.sameValue(previous.arg, arg))
            doEmit(arg);
    }

    /**
     * Fires the event (with argument)
     */
    @Service(name = "emit", title = "Emit", desc = "Emits this event.")    
    public void emit(@Param(name="arg", title="Argument") Object arg) {
        doEmit(arg);
    }
    
    /**
     * Fires the event.
     */
    private void doEmit(Object arg) {
        DateTime now = DateTime.now();
        
        if (_emitFilter != null) {
            // arg = _emitFilter.handle(arg);
            if (_callbackQueue != null) {
                try {
                    arg = _callbackQueue.handle(_emitFilter, arg);
                } catch (Exception e) {
                    // handle gracefully ...
                    _exceptionHandler.handle(e);
                    // ... and keep going with arg regardless
                }
            } else {
                try {
                    arg = _emitFilter.handle(arg);
                } catch (Exception exc) {
                    throw new RuntimeException("Emit filter", exc);
                }
            }
        }
        
        ArgInstance argInstance = new ArgInstance();
        argInstance.timestamp = now;
        argInstance.arg = arg;
        argInstance.seqNum = Nodel.getNextSeq(); 
        
        _argInstance.set(argInstance);
        
        if (_monitor != null)
            _monitor.handle(now, arg);

        NodelServers.instance().emitEvent(this, arg);
        
        // snap-shot of handlers
        final List<Handler.H1<Object>> handlers = _emitHandlers.items();
        
        final Object finalArg = arg;
        
        // if there are some handlers, use the Channel Client thread-pool (treat as though remote events)
        if (handlers.size() > 0) {
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
                                Handler.handle(handler, finalArg);
                            } catch (Exception exc) {
                                lastExc = exc;
                            }
                        }
                    } // (for)
                    
                    if (lastExc != null) {
                        // let the thread-pool exception handler deal with it
                        throw new RuntimeException("Emit handler", lastExc);
                    }
                }

            });
        }
    }
    
    /**
     * Attaches an in-process emit handler (will arrive on same thread as .emit() call)
     */
    public void addEmitHandler(Handler.H1<Object> handler) {
        _emitHandlers.add(handler);
    }
    
    /**
     * Filtering the arg emitted 
     */
    public void addEmitFilter(Handler.F1<Object, Object> filter) {
        _emitFilter = filter;
    }

    /**
     * Attaches a monitor.
     */
    public void attachMonitor(Handler.H2<DateTime, Object> monitor) {
        if (monitor == null)
            throw new IllegalArgumentException("Cannot detach monitor.");
        
        _monitor = monitor;
    }
    
    /**
     * Handles a persist request via background timer or if overridden by the user.
     */
    public void persistNow() {
        // persist the data if the values are different.
        // Not bothering with comparing timestamp as that information is secondary.
        
        ArgInstance argInstance = _argInstance.get();

        // don't bother if there has been no activity
        if (argInstance.seqNum == 0)
            return;
        
        // don't bother if the values are the same
        if (_persistedArg != null && Objects.sameValue(_persistedArg.arg, argInstance))
            return;
        
        // otherwise, persist the argument state
        Handler.tryHandle(_persister, argInstance);        
    }

    /**
     * Releases this binding.
     */
    public void close() {
        if (_closed)
            return;

        _closed = true;

        if (_persisterTimer != null)
            _persisterTimer.cancel();
        
        _emitHandlers.clear();
        
        persistNow();

        NodelServers.instance().unregisterEvent(this);
    }
    
} // (class)
