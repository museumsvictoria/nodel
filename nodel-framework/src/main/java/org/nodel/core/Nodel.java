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
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.nodel.Environment;
import org.nodel.Handler;
import org.nodel.Handlers;
import org.nodel.SimpleName;
import org.nodel.Strings;
import org.nodel.core.NodelClients.NodeURL;
import org.nodel.discovery.AdvertisementInfo;
import org.nodel.discovery.AutoDNS;
import org.nodel.io.UnexpectedIOException;

public class Nodel {

    private final static String VERSION = "2.2.1";

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
     * Reduces a string into a simple version i.e. no spaces, only letters and digits and removes
     * comments i.e. starts with starts with "--" or between round brackets.
     */
    public static String reduce(String name) {
        return reduce(name, false);
    }
    
    /**
     * (same as 'reduce(name)' but with 'removeCommentsOnly' keeping spaces, etc.
     */
    public static String reduce(String name, boolean removeCommentsOnly) {
        int len = name.length();
        StringBuilder sb = new StringBuilder(len);
        
        char lastChar = 0;
        
        int commentLevel = 0;

        for (int a = 0; a < len; a++) {
            char c = name.charAt(a);
            
            // deal with '(___)' comments, nested too
            if (c == '(') {
                commentLevel += 1;
            }
            
            else if (commentLevel > 0) {
                if (c == ')')
                    commentLevel -= 1;
                else
                    ; // don't capture
            }
            
            // deal with '-- ___' and '// ___' comments
            else if (c == '-' && lastChar == '-' || c == '/' && lastChar == '/')
                // ignore everything afterwards
                break;
            
            else if (removeCommentsOnly)
                sb.append(c);
            
            else if (Character.isLetterOrDigit(c))
                sb.append(c);
            
            else if (c > 127 && !Character.isSpaceChar(c))
                // every other extended ASCII and Unicode character
                // is significant (except space characters e.g. \u00A0 NO-BREAK SPACE)
                sb.append(c);
            
            // store last char for comments
            lastChar = c;
        } // (for)
        
        return sb.toString();
    } // (method)
    
    /**
     * Reduces a string into a simple comparable version i.e. lower-case, no spaces, only letters and digits. 
     */
    public static String reduceToLower(String name) {
        return SimpleName.flatten(name);
    }    
    
    /**
     * (overloaded)
     * 'ignore' - A list of characters pass-through regardless.
     */
    public static String reduceToLower(String name, char[] passthrough) {
        return SimpleName.flatten(name, passthrough);
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
            
            if (c == '*')
                sb.append('*');
            
            else
                // this method also performs filtering
                SimpleName.flattenChar(c, sb);
        }
        
        return sb.toString();
    }    
    
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
     * The interfaces Nodel will bind to.
     */
    private static String[] interfacesToUse;

    /**
     * Gets the interfaces that Nodel will bind to (or null if automatic binding should be done)
     */
    public static String[] getInterfacesToUse() {
        return interfacesToUse;
    }
    
    /**
     * Sets the interface that Nodel should bind to.
     */
    public static void setInterfacesToUse(String[] intfs) {
        interfacesToUse = intfs;
    }
    
    /**
     * Returns all the advertised nodes.
     */
    public static Collection<AdvertisementInfo> getAllNodes() {
        return AutoDNS.instance().list();
    }
    
    /**
     * Returns all the advertised nodes.
     */
    public static List<NodeURL> getNodeURLs() throws IOException {
        return NodelClients.instance().getAllNodesURLs();
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
     * Gets a node's URLs
     */
    public static List<NodeURL> getNodeURLsForNode(SimpleName name) throws IOException {
        return NodelClients.instance().getNodeURLs(name);
    }
    
    
    /**
     * (see public getter / setter)
     */
    private static int s_messagingPort = 0; // e.g. "tcp://IP_ADDR:PORT" or "udp://IP_ADDR:PORT"

    /**
     * The messaging port (native Nodel) for this environment, TCP & UDP (reserved)
     */
    public static int getMessagingPort() {
        return s_messagingPort;
    }
    
    /**
     * (see getter)
     */
    public static void updateMessagingPort(int value) {
        s_messagingPort = value;
    }    
    
    
    /**
     * (see public getter / setter)
     */
    private static int s_tcpPort = 0; // e.g. "tcp://IP_ADDR:PORT"

    /**
     * The TCP port (native Nodel) for this environment.
     */
    public static int getTCPPort() {
        return s_tcpPort;
    }
    
    /**
     * Sets the TCP address for this environment.
     */
    public static void updateTCPPort(int port) {
        s_tcpPort = port;
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

    /**
     * (see public getter / setter)
     */
    private static String[] s_httpAddresses = new String[] { String.format("http://127.0.0.1") };
    
    /**
     * (see public getter / setter)
     */
    private static String[] s_httpNodeAddresses = new String[] { String.format("http://127.0.0.1/node") };

    /**
     * Updated by host environment.
     */
    public static void updateHTTPAddresses(String[] httpAddress, String[] httpNodeAddress) {
        s_httpAddresses = httpAddress;
        s_httpNodeAddresses = httpNodeAddress;
    }
    
    /**
     * The server's HTTP address for this host environment.
     */
    public static String[] getHTTPAddresses() {
        return s_httpAddresses;
    }
    
    /**
     * A node's HTTP address for this host environment.
     */
    public static String[] getHTTPNodeAddress() {
        return s_httpNodeAddresses;
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
        if (!Strings.isBlank(javaRuntime))
            sb.append(" java/").append(javaRuntime.replace(' ', '_'));
        
        String vendor = System.getProperty("java.vm.vendor");
        if (!Strings.isBlank(vendor))
            sb.append(' ').append(vendor.replace(' ', '_'));        

        String os = System.getProperty("os.name");
        if (!Strings.isBlank(os))
            sb.append(' ').append(os.replace(' ', '_'));

        String arch = System.getProperty("sun.arch.data.model");
        if (!Strings.isBlank(arch))
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
    
    /**
     * (see getter)
     */
    private static String s_hostPath = "";
    
    /**
     * (see getter)
     */
    public static void setHostPath(String value) {
        s_hostPath = value;
    }

    /**
     * The host path (location) 
     */
    public static String getHostPath() {
        return s_hostPath;
    }
    
    /**
     * (see getter)
     */
    private static String s_hostingRule = "";
    
    /**
     * (see getter)
     */
    public static void setHostingRule(String value) {
        s_hostingRule = value;
    }

    /**
     * The hosting rule that applies.
     */
    public static String getHostingRule() {
        return s_hostingRule;
    }
    
    /**
     * (see getter)
     */
    private static String s_nodesRoot = "";

    /**
     * (see getter)
     */
    public static void setNodesRoot(String value) {
        s_nodesRoot = value;
    }

    /**
     * The nodes root path (location)
     */
    public static String getNodesRoot() {
        return s_nodesRoot;
    }
    
    /**
     * (see setter)
     * (must never be null)
     */
    private static List<InetAddress> s_hardLinksAddresses = Collections.emptyList();

    /**
     * Gets the list of direct multicast addresses to assist with inconvenient or unreliable multicasting.
     * (never returns null; items never null)
     */
    public static List<InetAddress> getHardLinksAddresses() {
        return s_hardLinksAddresses;
    }
    
    /**
     * Sets the direct multicast addresses list. Using null resets the list.
     */
    public static void setHardLinksAddresses(List<String> addresses) {
        if (addresses == null || addresses.size() == 0) {
            s_hardLinksAddresses = Collections.emptyList();
            return;
        }

        List<InetAddress> list = new ArrayList<InetAddress>();
        for (String address : addresses) {
            try {
                list.add(InetAddress.getByName(address));
                
            } catch (IOException exc) {
                throw new UnexpectedIOException(exc);
            }
        }

        s_hardLinksAddresses = list;
    }
    
    /**
     * Gets the PID of the current process.
     */
    public static int getPID() {
        return Environment.instance().getPID();
    }

}
