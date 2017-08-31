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
     * Returns true if the value is null or empty (or full of spaces), false otherwise.
     */
    public static boolean isNullOrEmpty(String value) {
        if (value == null)
            return true;

        if (value.isEmpty())
            return true;

        // check for any non-spaces
        int len = value.length();
        for (int a = 0; a < len; a++) {
            if (value.charAt(a) != ' ')
                return false;
        }
        
        // was empty or all spaces
        return true;
    } // (method)

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
