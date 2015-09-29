package org.nodel.host;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import org.joda.time.DateTime;
import org.nodel.reflection.Value;

/**
 * Console logs.
 */
public class ConsoleLogEntry {

    /**
     * Console types: standard out, or standard error.
     */
    public enum Console {
        out, err, warn, info
    }

    @Value(name = "seq", title = "Sequence", desc = "A sequence number.")
    public long seq;
    
    @Value(name = "timestamp", title = "Timestamp", desc = "A universal timestamp.")
    public DateTime timestamp;
    
    @Value(name = "console", title = "Console", desc = "The console type.")
    public Console console;
    
    @Value(name = "comment", title = "Comment", desc = "Short commentary.")
    public String comment;
    
    public ConsoleLogEntry(long seq, DateTime timestamp, Console console, String comment) {
        this.seq = seq;
        this.timestamp = timestamp;
        this.console = console;
        this.comment = comment;
    }

} // (class)