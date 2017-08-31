package org.nodel;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

/**
 * Mainly contains DateTime convenience functions.
 */
public class DateTimes {

    /**
     * Formats a duration (in millis) in a short variably
     * precise format. e.g. '2d 1h', '2m 1s', '2m 10s'
     */
    public static String formatShortDuration(long duration) {
        
        long value = Math.abs(duration);
        
        long days = (value / 3600 / 1000) / 24;
        long hours = (value / 3600 / 1000) % 24;
        long minutes = (value / 1000 / 60) % 60;
        long seconds = (value / 1000) % 60;
        long millis = value % 1000;
        
        String signPart = (duration < 0 ? "-" : "");
        
        if (days > 0)
            return signPart + days + "d " + hours + "h";

        if (hours > 0)
            return signPart + hours + "h " + minutes + "m";
        
        if (minutes > 0)
            return signPart + minutes + "m " + seconds + "s";
        
        if (seconds > 0)
            return signPart + seconds + "s " + millis + "ms";
        
        else
            return signPart + millis + "ms";
    } // (method)
    
    /**
     * Returns a period between two timestamps (in nanos).
     * Used with 'System.nanoTime()' as a base.
     */
    public static String formatPeriod(long startNanos, long stopNanos) {
        return DateTimes.formatShortDuration((stopNanos - startNanos) / 1000000);
    } // (method)
    
    /**
     * Returns a period between now and a given timestamp (in nanos).
     * Used with 'System.nanoTime()' as a base.
     */
    public static String formatPeriod(long startNanos) {
        return DateTimes.formatShortDuration((System.nanoTime() - startNanos) / 1000000);
    } // (method)    

} // (class)
