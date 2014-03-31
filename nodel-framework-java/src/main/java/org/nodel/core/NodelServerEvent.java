package org.nodel.core;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import org.nodel.Handler;
import org.nodel.SimpleName;
import org.nodel.Strings;

public class NodelServerEvent {
    
    protected SimpleName _node;
    
    protected SimpleName _event;
    
    protected NodelPoint _eventPoint;
    
    protected ActionRequestHandler handler;
    
    private boolean _closed;

    /**
     * For monitoring purposes.
     */
    private Handler.H1<Object> _monitor;
    
    public NodelServerEvent(String node, String event) {
        if (Strings.isNullOrEmpty(node) || Strings.isNullOrEmpty(event))
            throw new IllegalArgumentException("Names cannot be null or empty.");
        
        _node = new SimpleName(node);
        _event = new SimpleName(event);
        _eventPoint = NodelPoint.create(node, event);
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
     * Fires the event (with argument)
     */
    public void emit(Object arg) {
        doEmit(arg);
    }
    
    /**
     * Fires the event.
     */
    private void doEmit(Object arg) {
        if (_monitor != null)
            _monitor.handle(arg);
        
        NodelServers.instance().emitEvent(this, arg);        
    } // (method)
    
    
    
    /**
     * Attaches a monitor.
     */
    public void attachMonitor(Handler.H1<Object> monitor) {
        if (monitor == null)
            throw new IllegalArgumentException("Cannot detach monitor.");
        
        _monitor = monitor;
    } // (method)
    
    /**
     * Releases this binding.
     */
    public void close() {
        if (_closed)
            return;
        
        _closed = true;
        
        NodelServers.instance().unregisterEvent(this);
    }
    
} // (class)
