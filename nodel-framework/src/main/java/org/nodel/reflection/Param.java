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
@Target(ElementType.PARAMETER)
public @interface Param {
	
	/**
	 * The strict name/id/alias to use for the value.
	 * Name is compulsory because Java doesn't not support named parameters
	 */
	public String name() default "";

	/**
	 * A short title for the value.
	 */
	public String title() default "";
	
	/**
	 * A short description of the parameter.
	 */
	public String desc() default "";
	
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
	 * Required field?
	 */
	public boolean required() default true;

    /**
     * 'Complete values' means it encompasses a major parameter.
     * @return
     */
	public boolean isMajor() default false;
	
} // (method)
