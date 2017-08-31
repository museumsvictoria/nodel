package org.nodel.reflection;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.lang.reflect.Member;
import java.util.Map;

import org.nodel.Strings;

/**
 * Holds quick lookup info when Service attributes are specified.
 */
public class ServiceInfo implements Comparable<ServiceInfo> {

    public ServiceInfo(String name, Service annotation) {
        this.name = name;
        this.annotation = annotation;
    }

    /**
     * A short name.
     */
    public String name;

    /**
     * Could be a field or method
     */
    public Member member;

    /**
     * The annotation (never null)
     */
    public Service annotation;

    public Map<String, ParameterInfo> parameterMap;

    /**
     * Compares by 'order' field, then by name
     */
    @Override
    public int compareTo(ServiceInfo o) {
        double x = annotation.order();
        double y = o.annotation.order();

        return (x < y) ? -1 : ((x == y) ? Strings.compare(this.name, o.name) : 1);
    } // (method)

} // (class)