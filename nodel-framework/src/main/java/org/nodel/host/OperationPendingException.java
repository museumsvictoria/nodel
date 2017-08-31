package org.nodel.host;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

/**
 * Used when a operation is in progress.
 */
public class OperationPendingException extends Exception {

    /**
     * (auto-generated)
     */
    private static final long serialVersionUID = 4776299254834378717L;
    
    public OperationPendingException() {
        super("There is still an operation in progress; try again later.");
    }
    
    public OperationPendingException(String message) {
        super(message);
    }
    
    public OperationPendingException(String message, Throwable cause) {
        super(message, cause);
    }

} // (class)
