package org.nodel;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

/**
 * Runtime version of Interrupted exception.
 */
public class UnexpectedInterruptedException extends RuntimeException {

    private static final long serialVersionUID = 8255453972778347818L;

    public UnexpectedInterruptedException(String message) {
        super(message);
    }

    public UnexpectedInterruptedException(InterruptedException cause) {
        super(cause);
    }

    public UnexpectedInterruptedException(String message, InterruptedException cause) {
        super(message, cause);
    }

} // (class)