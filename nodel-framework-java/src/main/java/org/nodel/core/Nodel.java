package org.nodel.core;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.nodel.Handler;
import org.nodel.Handlers;
import org.nodel.SimpleName;
import org.nodel.Strings;
import org.nodel.core.NodelClients.NodeURL;
import org.nodel.discovery.AdvertisementInfo;
import org.nodel.discovery.AutoDNS;

// TODO: use one of these to implement character normalisation:
// http://stackoverflow.com/questions/1008802/converting-symbols-accent-letters-to-english-alphabet

public class Nodel {

    private final static String VERSION = "2.1.1";

    public static String getVersion() {
        return VERSION;
    }

    /**
     * For sequence counting, starting at current time to get a unique, progressing sequence number every 
     * time (regardless of restart).
     */
    private static AtomicLong s_seqCounter = new AtomicLong(System.currentTimeMillis());
    
    public static long getSeq() {
        return s_seqCounter.get();
    }
    
    public static long getNextSeq() {
        return s_seqCounter.getAndIncrement();
    }
   

    /**
     * Performs string matching using Nodel reduction rules i.e. lower-case, no spaces, only letters and digits.
     */
    public static boolean nameMatch(String str1, String str2) {
        if (str1 == null && str2 == null)
            return false;
        
        if (str1 == null)
            return false;
        
        if (str2 == null)
            return false;
        
        return reduceToLower(str1).equals(reduceToLower(str2));
    } // (method)
    
    /**
     * Reduces a string into a simple version i.e. no spaces, only letters and digits. 
     */
    public static String reduce(String name) {
        int len = name.length();
        StringBuilder sb = new StringBuilder(len);

        for (int a = 0; a < len; a++) {
            char c = name.charAt(a);
            
            if (Character.isLetterOrDigit(c))
                sb.append(c);
            
            else if (c <= 32)
                continue;
            
            else if (c > 127)
                sb.append(c);            
        } // (for)
        
        return sb.toString();
    } // (method)
    
    /**
     * Reduces a string into a simple comparable version i.e. lower-case, no spaces, only letters and digits. 
     */
    public static String reduceToLower(String name) {
        return reduceToLower(name, null);
    }    
    
    /**
     * (overloaded)
     * 'ignore' - A list of characters pass-through regardless.
     */
    public static String reduceToLower(String name, char[] passthrough) {
        int len = name.length();
        StringBuilder sb = new StringBuilder(len);

        for (int a = 0; a < len; a++) {
            char c = name.charAt(a);
            
            if (passthrough != null && isPresent(c, passthrough))
                sb.append(Character.toLowerCase(c));
            
            else if (Character.isLetterOrDigit(c))
                sb.append(Character.toLowerCase(c));
            
            else if (c <= 32)
                continue;
            
            else if (c > 127)
                sb.append(Character.toLowerCase(c));
        } // (for)
        
        return sb.toString();
    } // (method)
    
    /**
     * Returns true if a character is present in a list of them (held within a string)
     * ('chars' must be pre-checked)
     */
    private static boolean isPresent(char c, char[] chars) {
        int len = chars.length;
        for (int a = 0; a < len; a++) {
            if (chars[a] == c)
                return true;
        }
        return false;
    }
    
    /**
     * Reduces a string into a simple comparable version i.e. lower-case, no spaces, only letters and digits.
     * (allows '*' for filter)
     */
    public static String reduceFilter(String filter) {
        int len = filter.length();
        StringBuilder sb = new StringBuilder(len);

        for (int a = 0; a < len; a++) {
            char c = filter.charAt(a);
            
            if (Character.isLetterOrDigit(c) || c == '*')
                sb.append(Character.toLowerCase(c));
        } // (for)
        
        return sb.toString();
    } // (method)    
    
    /**
     * Performs a wild card matching for the text and pattern 
     * provided.
     * 
     * @param text the text to be tested for matches.
     * 
     * @param pattern the pattern to be matched for.
     * This can contain the wild card character '*' (asterisk).
     * 
     * @return <tt>true</tt> if a match is found, <tt>false</tt> 
     * otherwise.
     */
    public static boolean filterMatch(String text, String filter) {
        // Create the cards by splitting using a RegEx. If more speed
        // is desired, a simpler character based splitting can be done.
        Collection<String> parts = split(reduceFilter(filter), '*');

        // iterate over the cards.
        for (String card : parts) {
            int i = text.indexOf(card);

            // card not detected in the text.
            if (i == -1)
                return false;

            // move ahead, towards the right of the text.
            text = text.substring(i + card.length());
        } // (for)

        return true;
    } // (method)
    
    /**
     * Fast, simple string splitting by char. Assumes string is prefiltered (trimmed, etc).
     */
    private static List<String> split(String string, char delim) {
        List<String> parts = new LinkedList<String>();

        StringBuilder sb = new StringBuilder();

        int len = string.length();

        for (int a = 0; a < len; a++) {
            char c = string.charAt(a);

            if (c == delim) {
                if (sb.length() != 0) {
                    parts.add(sb.toString());
                    sb.setLength(0);
                }
            } else {
                sb.append(c);
            }
        } // (for)

        if (sb.length() != 0) {
            parts.add(sb.toString());
        }

        return parts;
    } // (method)
    
    /**
     * The interface Nodel will bind to.
     */
    private static InetAddress interfaceToUse;

    /**
     * Gets the interface that Nodel will bind to.
     */
    public static InetAddress getInterfaceToUse() {
        return interfaceToUse;
    }
    
    /**
     * Sets the interface that Nodel should bind to.
     */
    public static void setInterfaceToUse(InetAddress inetAddr) {
        interfaceToUse = inetAddr;
        
        // immediately update the MDNS interface
        AutoDNS.setInterface(inetAddr);
    }
    
    /**
     * Returns all the advertised nodes.
     */
    public static Collection<AdvertisementInfo> getAllNodes() {
        return AutoDNS.instance().list();
    }
    
    /**
     * Returns all the advertised nodes.
     * @throws IOException 
     */
    public static List<NodeURL> getNodeURLs() throws IOException {
        return NodelClients.instance().getNodeURLs();
    }
    
    /**
     * Same as 'getNodeURLS' with filter.
     */
    public static List<NodeURL> getNodeURLs(String filter) throws IOException {
        List<NodeURL> urls = Nodel.getNodeURLs();
        
        if (Strings.isNullOrEmpty(filter))
            return urls;
        
        String lcFilter = filter.toLowerCase();
        
        ArrayList<NodeURL> filteredList = new ArrayList<NodeURL>();
        for (NodeURL url : urls) {
            if (url.node.getOriginalName().toLowerCase().contains(lcFilter))
            filteredList.add(url);
        }
        
        return filteredList;
    }    
    
    /**
     * (see public getter / setter)
     */
    private static int httpPort;

    /**
     * The HTTP port for this environment.
     */
    public static int getHTTPPort() {
        return httpPort;
    }
    
    /**
     * Sets the HTTP port for this environment.
     */
    public static void setHTTPPort(int value) {
        httpPort = value;
    }
    
    public static String s_httpAddress;

    public static void updateHTTPAddress(String httpAddress) {
        s_httpAddress = httpAddress;
    }
    
    public static String getHTTPAddress() {
        return s_httpAddress;
    }
    
    /**
     * (see public getter / setter)
     */
    private static int webSocketPort;

    /**
     * The WebSocket port for this environment.
     */
    public static int getWebSocketPort() {
        return webSocketPort;
    }
    
    /**
     * Sets the WebSocket port for this environment.
     */
    public static void setWebSocketPort(int value) {
        webSocketPort = value;
    }
    
    public static String s_webSocketAddress;

    public static void updateWebSocketAddress(String webSocketAddress) {
        s_webSocketAddress = webSocketAddress;
    }
    
    public static String getWebSocketAddress() {
        return s_webSocketAddress;
    }    
    
    /**
     * The default HTTP suffix (e.g. '/nodes/%NODE%/')
     */
    private static String s_defaultHTTPSuffix = "/nodes/%NODE%/";
    
    /**
     * Gets the HTTP suffix.
     */
    public static String getHTTPSuffix() {
        return s_defaultHTTPSuffix;
    }
    
    /**
     * Sets the default suffix.
     */
    public static void setHTTPSuffix(String value) {
        s_defaultHTTPSuffix = value;
    }    
    
    /**
     * Whether to disable server advertisements.
     */
    private static boolean disableServerAdvertisements = false;

    /**
     * Whether to disable server advertisements.
     */
    public static boolean getDisableServerAdvertisements() {
        return disableServerAdvertisements;
    }
    
    /**
     * Sets whether to disable server advertisements.
     */
    public static void setDisableServerAdvertisements(boolean value) {
        disableServerAdvertisements = value;
    }
    
    /**
     * Permanently shuts down all Nodel related services.
     */
    public static void shutdown() {
        NodelServers.instance().shutdown();
        NodelClients.instance().shutdown();
    }
    
    /**
     * Used to classes within this package to report name registration failures related to specific nodes.
     * (will never be null)
     */
    public static Handlers.H2<SimpleName, Exception> onNameRegistrationFault = new Handlers.H2<SimpleName, Exception>();
    
    public static void attachNameRegistrationFaultHandler(Handler.H2<SimpleName, Exception> handler) {
        onNameRegistrationFault.addHandler(handler);
    } // (method)
    
    public static void detachNameRegistrationFaultHandler(Handler.H2<SimpleName, Exception> handler) {
        onNameRegistrationFault.removeHandler(handler);
    } // (method)
    
    /**
     * See 'getAgent()'
     */
    private static String s_agent = formatAgent();
    
    private static String formatAgent() {
        StringBuilder sb = new StringBuilder();

        sb.append("nodel/").append(VERSION);

        String javaRuntime = System.getProperty("java.runtime.version");
        if (!Strings.isNullOrEmpty(javaRuntime))
            sb.append(" java/").append(javaRuntime.replace(' ', '_'));
        
        String vendor = System.getProperty("java.vm.vendor");
        if (!Strings.isNullOrEmpty(vendor))
            sb.append(' ').append(vendor.replace(' ', '_'));        

        String os = System.getProperty("os.name");
        if (!Strings.isNullOrEmpty(os))
            sb.append(' ').append(os.replace(' ', '_'));

        String arch = System.getProperty("sun.arch.data.model");
        if (!Strings.isNullOrEmpty(arch))
            sb.append(" arch").append(arch.replace(' ', '_'));

        return sb.toString();
    }
    
    /**
     * Returns a Browser "user-agent" like string. Something like:
     * nodel/2.0.5 java/1.7.0_71-b14_Oracle_Corporation arch64 Windows/8.1
     */
    public static String getAgent() {
        return s_agent;
    }
    
}
