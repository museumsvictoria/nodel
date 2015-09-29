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
import org.nodel.host.Binding;
import org.nodel.reflection.Param;
import org.nodel.reflection.Service;
import org.nodel.reflection.Value;

public class NodelServerEvent implements Closeable {
    
    protected SimpleName _node;
    
    protected SimpleName _event;
    
    protected NodelPoint _eventPoint;
    
    protected ActionRequestHandler handler;
    
    private boolean _closed;

    /**
     * For monitoring purposes.
     */
    private Handler.H1<Object> _monitor;

    private String _title;

    private String _desc;

    private String _group;

    private String _caution;
    
    private double _order;
    
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
    
    public NodelServerEvent(String node, String event, Binding metadata) {
        if (Strings.isNullOrEmpty(node) || Strings.isNullOrEmpty(event))
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
        return _argValue.get();
    }

    @Value(name = "seq", title = "Sequence number")
    public long getSeqNum() {
        return _seqNum;
    }

    @Value(name = "timestamp", title = "Timestamp")
    public DateTime getTimestamp() {
        return _timestamp.get();
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
    @Service(name = "emit", title = "Emit", desc = "Emits this event.")    
    public void emit(@Param(name="arg", title="Argument") Object arg) {
        doEmit(arg);
    }
    
    /**
     * Fires the event.
     */
    private void doEmit(Object arg) {
        _argValue.set(arg);
        _timestamp.set(DateTime.now());
        
        // seq must be set last
        _seqNum = Nodel.getNextSeq();

        if (_monitor != null)
            _monitor.handle(arg);

        NodelServers.instance().emitEvent(this, arg);        
    }
    
    /**
     * Attaches a monitor.
     */
    public void attachMonitor(Handler.H1<Object> monitor) {
        if (monitor == null)
            throw new IllegalArgumentException("Cannot detach monitor.");
        
        _monitor = monitor;
    }
    
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
