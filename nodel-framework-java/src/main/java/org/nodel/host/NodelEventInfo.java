package org.nodel.host;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import org.nodel.reflection.Serialisation;
import org.nodel.reflection.Value;

/**
 * Represents a dynamic node's event.
 */
public class NodelEventInfo {
    
    public final static NodelEventInfo Example;
    
    static {
        Example = new NodelEventInfo();
        Example.node = "ExhibitSensor1";
        Example.event = "Triggered";
        Example.group = "General";
    }
    
    @Value(title = "Node", order = 2)
    public String node;
    
    @Value(title = "Event", order = 3)
    public String event;
    
    @Value(title = "Group", order = 4, required = false)
    public String group;

    @Value(title = "Title", order = 5, required = false)
    public String title;
    
    @Value(title = "Desc", order = 6, required = false)
    public String desc;
    
    @Value(name = "caution", title = "Description", order = 7, required = false)
    public String caution;
    
    public String toString() {
        return Serialisation.serialise(this); 
    }

} // (class)
