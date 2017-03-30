package org.nodel.core;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.util.concurrent.atomic.AtomicReference;

import org.joda.time.DateTime;
import org.nodel.Handler;
import org.nodel.SimpleName;
import org.nodel.host.Binding;
import org.nodel.reflection.Objects;
import org.nodel.reflection.Serialisation;
import org.nodel.reflection.Value;
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
     * When wired status changes.
     */
    private Handler.H1<BindingState> _wiredStatusHandler;
    
    /**
     * The last status.
     */
    private AtomicReference<BindingState> _statusValue = new AtomicReference<BindingState>(BindingState.Empty);
    
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
     * How often to persist the args. (default 12 hours)
     */
    private long PERSIST_PERIOD = 12 * 3600 * 1000;    

    /**
     * In an unbound state.
     */
    private boolean _isUnbound;

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
        // (not critical, so default is every 12 hours. Persist will occur on close anyway.)
        _persisterTimer = s_timers.schedule(ThreadPool.background(), new TimerTask() {

            @Override
            public void run() {
                handlePersistRequest();
            }

        }, PERSIST_PERIOD, PERSIST_PERIOD);
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
    public BindingState getStatus() {
        return _statusValue.get();
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
     * Handles a persist request.
     * 
     * (mainly timer entry-point)
     */
    private void handlePersistRequest() {
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
        
        handlePersistRequest();
        
        NodelClients.instance().release(this);
    }
    
    public void attachWiredStatusChanged(Handler.H1<BindingState> handler) {
        if (handler == null)
            throw new IllegalArgumentException("Handler cannot be null.");

        _wiredStatusHandler = handler;
    }

    void setWiredStatus(BindingState status) {
        BindingState last = _statusValue.getAndSet(status);

        if (last != status && _wiredStatusHandler != null) {
            _statusTimestamp = DateTime.now();

            // must set sequence number last
            _statusSeq = Nodel.getNextSeq();

            _wiredStatusHandler.handle(status);
        }
    }

    @Override
    public String toString() {
        return Serialisation.serialise(this);
    }

}
