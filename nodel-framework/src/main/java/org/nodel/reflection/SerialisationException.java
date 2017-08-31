package org.nodel.reflection;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

/**
 * Represents any exception related to a serialisation failure.
 */
public class SerialisationException extends RuntimeException {

    private static final long serialVersionUID = 0L;
    
    public SerialisationException(String message) {
        super(message);
    }
    
    public SerialisationException(Throwable cause) {
        super(cause);
    }
    
    public SerialisationException(String message, Throwable cause) {
        super(message, cause);
    }

} // (class)
