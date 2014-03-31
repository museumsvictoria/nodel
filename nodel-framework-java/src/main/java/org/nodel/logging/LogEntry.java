package org.nodel.logging;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.logging.log4j.Level;
import org.joda.time.DateTime;
import org.nodel.Strings;
import org.nodel.reflection.Value;

public class LogEntry {
    
    /**
     * Static thread-safe counter.
     */
    private final static AtomicLong s_sequenceCounter = new AtomicLong(0);
    
    @Value(name = "seq", title = "Sequence", desc = "A sequence number.", order = 1)
    public long seq = s_sequenceCounter.getAndIncrement();

    @Value(name = "timestamp", title = "Timestamp", desc = "A universal timestamp.", order = 2)
    public DateTime timestamp;

    @Value(name = "level", title = "Level", desc = "A standard log level.", order = 3)
    public Level level;

    @Value(name = "thread", title = "Thread", desc = "The thread name (if available) or thread ID.", order = 4)
    public String thread;

    @Value(name = "tag", title = "Tag", desc = "A general purpose tag which may improve context.", order = 5)
    public String tag;

    @Value(name = "message", title = "Message", desc = "Short commentary or message relating to this log event.", order = 6)
    public String message;

    @Value(name = "error", title = "Error", desc = "Specific error information.", order = 7)
    public String error;
    
    /**
     * Constructs a log entry from a basic set of logging info.
     */
    public LogEntry(DateTime timestamp, Level level, String tag, String msg, Throwable tr) {
        this.timestamp = timestamp;
        this.level = level;
        this.tag = tag;
        this.message = msg;

        // capture stack trace
        if (tr != null)
            this.error = captureStackTrace(tr);

        // capture thread info
        Thread currentThread = Thread.currentThread();
        String threadName = currentThread.getName();
        this.thread = !Strings.isNullOrEmpty(threadName) ? threadName : String.valueOf(currentThread.getId());
    }

    /**
     * Captures an exception's stack-trace.
     */
    private static String captureStackTrace(Throwable currentExc) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        currentExc.printStackTrace(pw);

        pw.flush();
        
        return sw.toString();
    }        

} // (class)
