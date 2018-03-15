package org.nodel.host;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import org.nodel.SimpleName;
import org.nodel.reflection.Value;

/**
 * Configuration required by a dynamic node.
 */
public class NodeConfig {
    
    public static NodeConfig Empty = new NodeConfig();
    
    public static NodeConfig Example = new NodeConfig();
    
    static {
        Empty.remoteBindingValues = RemoteBindingValues.Empty;
        Empty.paramValues = ParamValues.Empty;
        
        Example.remoteBindingValues = RemoteBindingValues.Example;
        Example.paramValues = ParamValues.Example;
        Example.dependencies = new String[] { "script.py", "custom.py" };
    }
    
    
    @Value(name="dependencies", title="Dependencies / scripts / entry-points", order = 49, required = false,
           desc="An ordering of dependencies / scripts / entry-points that will be used when loading the node")
    public String[] dependencies;
    
    @Value(name = "remoteBindingValues", title = "Remote binding values", order = 50, required = true, 
           desc = "The remote action and event binding values.")
    public RemoteBindingValues remoteBindingValues;
    
    @Value(name = "paramValues", title = "Parameter values", order = 60, required = true,
           genericClassA = SimpleName.class, genericClassB = Object.class,
           desc = "The script parameter values.")
    public ParamValues paramValues;
    
} // (class)
