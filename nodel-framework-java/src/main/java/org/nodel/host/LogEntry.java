package org.nodel.host;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import org.joda.time.DateTime;
import org.nodel.SimpleName;
import org.nodel.reflection.Value;

/**
 * Used for feedback by dynamic nodes.
 */
public class LogEntry {
    
    public enum Source {
        local, remote, unbound;
    }
    
    public enum Type {
        action, event, actionBinding, eventBinding;
    }
    
    /**
     * The sequence number.
     */
    @Value(name = "seq", title = "Sequence", desc = "The sequence number.", order = 1)
    public long seq;

    /**
     * The time stamp.
     */
    @Value(name = "timestamp", title = "Timestamp", desc = "A universal timestamp.", order = 2)
    public DateTime timestamp;
    
    /**
     * The source.
     */
    @Value(name = "source", title = "Source", desc = "The source of this event.", order = 3)
    public Source source;

    /**
     * The type.
     */
    @Value(name = "type", title = "Type", desc = "The event type.", order = 4)
    public Type type;
    
    /**
     * The related event/action alias.
     */
    @Value(name = "alias", title = "Alias", desc = "The related event/action alias.", order = 5)
    public SimpleName alias;    
    
    /**
     * The argument.
     */
    @Value(name = "arg", title = "Argument", desc = "An argument.", required = false, order = 6)
    public Object arg;
    
    public LogEntry(long seq, DateTime timestamp, Source source, Type type, SimpleName alias, Object arg) {
        this.seq = seq;
        this.timestamp = timestamp;
        this.source = source;
        this.type = type;
        this.alias = alias;
        this.arg = arg;
    }
    
} // (class)