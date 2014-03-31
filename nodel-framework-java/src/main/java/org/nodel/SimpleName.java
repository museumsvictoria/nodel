package org.nodel;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.util.ArrayList;
import java.util.List;

import org.nodel.core.Nodel;

/**
 * Widely used across the framework as Node names and binding points. Represents a simplified Nodel name that
 * can be used for map keys and lists, for case-and-punctuation-insensitive comparisons.
 */
public class SimpleName {

    /**
     * (see 'getOriginalName')
     */
    private String _original;
    
    /**
     * (see 'getReducedName')
     */
    private String _reduced;
    
    /**
     * For matching purposed.
     */
    private String _reducedForMatching;
    
    /**
     * (private constructor)
     */
    public SimpleName(String original) {
        _original = original;
        _reduced = Nodel.reduce(original);
        _reducedForMatching = Nodel.reduceToLower(original);
    } // (constructor)
    
    /**
     * Returns the original name used.
     */
    public String getOriginalName() {
        return _original;
    }
    
    /**
     * Returns the reduced name.
     */
    public String getReducedName() {
        return _reduced;
    }
    
    /**
     * Gets the reduced name for string matching names.
     */
    public String getReducedForMatchingName() {
        return _reducedForMatching;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof SimpleName))
            return false;
        
        SimpleName other = (SimpleName) obj;

        return _reducedForMatching.equals(other._reducedForMatching);
    } // (method)
    
    @Override
    public int hashCode() {
        return _reducedForMatching.hashCode();
    } // (method)
    
    /**
     * Returns the reduced version of the name. 
     */
    public String toString() {
        return _original;
    }
    
    /**
     * Returns a string array of original versions.
     */
    public static String[] intoOriginals(List<SimpleName> list) {
        int len = list.size();
        String[] names = new String[len];

        for (int i = 0; i < len; i++)
            names[i] = list.get(i).getOriginalName();

        return names;
    } // (method)
    
    /**
     * Returns a string array of reduced versions.
     */
    public static String[] intoReduced(List<SimpleName> list) {
        int len = list.size();;
        String[] names = new String[len];
        
        for(int i=0; i<len; i++)
            names[i] = list.get(i).getReducedName();
        
        return names; 
    } // (method)
    
    /**
     * Returns a NodelName list from an array of names.
     */
    public static List<SimpleName> fromNames(String[] names) {
        int len = names.length;

        List<SimpleName> nodes = new ArrayList<SimpleName>(len);

        for (String name : names)
            nodes.add(new SimpleName(name));

        return nodes;
    } // (method)
    
} // (class)
