package org.nodel.host;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.io.File;
import java.io.IOException;

import org.joda.time.DateTime;
import org.nodel.Handler;
import org.nodel.SimpleName;
import org.nodel.core.NodelServerAction;
import org.nodel.core.NodelServerEvent;

/**
 * A base for a dynamic node, i.e. one which has dynamic actions and events.
 */
public abstract class BaseDynamicNode extends BaseNode {
    
    public BaseDynamicNode(File root) throws IOException {
        super(root);
    }
    
    /**
     * Injects log into this Node on behalf of another entity (override to prevent)
     */
    public void injectLog(DateTime now, LogEntry.Source source, LogEntry.Type type, SimpleName alias, Object arg) {
        addLog(now, source, type, alias, arg);
    }
    
    /**
     * Injects an action on behalf of another entity (override to disallow)
     */
    public void injectLocalAction(NodelServerAction action) {
        addLocalAction(action);
    }
    
    /**
     * Extracts an action on behalf of another entity (override to disallow)
     */
    public void extractLocalAction(NodelServerAction action) {
        removeLocalAction(action);
    }
    
    /**
     * Injects an event on behalf of another entity (override to disallow)
     */
    public void injectLocalEvent(final NodelServerEvent event) {
        addLocalEvent(event);

        event.attachMonitor(new Handler.H2<DateTime, Object>() {

            @Override
            public void handle(DateTime timestamp, Object arg) {
                addLog(timestamp, LogEntry.Source.local, LogEntry.Type.event, event.getEvent(), arg);
            }

        });
    }
    
    /**
     * Removes an event on behalf of another entity (override to disallow)
     */
    public void extractLocalEvent(NodelServerEvent event) {
        removeLocalEvent(event);
    }    
   
}
