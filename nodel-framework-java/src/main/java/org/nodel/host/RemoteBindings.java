package org.nodel.host;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.nodel.SimpleName;
import org.nodel.Strings;
import org.nodel.host.RemoteBindingValues.ActionValue;
import org.nodel.host.RemoteBindingValues.EventValue;
import org.nodel.json.JSONException;
import org.nodel.json.JSONObject;
import org.nodel.reflection.Schema;
import org.nodel.reflection.Serialisation;
import org.nodel.reflection.Value;

/**
 * The full remote binding maps.
 */
public class RemoteBindings {
    
    /**
     * The default or empty instance.
     */
    public final static RemoteBindings Empty;
    
    static {
        Empty = new RemoteBindings();
        Empty.actions = Collections.emptyMap();
        Empty.events = Collections.emptyMap();
    }
    
    @Value(title = "Actions", order = 1, desc = "The remote actions required by this node.", genericClassA = SimpleName.class, genericClassB = NodelActionInfo.class)
    public Map<SimpleName, NodelActionInfo> actions;

    @Value(title = "Events", order = 2, desc = "The remote events required by this node.", genericClassA = SimpleName.class, genericClassB = NodelEventInfo.class)
    public Map<SimpleName, NodelEventInfo> events;

    public String toString() {
        return Serialisation.serialise(this);
    }
    
    public Map<String, Object> asSchema() {
        Map<String, Object> schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("title", "Remote");
        schema.put("desc", "Holds all the remote bindings.");

        Map<String, Object> properties = new LinkedHashMap<String, Object>();

        schema.put("properties", properties);
        
        if (this.actions.size() > 0)
            properties.put("actions", actionsAsSchema());
        
        if (this.events.size() > 0)
            properties.put("events", eventsAsSchema());
        
        return schema;        
    }

    private Map<String, Object> actionsAsSchema() {
        Map<String, Object> schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("title", "Actions");

        Map<String, Object> properties = new LinkedHashMap<String, Object>();

        schema.put("properties", properties);

        for (Entry<SimpleName, NodelActionInfo> entry : this.actions.entrySet()) {
            String name = entry.getKey().getOriginalName();
            NodelActionInfo info = entry.getValue();
            
            Map<String, Object> actionsSchema = Schema.getSchemaObject(ActionValue.class);
            actionsSchema.put("title", info.title);
            actionsSchema.put("desc", info.desc);
            
            properties.put(name, actionsSchema);
        } // (for)

        return schema;
    } // (method)
    
    private Map<String, Object> eventsAsSchema() {
        Map<String, Object> schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("title", "Events");
        
        Map<String, Object> properties = new LinkedHashMap<String, Object>();

        schema.put("properties", properties);

        for (Entry<SimpleName, NodelEventInfo> entry : this.events.entrySet()) {
            String name = entry.getKey().getOriginalName();
            NodelEventInfo info = entry.getValue();
            Map<String, Object> eventSchema = Schema.getSchemaObject(EventValue.class);
            eventSchema.put("title", info.title);
            eventSchema.put("desc", info.desc);
            properties.put(name, eventSchema);
        } // (for)

        return schema;
    } // (method)    
    
    public Map<String, Object> asValue() {
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        
        Map<String, Object> actionsSection = new LinkedHashMap<String, Object>();

        for (Entry<SimpleName, NodelActionInfo> entry : this.actions.entrySet()) {
            SimpleName actionName = entry.getKey();
            NodelActionInfo actionInfo = entry.getValue();
            
            ActionValue actionState = new ActionValue();
            
            if (!Strings.isNullOrEmpty(actionInfo.node))
                actionState.node = new SimpleName(actionInfo.node);

            if (!Strings.isNullOrEmpty(actionInfo.action))
                actionState.action = new SimpleName(actionInfo.action);
            
            actionsSection.put(actionName.getOriginalName(), actionState);
        } // (for)
        
        Map<String, Object> eventsSection = new LinkedHashMap<String, Object>();
        
        for (Entry<SimpleName, NodelEventInfo> entry : this.events.entrySet()) {
            SimpleName actionName = entry.getKey();
            NodelEventInfo eventInfo = entry.getValue();
            
            EventValue eventState = new EventValue();
            
            if (!Strings.isNullOrEmpty(eventInfo.node))
                eventState.node = new SimpleName(eventInfo.node);

            if (!Strings.isNullOrEmpty(eventInfo.event))
                eventState.event = new SimpleName(eventInfo.event);
            
            eventsSection.put(actionName.getOriginalName(), eventState);            
        } // (for)
        
        value.put("actions", actionsSection);
        value.put("events", eventsSection);

        return value;
    } // (method)

    public void save(Map<String, Object> valueMap) throws JSONException {
        // get the actions section
        Object actionsValue = valueMap.get("actions");
        
        // (will be a JSON object)
        JSONObject actionsSection = (JSONObject) actionsValue;
        
        // go through the map and set the values
        for (String key : actionsSection.keySet()) {
            
            SimpleName name = new SimpleName(key);
            
            NodelActionInfo nodelActionInfo = this.actions.get(name);
            if (nodelActionInfo == null)
                // undeclared variable, skip
                continue;

            Object value = actionsSection.get(key);
            NodelActionInfo newNodelActionInfo = (NodelActionInfo) Serialisation.coerce(NodelActionInfo.class, value);
            this.actions.put(new SimpleName(key), newNodelActionInfo);
        } // (for)
        
        // get the events section
        Object eventsValue = valueMap.get("events");
        
        // (will be a JSON object)
        JSONObject eventsSection = (JSONObject) eventsValue;
        
        // go through the map and set the values
        for (String key : eventsSection.keySet()) {
            
            SimpleName name = new SimpleName(key);
            
            NodelEventInfo nodelEventInfo = this.events.get(name);
            if (nodelEventInfo == null)
                // undeclared variable, skip
                continue;

            Object value = eventsSection.get(key);
            NodelEventInfo newNodelEventInfo = (NodelEventInfo) Serialisation.coerce(NodelEventInfo.class, value);
            this.events.put(new SimpleName(key), newNodelEventInfo);
        } // (for)  
        
    } // (method) 
    
} // (class)
