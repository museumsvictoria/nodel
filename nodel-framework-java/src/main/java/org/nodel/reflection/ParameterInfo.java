package org.nodel.reflection;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

public class ParameterInfo implements Comparable<ParameterInfo> {

    /**
     * The name of the parameter.
     */
    public String name;

    /**
     * Can be null if no annotation was used.
     */
    public Param annotation;

    /**
     * The index of the parameter.
     */
    public int index;

    public Class<?> klass;

    public ParameterInfo(String name, int index, Class<?> klass, Param annotation) {
        this.name = name;
        this.index = index;
        this.klass = klass;
        this.annotation = annotation;
    }

    public ParameterInfo(String name, int index, Class<?> klass) {
        this(name, index, klass, null);
    }

    /**
     * Compares by index.
     */
    @Override
    public int compareTo(ParameterInfo o) {
        int x = this.index;
        int y = o.index;

        return (x < y) ? -1 : ((x == y) ? 0 : 1);
    }
    
} // (class)
