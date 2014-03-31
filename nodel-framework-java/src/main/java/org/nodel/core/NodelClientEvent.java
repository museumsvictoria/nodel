package org.nodel.core;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.util.concurrent.atomic.AtomicReference;

import org.nodel.Handler;
import org.nodel.SimpleName;
import org.nodel.reflection.Serialisation;
import org.nodel.reflection.Value;

public class NodelClientEvent {
    
    /**
     * Released or not.
     */
    @Value(name = "closed")
    private boolean _closed = false;    
    
    /**
     * The node name.
     * (will never be null)
     */
    @Value(name = "node")
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
    @Value(name = "eventPoint")
    protected NodelPoint _eventPoint;
    
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
    @Value
    private AtomicReference<BindingState> _lastStatus = new AtomicReference<BindingState>(BindingState.Empty);
    
    /**
     * Constructs a new Nodel Client to manage a single remote node.
     */
    public NodelClientEvent(String name, String event) {
        if (name == null || event == null)
            throw new IllegalArgumentException("Arguments cannot be null.");        
        
        _node = new SimpleName(name);
        _event = new SimpleName(event);
        _eventPoint = NodelPoint.create(_node, _event);
    }
    
    /**
     * Returns the name object of the node being managed by this nodel client.
     */
    public SimpleName getNode() {
        return _node;
    }
    
    /**
     * The Nodel event.
     */
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
     * Registers interest in a Node's events. 
     */
    public void setHandler(NodelEventHandler handler) {
        if  (handler == null)
            throw new IllegalArgumentException();
        
        _handler = handler;
        
        NodelClients.instance().registerEventInterest(this);
    }
    
    /**
     * Releases this binding.
     */
    public void close() {
        if (_closed)
            return;
        
        _closed = true;
        
        NodelClients.instance().release(this);
    }
    
    public void attachWiredStatusChanged(Handler.H1<BindingState> handler) {
        if (handler == null)
            throw new IllegalArgumentException("Handler cannot be null.");
        
        _wiredStatusHandler = handler; 
    } // (method)    

    void setWiredStatus(BindingState status) {
        BindingState last = _lastStatus.getAndSet(status);
        
        if (last != status && _wiredStatusHandler != null) {
            _wiredStatusHandler.handle(status);
        }
    }

    @Override
    public String toString() {
        return Serialisation.serialise(this);
    }

} // (class)
