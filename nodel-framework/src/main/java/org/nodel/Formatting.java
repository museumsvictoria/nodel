package org.nodel;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.text.DecimalFormat;

/**
 * Does some utility functioning to perform formatting.
 * 
 * Mainly byte/bit based - see http://en.wikipedia.org/wiki/Kilobyte
 */
public class Formatting {

    private static DecimalFormat s_formatter = new DecimalFormat("###,###");
    
    /**
     * The decimal formatter ("###,###").
     */
    public static DecimalFormat decimalFormatter() {
        return s_formatter;
    }

    /**
     * Sensibly formats a data lengths into presentable "GB", "MB", "KB" values. 
     */
    public static String formatByteLength(long value) {
        if (value > 1024L * 1024 * 1024 * 1024)
            return s_formatter.format(value / 1024 / 1024 / 1024) + " GB";

        else if (value > 1024L * 1024 * 1024)
            return s_formatter.format(value / 1024 / 1024) + " MB";

        else if (value > 1024L * 1024)
            return s_formatter.format(value / 1024) + " KB";

        else if (value > 1024L)
            return s_formatter.format(value / 1024) + " KB";
        
        else if (value == 1)
            return value + " byte";

        else
            return s_formatter.format(value) + " bytes";
    }
    
    /**
     * Sensibly formats a data rate into "bps", "Kbps", "Mbps", etc.
     */
    public static String formatBitRate(long bytes, long timeInMills) {
        // calculate bits per second
        double rate = bytes * 8d / (timeInMills / 1000);
        
        return formatBitRate(rate);
    }
    
    /**
     * Sensibly formats a data rate into "bps", "Kbps", "Mbps", etc.
     */
    public static String formatBitRateUsingNanos(long bytes, long timeInNanos) {
        // calculate bits per second
        double rate = bytes * 8d / (timeInNanos / 1000 / 1000000);
        
        return formatBitRate(rate);
    }    
    
    /**
     * Sensibly formats a data rate into "bps", "Kbps", "Mbps", etc.
     */    
    public static String formatBitRate(double bitRate) {
        if (bitRate > 1000L * 1000 * 1000 * 1000)
            return s_formatter.format(bitRate / 1000 / 1000 / 1000) + " Gbps";

        else if (bitRate > 1000L * 1000 * 1000)
            return s_formatter.format(bitRate / 1000 / 1000) + " Mbps";

        else if (bitRate > 1000L * 1000)
            return s_formatter.format(bitRate / 1000) + " kbps";

        else if (bitRate > 1000L)
            return s_formatter.format(bitRate / 1000) + " kbps";
        
        else
            return s_formatter.format(bitRate) + " bps";        
    }
    
} // (class)
