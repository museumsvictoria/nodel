package org.nodel.host;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import org.joda.time.DateTime;
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
    @Value(name = "seq", title = "Sequence", desc = "The sequence number.")
    public long seq;

    /**
     * The time stamp.
     */
    @Value(name = "timestamp", title = "Timestamp", desc = "A universal timestamp.")
    public DateTime timestamp;
    
    /**
     * The source.
     */
    @Value(name = "source", title = "Source", desc = "The source of this event.")
    public Source source;

    /**
     * The type.
     */
    @Value(name = "type", title = "Type", desc = "The event type.")
    public Type type;
    
    /**
     * The related event/action alias.
     */
    @Value(name = "alias", title = "Alias", desc = "The related event/action alias.")
    public String alias;    
    
    /**
     * The argument.
     */
    @Value(name = "arg", title = "Argument", desc = "An argument.", required = false)
    public Object arg;
    
    public LogEntry(long seq, DateTime timestamp, Source source, Type type, String alias, Object arg) {
        this.seq = seq;
        this.timestamp = timestamp;
        this.source = source;
        this.type = type;
        this.alias = alias;
        this.arg = arg;
    }
    
} // (class)