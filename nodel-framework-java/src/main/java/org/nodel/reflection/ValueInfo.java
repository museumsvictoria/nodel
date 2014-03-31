package org.nodel.reflection;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.lang.reflect.Member;

/**
 * Holds quick lookup info when Value attributes are specified.
 */
public class ValueInfo implements Comparable<ValueInfo> {
    
    public String name;

    /**
     * Could be a field or method with no arguments
     */
    public Member member;
    
    public Value annotation;

    /**
     * Compare by 'order' field.
     */
    @Override
    public int compareTo(ValueInfo o) {
        double x = annotation.order();
        double y = o.annotation.order();

        return (x < y) ? -1 : ((x == y) ? 0 : 1);
    } // (method)

} // (class)