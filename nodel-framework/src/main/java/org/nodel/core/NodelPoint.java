package org.nodel.core;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import org.nodel.SimpleName;

/**
 * Stores a "node name" ,  "event" / "action" key. 
 * For use in maps or lists; performs Nodel name reduction.
 */
public class NodelPoint {
    
    /**
     * (see 'getNode')
     */
    private SimpleName _node;
    
    /**
     * (see 'getConnection')
     */
    private SimpleName _point;
    
    /**
     * (private constructor)
     */
    private NodelPoint(SimpleName node, SimpleName point) {
        _node = node;
        _point = point;
    }
    
    /**
     * Creates a new key.
     */
    public static NodelPoint create(String nodeName, String pointName) {
        return new NodelPoint(new SimpleName(nodeName), new SimpleName(pointName));
    }
    
    /**
     * Creates a new key.
     */
    public static NodelPoint create(SimpleName node, SimpleName point) {
        return new NodelPoint(node, point);
    }    
    
    /**
     * Gets the node.
     */
    public SimpleName getNode() {
        return _node;
    }
    
    /**
     * Gets the connection.
     */
    public SimpleName getPoint() {
        return _point;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof NodelPoint))
            return false;
        
        NodelPoint other = (NodelPoint) obj;

        return _node.equals(other._node) && _point.equals(other._point);
    } // (method)
    
    @Override
    public int hashCode() {
        return _node.hashCode() ^ _point.hashCode();
    } // (method)
    
    @Override
    public String toString() {
        return _node + "." + _point; 
    }
    
} // (class)