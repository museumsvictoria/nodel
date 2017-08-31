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

/**
 * An annotation used to represent JSON fields
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface Value {

    /**
     * The strict name/id/alias to use for the value.
     */
    public String name();

    /**
     * A short title for the value.
     */
    public String title() default "";

    /**
     * The field order.
     */
    public double order() default 0;
    
    /**
     * Is the default value for a given class. 
     */
    public boolean treatAsDefaultValue() default false;

    /**
     * Options or allowed / suggested values.
     */
    public String[] suggestions() default {};

    /**
     * Options or allowed / suggested values.
     */
    public String desc() default "";
    
    /**
     * Required field?
     */
    public boolean required() default true;
    
    /**
     * Whether or not this is considered an advanced field. 
     */
    public boolean advanced() default false;
    
    /**
     * Holds if generics are being used.
     * 'Type A' would apply to Lists and Map keys.
     */
    public Class<?> genericClassA() default Object.class;
    
    /**
     * Holds if generics are being used.
     * 'Type B' would apply to Map values.
     */ 
    public Class<?> genericClassB() default Object.class;    

    /**
     * Don't expose this field in schema views.
     * 
     * @return
     */
    public boolean hidden() default false;

    /**
     * The MIME type for the value.
     * TODO: reserved only; not supported
     */
    public String contentType() default "";

    /**
     * Allows the 'format' to be overridden.
     */
    public String format() default "";
    
    /**
     * For arrays, the minimum number of items allowed OR -1 for any number.
     */
    public int minItems() default -1;
    
    /**
     * For arrays, the maximum number of items allowed OR -1 for any number.
     */
    public int maxItems() default -1;    

} // (class)
