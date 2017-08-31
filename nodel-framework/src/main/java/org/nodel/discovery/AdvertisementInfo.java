package org.nodel.discovery;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.util.Collection;

import org.nodel.SimpleName;
import org.nodel.reflection.Value;

public class AdvertisementInfo {
    
    @Value(name = "name")
    public SimpleName name;
    
    @Value(name = "addresses")
    public Collection<String> addresses;
    
    /**
     * (millis)
     */
    @Value(name = "timeStamp")
    public long timeStamp;

} // (class)
