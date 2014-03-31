package org.nodel.reflection;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface MultiClassValue {

    /**
     * Stores the list of allowed item names when 'allowedInstances' is used.
     * (Valid for class annotations only)
     */
    public String[] allowedItemTitles() default {};

    /**
     * Stores the list of allowed instances.
     * (Valid for class annotations only)
     */
    public Class<?>[] allowedItemClasses() default {};
    
}
