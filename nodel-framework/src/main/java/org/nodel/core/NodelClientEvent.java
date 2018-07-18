package org.nodel.core;

import java.util.List;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.util.concurrent.atomic.AtomicReference;

import org.joda.time.DateTime;
import org.nodel.Handler;
import org.nodel.Handler.H1;
import org.nodel.LockFreeList;
import org.nodel.Random;
import org.nodel.SimpleName;
import org.nodel.host.Binding;
import org.nodel.reflection.Objects;
import org.nodel.reflection.Serialisation;
import org.nodel.reflection.Value;
import org.nodel.threading.CallbackQueue;
import org.nodel.threading.ThreadPool;
import org.nodel.threading.TimerTask;
import org.nodel.threading.Timers;

public class NodelClientEvent {
    
    private final static SimpleName UNBOUND = new SimpleName("unbound");
    
    /**
     * (background timers)
     */
    private static Timers s_timers = new Timers("_NodelClientEvent");
    
    /**
     * The name (or alias) of this client event.
     */
    private SimpleName _name;
    
    /**
     * To match threading of wild environment.
     */
    private CallbackQueue _callbackQueue;
    
    /**
     * Released or not.
     */
    @Value(name = "closed")
    private boolean _closed = false;    
    
    /**
     * The node name.
     * (will never be null)
     */
    protected SimpleName _node;
    
    /**
     * The node event.
     * (will never be null) 
     */
    @Value(name = "event")
    protected SimpleName _event;
    
    /**
     * The composite name and event.
     * (will never be null) 
     */
    protected NodelPoint _eventPoint;
    
    /**
     * (never null)
     */
    private Binding _metadata;
    
    /**
     * The handler call-back.
     * (will never be null after 'setHandler' is used)
     */
    protected NodelEventHandler _handler;
    
    /**
     * When binding state changes, the first handler considered "safe", second and subsequent "wild".
     */
    private LockFreeList<Handler.H1<BindingState>> _bindingStateHandlers = new LockFreeList<>();
    
    /**
     * The last status.
     */
    private AtomicReference<BindingState> _bindingState = new AtomicReference<BindingState>(BindingState.Empty);
    
    /**
     * Binding status sequence number
     */
    private long _statusSeq;
    
    /**
     * Time binding status was set
     */
    private DateTime _statusTimestamp;

    /**
     * The last snap shot of the argument (linked to 'seqNum') (can never be 'null')
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
     * How often to persist the args. (default 2 hours)
     */
    private long PERSIST_PERIOD = 2 * 3600 * 1000;

    /**
     * In an unbound state.
     */
    private boolean _isUnbound;

    /**
     * (for context on callbacks)
     */
    private Handler.H0 _threadStateHandler;

    /**
     * (for context on callbacks)
     */
    private Handler.H1<Exception> _exceptionHandler;

    /**
     * Constructs a new Nodel Client to manage a single remote node.
     */
    public NodelClientEvent(SimpleName name, Binding metadata, SimpleName node, SimpleName event) {
        if (name == null)
            throw new IllegalArgumentException("name cannot be null.");
        
        _name = name;
        _metadata = (metadata != null ? metadata : Binding.Blank);
        
        setNodeAndEvent(node, event);
    }
    
    /**
     * (overloaded: null metadata)
     */
    public NodelClientEvent(SimpleName name, SimpleName node, SimpleName event) {
        this(name, null, node, event);
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
    
    /**
     * Delayed node and event setting.
     */
    public void setNodeAndEvent(SimpleName node, SimpleName event) {
        _isUnbound = (node == null || event == null); 
        
        _node = node != null ? node : UNBOUND;
        _event = event != null ? event : UNBOUND;

        _eventPoint = NodelPoint.create(_node, _event);
    }

    @Value(name = "name", title = "Name")
    public SimpleName getName() {
        return _name;
    }
    
    /**
     * Returns the name object of the node being managed by this nodel client.
     */
    @Value(name = "node")
    public SimpleName getNode() {
        return _node;
    }
    
    /**
     * The Nodel event.
     */
    @Value(name = "eventPoint")
    public SimpleName getEvent() {
        return _event;
    }
    
    /**
     * The composite Nodel point.
     */
    public NodelPoint getNodelPoint() {
        return _eventPoint;
    }
    
    /**
     * The metadata field.
     */
    public Binding getMetadata() {
        return _metadata;
    }
    
    @Value(name = "arg", title = "Argument")
    public Object getArg() {
        return _argInstance.get().arg;
    }   

    @Value(name = "argSeq", title = "Argument sequence")
    public long getSeqNum() {
        return _argInstance.get().seqNum;
    }
    
    @Value(name = "argTimestamp", title = "Argument timestamp")
    public DateTime getTimestamp() {
        return _argInstance.get().timestamp;
    }
    
    @Value(name = "status", title = "Status")
    @Deprecated
    public BindingState getStatus() {
        return _bindingState.get();
    }
    
    @Value(name = "bindingState", title = "Binding state")
    public BindingState getBindingState() {
        return _bindingState.get();
    }    

    @Value(name = "statusSeq", title = "Status sequence")
    public long getStatusSeqNum() {
        return _statusSeq;
    }
    
    @Value(name = "statusTimestamp", title = "Status timestamp")
    public DateTime getStatusTimestamp() {
        return _statusTimestamp;
    }
    
    /**
     * Sets the fields which control the threading context
     */
    public void setThreadingEnvironment(CallbackQueue callbackQueue, Handler.H0 threadStateHandler, Handler.H1<Exception> exceptionHandler) {
        _callbackQueue = callbackQueue;
        _threadStateHandler = threadStateHandler;
        _exceptionHandler = exceptionHandler;
    }
    
    /**
     * Sets the callback handler 
     */
    public void setHandler(final NodelEventHandler handler) {
        if (handler == null)
            throw new IllegalArgumentException();

        // intercept callback
        _handler = new NodelEventHandler() {

            @Override
            public void handleEvent(SimpleName node, SimpleName event, Object arg) {
                DateTime now = DateTime.now();
                
                ArgInstance argInstance = new ArgInstance();
                argInstance.timestamp = now;
                argInstance.arg = arg;
                argInstance.seqNum = Nodel.getNextSeq(); 
                
                _argInstance.set(argInstance);
                
                handler.handleEvent(node, event, arg);
            }

        };
    }
    
    /**
     * Registers interest in a remote Node's event (Nodel layer trigger)
     * (will fail if already registered)
     */
    public void registerInterest() {
        if (_isUnbound)
            return;
        
        NodelClients.instance().registerEventInterest(this);
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
     * Framework (first) and wild (subsequent) event handling.
     */
    public void addBindingStateHandler(Handler.H1<BindingState> handler) {
        if (handler == null)
            throw new IllegalArgumentException("Handler cannot be null.");

        _bindingStateHandlers.add(handler);
    }
    
    /**
     * (called internally by framework)
     */
    void setBindingState(final BindingState status) {
        BindingState last = _bindingState.getAndSet(status);

        if (last == status)
            return;
        
        _statusTimestamp = DateTime.now();

        // must set sequence number last
        _statusSeq = Nodel.getNextSeq();
        
        // get snapshot
        final List<H1<BindingState>> handlers = _bindingStateHandlers.items();
        
        final int handlerCount = handlers.size();

        // treat the first one as safe
        if (handlerCount > 0)
            Handler.handle(handlers.get(0), status);

        // treat the others as "wild"
        if (handlers.size() > 1) {
            ChannelClient.getThreadPool().execute(new Runnable() {

                @Override
                public void run() {
                    // set up thread context
                    Handler.handle(_threadStateHandler);

                    Exception lastExc = null;

                    // go through subsequent "wild" handlers
                    for (int i = 1; i < handlerCount; i++) {
                        Handler.H1<BindingState> handler = handlers.get(i);

                        if (_callbackQueue != null) {
                            _callbackQueue.handle(handler, status, _exceptionHandler);

                        } else {
                            try {
                                Handler.handle(handler, status);
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
     * Releases this binding.
     */
    public void close() {
        if (_closed)
            return;
        
        _closed = true;
        
        _bindingStateHandlers.clear();
        
        if (_persisterTimer != null)
            _persisterTimer.cancel();
        
        persistNow();
        
        NodelClients.instance().release(this);
    }    

    @Override
    public String toString() {
        return Serialisation.serialise(this);
    }

}
