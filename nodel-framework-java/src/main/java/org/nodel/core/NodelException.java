package org.nodel.core;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

/**
 * Nodel specific exceptions.
 */
public class NodelException extends RuntimeException {

    private static final long serialVersionUID = 4493818877974306542L;
    
    public NodelException() {
        super();
    }
    
    public NodelException(String message) {
        super(message);
    }
    
    public NodelException(Throwable cause) {
        super(cause);
    }
    
    public NodelException(String message, Throwable cause) {
        super(message, cause);
    }

} // (class)
