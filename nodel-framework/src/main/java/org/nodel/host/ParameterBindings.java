package org.nodel.host;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.nodel.SimpleName;
import org.nodel.reflection.Serialisation;

/**
 * The dynamic node's full parameter map.
 */
public class ParameterBindings extends HashMap<SimpleName, ParameterBinding> {
    
    private static final long serialVersionUID = 9052202331768703250L;

    public static ParameterBindings Empty = new ParameterBindings();

    public static ParameterBindings Example;

    static {
        Example = new ParameterBindings();
        Example.put(new SimpleName(ParameterBinding.ExampleName), ParameterBinding.Example);
    }
    
    public ParameterBinding get(SimpleName key) {
        return super.get(key);
    }
    
    public Map<String, Object> asSchema() {
        Map<String, Object> schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("title", "Parameters");

        Map<String, Object> properties = new LinkedHashMap<String, Object>();

        schema.put("properties", properties);
        for (Map.Entry<SimpleName, ParameterBinding> entry : this.entrySet()) {
            String paramName = entry.getKey().getReducedName();
            ParameterBinding paramBinding = entry.getValue();
            Map<String, Object> paramSchema = paramBinding.schema;
            
            if (paramSchema == null)
                paramSchema = new HashMap<String, Object>();
            
            paramSchema.put("title", paramBinding.title);
            paramSchema.put("desc", paramBinding.desc);
            paramSchema.put("group", paramBinding.group);
            paramSchema.put("order", paramBinding.order);
            properties.put(paramName, paramSchema);
        } // (for)

        return schema;
    } // (method)   
    
    public String toString() {  
        return Serialisation.serialise(this);
    }    
    
//    public 

} // (class)
