package org.nodel.rest;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

public class EndpointNotFoundException extends RuntimeException {

    /**
     * (generated) 
     */
    private static final long serialVersionUID = -1516647525053272225L;
    
    private String endpoint;
    
    public EndpointNotFoundException(String endpoint) {
        super("'" + endpoint + "' not found.");
        
        this.endpoint = endpoint;
    } // (class)
    
    public String getEndpoint() {
        return this.endpoint;
    }
    
} // (class)
