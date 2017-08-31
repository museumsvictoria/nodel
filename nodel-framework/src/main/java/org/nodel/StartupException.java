package org.nodel;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

/**
 * Represents a runtime exception when part of a startup procedure
 * fails.
 */
public class StartupException extends RuntimeException {

    private static final long serialVersionUID = 9164072670146296673L;

    public StartupException() {
    }

    public StartupException(String message) {
        super(message);
    }

    public StartupException(Throwable cause) {
        super(cause);
    }

    public StartupException(String message, Throwable cause) {
        super(message, cause);
    }
}
