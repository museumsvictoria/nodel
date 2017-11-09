package org.nodel;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

/**
 * Convenience methods for Strings.
 */
public class Strings {
    
    /**
     * An empty array of strings.
     */
    public static final String[] EmptyArray = new String[] {};
    
    /**
     * Returns true if the value is null or empty (or full of common whitespace), false otherwise.
     */
    public static boolean isNullOrEmpty(String value) {
        if (value == null)
            return true;
        
        // check length...
        int len = value.length();
        
        if (len == 0)
            return true;
        
        // ...and then for any non common-whitespace
        for (int a = 0; a < len; a++) {
            char c = value.charAt(a);
            
            if (c != ' ' && c != '\t' && c != '\r' && c != '\n')
                return false;
        }
        
        // was empty or all common whitespace
        return true;
    }
    
    /**
     * (shorthand for 'isNullOrEmpty')
     */
    public static boolean isBlank(String value) {
        return isNullOrEmpty(value);
    }

    /**
     * A safe, simple alphabetical compare function.
     */
    public static int compare(String name1, String name2) {
        if (name1 == null && name2 == null)
            return 0;
        
        if (name1 == null)
            return 1;
        
        if (name2 == null)
            return -1;
        
        return name1.compareTo(name2);
    }
    
}
