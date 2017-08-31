package org.nodel.reflection;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

public class EnumInfo {
    
    /**
     * Holds the actual enum constant.
     */
    public Object constant;
    
    /**
     * A title (defaults to value (as string) if unavailable)
     */
    public String title;
    
    /**
     * A description (will be null if none available)
     */
    public String desc;
    
    public EnumInfo(Object constant) {
        this.constant = constant;
        this.title = constant.toString();
    }

}
