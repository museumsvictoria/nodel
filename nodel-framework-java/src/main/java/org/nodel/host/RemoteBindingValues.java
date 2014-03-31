package org.nodel.host;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.nodel.SimpleName;
import org.nodel.reflection.Serialisation;
import org.nodel.reflection.Value;

/**
 * The actual remote binding values.
 */
public class RemoteBindingValues {
    
    public static final RemoteBindingValues Empty = new RemoteBindingValues();

    public static final RemoteBindingValues Example = new RemoteBindingValues();; 
    
    static {
        Empty.actions = Collections.emptyMap();
        Empty.events = Collections.emptyMap();
        
        Example.actions = new LinkedHashMap<SimpleName, RemoteBindingValues.ActionValue>();
        Example.actions.put(new SimpleName("projectorOn"), ActionValue.Example);
        Example.events = new LinkedHashMap<SimpleName, RemoteBindingValues.EventValue>();
        Example.events.put(new SimpleName("sensorTriggered"), EventValue.Example);
    }

    public static class ActionValue {
        
        public static final ActionValue Example = new ActionValue();
        
        static {
            Example.node = new SimpleName("Projector1");
            Example.action = new SimpleName("Turn on");
        }
        
        @Value(name = "node", title = "Node", desc = "A node name.", format = "node", order = 1)
        public SimpleName node;
        
        @Value(name = "action", title = "Action", desc = "An action name.", format = "action", order = 2)
        public SimpleName action;
        
        public String toString() {  
            return Serialisation.serialise(this);
        }        
    }
    
    public static class EventValue {
        
        public static final EventValue Example = new EventValue();
        
        static {
            Example.node = new SimpleName("Sensor1");
            Example.event = new SimpleName("Triggered");
        }        
        
        @Value(order = 1, title = "Node", desc = "A node name.", format = "node")
        public SimpleName node;
        
        @Value(order = 2, title = "Event", desc = "An event name.", format = "event")
        public SimpleName event;
        
        public String toString() {  
            return Serialisation.serialise(this);
        }        
    }    

    @Value(name = "actions", order = 1, title = "Actions", genericClassA = SimpleName.class, genericClassB = ActionValue.class,
            desc = "The actions.")
    public Map<SimpleName, ActionValue> actions;

    @Value(name = "events", order = 2, title = "Events", genericClassA = SimpleName.class, genericClassB = EventValue.class,
            desc = "The events.")
    public Map<SimpleName, EventValue> events;
    
    public String toString() {  
        return Serialisation.serialise(this);
    }    

} // (class)
