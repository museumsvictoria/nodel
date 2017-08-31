package org.nodel.host;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.util.LinkedHashMap;

import org.nodel.SimpleName;
import org.nodel.reflection.Serialisation;

/**
 * Stores the values of the parameters.
 */
public class ParamValues extends LinkedHashMap<SimpleName, Object> {
    
    /**
     * (auto-generated)
     */
    private static final long serialVersionUID = -6657841283220392716L;
    
    /**
     * Empty version.
     */
    public static final ParamValues Empty = new ParamValues();
    
    public static final ParamValues Example = new ParamValues();
    
    static {
        Example.put(new SimpleName("ipAddress"), "192.168.100.22");
    }
    
    public String toString() {  
        return Serialisation.serialise(this);
    }    
    
} // (class)
