package org.nodel.net;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

/**
 * Represents network credentials
 */
public class Credentials {
    
    private String username;
    
    private String password;
    
    /**
     * Empty constructor.
     */
    public Credentials() { }

    /**
     * Full constructor.
     */
    public Credentials(String username, String password) {
	this.username = username;
	this.password = password;
    }
    
    public String getUsername() {
	return this.username;
    }
    
    public void setUsername(String value) {
	this.username = value;
    }
    
    public String getPassword() {
	return this.password;
    }
    
    public void setPassword(String value) {
	this.password = value;
    }
    
} // (class)
