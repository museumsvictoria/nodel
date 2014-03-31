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
 * An annotation used to represent a method or field that will act as a service end-point
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface Service {
	
	/**
	 * The strict name/id/alias to use for the value.
	 */
	public String name() default "";

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
	 * Holds an optional description of this service.
	 */
	public String desc() default "";
	
	/**
	 * Overrides the content type (MIME).
	 * TODO: reserved only; not supported
	 */
	public String contentType() default "";
	
	/**
	 * Embed in outer class using the field name specified.
	 */
	public String embeddedFieldName() default "";
	
	/**
	 * Holds if generics are being used.
	 * 'Type A' would apply to Lists and Map keys, for example.
	 */
	public Class<?> genericClassA() default Object.class;
	
	/**
	 * Holds if generics are being used.
	 * 'Type B' would apply to Map values.
	 */	
	public Class<?> genericClassB() default Object.class;

} // (class)