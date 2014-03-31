package org.nodel.rest;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

@SuppressWarnings("serial")
public class CallException extends RuntimeException {
    
    public CallException() { }
    
    public CallException(int code, String message) {
	super(message);
	this.code = code;
    }
    
    int code;
    
    public int getCode() {
	return this.code;
    }
            
} // (class)
