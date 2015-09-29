package org.nodel.core;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

/**
 * Handler to deal with action requests as they arrive via the Nodel layer.
 */
public interface ActionRequestHandler {
    
    public void handleActionRequest(Object arg);

} // (class)
