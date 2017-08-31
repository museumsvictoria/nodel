package org.nodel.host;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.util.Map;

import org.nodel.reflection.Schema;
import org.nodel.reflection.Serialisation;
import org.nodel.reflection.Value;

/**
 * Represents a dynamic node's parameter.
 */
public class ParameterBinding {
    
    public final static ParameterBinding Example;
    
    public final static String ExampleName = "ipAddress";
    
    static {
        Example = new ParameterBinding();
        Example.title = "IP address";
        Example.desc = "The IP address to connect to.";
        Example.schema = Schema.getSchemaObject(String.class);
        Example.value = "192.168.100.1";
    }
    
    @Value(name = "desc", order = 2)
    public String desc;
    
    @Value(name = "group", order = 3)
    public String group;
    
    @Value(name = "schema", order = 4)
    public Map<String, Object> schema;
    
    @Value(name = "value", order = 5)
    public Object value;

    @Value(name = "title", order = 6)
    public String title;

    @Value(name = "order", title = "Order", order = 7, required = false)
    public double order;

    public String toString() {  
        return Serialisation.serialise(this);
    }
    
} // (class)
