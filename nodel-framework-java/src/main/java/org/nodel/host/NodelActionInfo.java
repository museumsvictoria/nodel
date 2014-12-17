package org.nodel.host;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import org.nodel.reflection.Serialisation;
import org.nodel.reflection.Value;

/**
 * Represents a dynamic node's action.
 */
public class NodelActionInfo {
    
    public final static NodelActionInfo Example;
    
    static {
        Example = new NodelActionInfo();
        Example.node = "MyProjector";
        Example.action = "TurnOn";
        Example.group = "Power";
    }
    
    @Value(name = "node", title = "Node", order = 2)
    public String node;
    
    @Value(name = "action", title = "Action", order = 3)
    public String action;
    
    @Value(name = "title", title = "Title", order = 4, required = false)
    public String title;
    
    @Value(name = "group", title = "Group", order = 5, required = false)
    public String group;

    @Value(name = "desc", title = "Description", order = 6, required = false)
    public String desc;
    
    @Value(name = "caution", title = "Caution", order = 7, required = false)
    public String caution;
    
    public String toString() {
        return Serialisation.serialise(this); 
    }

} // (class)
