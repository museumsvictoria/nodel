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
import org.nodel.SimpleName;
import org.nodel.Strings;
import org.nodel.host.Binding;
import org.nodel.reflection.Param;
import org.nodel.reflection.Service;
import org.nodel.reflection.Value;

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

    protected ActionRequestHandler _handler;

    private boolean _closed;

    public NodelServerAction(String node, String action, Binding metadata) {
        if (Strings.isNullOrEmpty(node) || Strings.isNullOrEmpty(action))
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
     * Registers an action.
     */
    public void registerAction(final ActionRequestHandler handler) {
        if (handler == null)
            throw new IllegalArgumentException();
        
        // intercept the callback and record state
        _handler = new ActionRequestHandler() {
            
            @Override
            public void handleActionRequest(Object arg) {
                _argValue.set(arg);
                _timestamp.set(DateTime.now());

                // seq must be set last
                _seqNum = Nodel.getNextSeq();

                handler.handleActionRequest(arg);
            }

        };
        
        NodelServers.instance().registerAction(this);
    } // (method)
    
    /**
     * Gets request handling object.
     */
    public ActionRequestHandler getHandler() {
        return _handler;
    }

    @Service(name = "call", title = "Call", desc = "Invokes this action.")
    public void callExternal(@Param(name = "arg", title = "Argument") Object arg) {
        if (_handler != null)
            _handler.handleActionRequest(arg);
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
