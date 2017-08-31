package org.nodel.logging;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * A class designed to be used as a singleton to publish logs at run-time.
 */
public class Logging {
    
    /**
     * (singleton)
     */
    private static Logging instance = new Logging();
    
    /**
     * Retrieves the shared instance.
     */
    public static Logging instance() {
        return instance;
    }
    
    /**
     * Instance lock / signal.
     */
    private Object lock = new Object();
    
    /**
     * Holds the event logs.
     * (locked around self)
     */
    private LinkedList<LogEntry> logs = new LinkedList<LogEntry>();

    /**
     * (constructor)
     */
    public Logging() {
        super();
    }
    
    public void addLog(LogEntry entry) {
        synchronized(this.lock) {
            this.logs.add(entry);
            
            if (this.logs.size() > 3000)
                this.logs.removeFirst();
        }
    } // (method)
    
    /**
     * Retrieves logs.
     */
    public List<LogEntry> getLogs(long from, int max) {
        LinkedList<LogEntry> batch = new LinkedList<LogEntry>();

        synchronized (this.lock) {
            Iterator<LogEntry> inReverse = this.logs.descendingIterator();
            while (inReverse.hasNext()) {
                LogEntry entry = inReverse.next();
                if (entry.seq >= from)
                    batch.add(entry);

                if (entry.seq < from || batch.size() >= max)
                    break;
            } // (while)

            return batch;
        }
    } // (method)
    
    /**
     * Same as 'getLogs' except filters out everything except WARNINGs and more serious.
     */
    public List<LogEntry> getWarningLogs(long from, int max) {
        return getLogsByLevel(from, max, Level.WARN);
    }
    
    /**
     * (this could have been rolled into one since it's only used once.)
     * (internal use)
     */
    private List<LogEntry> getLogsByLevel(long from, int max, Level filterLevel) {
        LinkedList<LogEntry> batch = new LinkedList<LogEntry>();
        
        synchronized (this.lock) {
            Iterator<LogEntry> inReverse = this.logs.descendingIterator();
            while (inReverse.hasNext()) {
                LogEntry entry = inReverse.next();
                // if (entry.seq >= from && entry.level.isAtLeastAsSpecificAs(filterLevel))
                if (entry.seq >= from && entry.level.isMoreSpecificThan(filterLevel))
                    batch.add(entry);

                if (entry.seq < from || batch.size() >= max)
                    break;
            } // (while)

            return batch;
        }
    } // (method)   

    /**
     * Clears all logs.
     */
    public void clear() {
        synchronized(this.lock) {
            this.logs.clear();
        }
    } // (method)    

} // (class)
