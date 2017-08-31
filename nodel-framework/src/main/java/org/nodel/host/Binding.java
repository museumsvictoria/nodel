package org.nodel.host;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.util.HashMap;
import java.util.Map;

import org.nodel.SimpleName;
import org.nodel.reflection.Serialisation;
import org.nodel.reflection.Value;

/**
 * Holds a single local / remote action / event binding information for a dynamic node.
 */
public class Binding {

    public final static Binding[] EmptyArray = new Binding[0];
    
    public final static Binding Blank = new Binding();
    
    public final static Binding LocalEventExample;
    
    public final static Binding LocalActionExample1;
    
    public final static Binding LocalActionExample2;
    
    public final static SimpleName RemoteActionExampleName;
    
    public final static Binding RemoteActionExample;
    
    public final static SimpleName RemoteEventExampleName;
    
    public final static Binding RemoteEventExample;
    
    static {
        LocalEventExample = new Binding();
        LocalEventExample.schema = null;
        LocalEventExample.title = "Sensor is triggered";
        LocalEventExample.desc = "When this sensor is triggered.";
        LocalEventExample.order = 1;
        LocalEventExample.group = "General";
        
        LocalActionExample1 = new Binding();
        LocalActionExample1.schema = null;
        LocalActionExample1.title = "Turns on";
        LocalActionExample1.desc = "Turns this node on.";
        LocalActionExample1.order = 2;
        LocalActionExample1.caution = "Ensure hardware is in a state to be turned on.";
        LocalActionExample1.group = "Power";
        
        LocalActionExample2 = new Binding();
        LocalActionExample2.schema = new HashMap<String,Object>();
        LocalActionExample2.schema.put("type", "integer");
        LocalActionExample2.schema.put("title", "Level");
        LocalActionExample2.title = "Adjust level";
        LocalActionExample2.desc = "Adjusts a level.";
        LocalActionExample2.order = 3;
        LocalActionExample2.group = "General";
        
        RemoteActionExampleName = new SimpleName("TurnProjectorOn");
        RemoteActionExample = new Binding();
        RemoteActionExample.title = "Turn on";
        RemoteActionExample.desc = "Turns on the device.";
        RemoteActionExample.group = "Power";
        RemoteActionExample.order = 4;
        RemoteActionExample.schema = null;
        
        RemoteEventExampleName = new SimpleName("SensorTriggered");
        RemoteEventExample = new Binding();
        RemoteEventExample.title = "Sensor triggered";
        RemoteEventExample.desc = "Occurs when the sensor is triggered.";
        RemoteEventExample.group = "Sensing";
        RemoteEventExample.order = 5;
        RemoteEventExample.schema = null;
    } // (static)
    
    /**
     * (blank for deserialisation)
     */
    public Binding() {
    }
    
    /**
     * Constructor
     */
    public Binding(String title, String desc, String group, String caution, double order, Map<String, Object> schema) {
        this.title = title;
        this.group = group;
        this.desc = desc;
        this.caution = caution;
        this.order = order;
        this.schema = schema;
    }
    
    @Value(name = "title", title = "Title", order = 2)
    public String title;
    
    @Value(name = "desc", title = "Description", order = 3)
    public String desc;
    
    @Value(name = "schema", title = "Argument schema", order = 4, required = false, genericClassA = String.class, genericClassB = Object.class)
    public Map<String, Object> schema;
    
    @Value(name = "group", title = "Group", order = 5)
    public String group;
    
    @Value(name = "caution", title = "Caution", order = 6)
    public String caution;
    
    @Value(name = "order", title = "order", order = 7)
    public double order;
    
    public String toString() {
        return Serialisation.serialise(this); 
    } // (method)

} // (class)
