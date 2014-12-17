package org.nodel.core;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.io.Closeable;

import org.nodel.SimpleName;
import org.nodel.Strings;

public class NodelServerAction implements Closeable {
    
    protected SimpleName _node;
    
    protected SimpleName _action;
    
    protected NodelPoint _actionPoint;
    
    protected ActionRequestHandler _handler;
    
    private boolean _closed;
    
    public NodelServerAction(String node, String action) {
        if (Strings.isNullOrEmpty(node) || Strings.isNullOrEmpty(action))
            throw new IllegalArgumentException("Names cannot be null or empty.");
        
        _node = new SimpleName(node);
        _action = new SimpleName(action);
        _actionPoint = NodelPoint.create(node, action);
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
    public SimpleName getAction() {
        return _action;
    }
    
    /**
     * The composite Nodel point.
     */
    public NodelPoint getNodelPoint() {
        return _actionPoint;
    }    
    
    /**
     * Registers an action.
     */
    public void registerAction(ActionRequestHandler handler) {
        if (handler == null)
            throw new IllegalArgumentException();
        
        _handler = handler;
        
        NodelServers.instance().registerAction(this);
    } // (method)
    
    /**
     * Gets request handling object.
     */
    public ActionRequestHandler getHandler() {
        return _handler;
    }
    
    public void close() {
        if (_closed)
            return;
        
        _closed = true;
        
        NodelServers.instance().unregisterAction(this);
    } // (method)
    
} // (class)
