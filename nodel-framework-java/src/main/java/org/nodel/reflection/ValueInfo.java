package org.nodel.reflection;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.lang.reflect.Member;

import org.nodel.Strings;

/**
 * Holds quick lookup info when Value attributes are specified.
 */
public class ValueInfo implements Comparable<ValueInfo> {
    
    public String name;

    /**
     * (see annotation)
     */
    public Member member;
    
    /**
     * (see annotation)
     */
    public String title = "";

    /**
     * (see annotation)
     */
    public double order = 0;
    /**
     * (see annotation)
     */
    public boolean treatAsDefaultValue = false;

    /**
     * (see annotation)
     */
    public String[] suggestions = Strings.EmptyArray;

    /**
     * (see annotation)
     */
    public String desc = "";
    
    /**
     * (see annotation)
     */
    public boolean required = false;
    
    /**
     * (see annotation) 
     */
    public boolean advanced = false;    
    
    /**
     * (see annotation)
     */
    public Class<?> genericClassA = Object.class;
    
    /**
     * (see annotation)
     */
    public Class<?> genericClassB = Object.class;    

    /**
     * (see annotation)
     */
    public boolean hidden = false;

    /**
     * (see annotation)
     */
    public String contentType = "";

    /**
     * (see annotation)
     */
    public String format = "";
    
    /**
     * (see annotation)
     */
    public int minItems = -1;
    
    /**
     * (see annotation)
     */
    public int maxItems = -1;
    
    public ValueInfo(String name) {
        this.name = name;
    }
    
    public ValueInfo(Value value) {
        this.name = value.name();
        this.title = value.title();
        this.order = value.order(); 
        this.treatAsDefaultValue = value.treatAsDefaultValue();
        this.suggestions = value.suggestions();
        this.desc = value.desc();
        this.required = value.required();
        this.advanced = value.advanced();
        this.genericClassA = value.genericClassA();
        this.genericClassB = value.genericClassB();    
        this.hidden = value.hidden();
        this.contentType = value.contentType();
        this.format = value.format();
        this.minItems = value.minItems();
        this.maxItems = value.maxItems();      
    }
    
    public static ValueInfo listType(Class<?> genericClassA) {
        ValueInfo result = new ValueInfo("custom");
        result.genericClassA = genericClassA;
        return result;
    }
    
    public static ValueInfo mapType(Class<?> genericClassA, Class<?> genericClassB) {
        ValueInfo result = new ValueInfo("custom");
        result.genericClassA = genericClassA;
        result.genericClassB = genericClassB;
        return result;
    }    
    
    /**
     * Holds the matcher setter info (if present)
     */
    public SetterInfo setter;    

    /**
     * Compare by 'order' field, then by name.
     */
    @Override
    public int compareTo(ValueInfo o) {
        // use order first
        double x = this.order;
        double y = o.order;

        return (x < y) ? -1 : ((x == y) ? Strings.compare(this.name, o.name) : 1);
    }

} // (class)