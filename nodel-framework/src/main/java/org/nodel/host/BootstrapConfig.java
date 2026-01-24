package org.nodel.host;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.nodel.Strings;
import org.nodel.Version;
import org.nodel.core.Nodel;
import org.nodel.json.JSONObject;
import org.nodel.reflection.Reflection;
import org.nodel.reflection.Serialisation;
import org.nodel.reflection.Value;
import org.nodel.reflection.ValueInfo;

/**
 * Critical configuration that is used before general initialisation. 
 */
public class BootstrapConfig {

    public final static BootstrapConfig Example;

    static {
        Example = new BootstrapConfig();
        Example.networkInterfaces = networkInterfaceNames();
        Example.inclFilters = new String[] { "Main Campus *", "Campus 2*" };
        Example.exclFilters = new String[] { "Campus 3*" };
    }

    public final static int DEFAULT_NODELHOST_PORT = 8085;

    @Value(name = "NodelHostPort", title = "NodelHost HTTP port", order = 200, required = true, desc = "(command-line arg '-p')")
    private int nodelHostPort = DEFAULT_NODELHOST_PORT;

    public int getNodelHostPort() {
        return this.nodelHostPort;
    }

    public void setPyNodePort(int value) {
        this.nodelHostPort = value;
    }

    public final static int DEFAULT_MESSAGING_PORT = 0;

    @Value(name = "messagingPort", title = "Messaging Port", order = 210, required = true, 
           desc = "TCP & UDP (reserved) port for the native node-to-node messaging protocol to run on; normally '0' meaning any port. (command-line arg '--messagingPort')")
    private int messagingPort = DEFAULT_MESSAGING_PORT;

    public int getMessagingPort() {
        return this.messagingPort;
    }

    public void setMessagingPort(int value) {
        this.messagingPort = value;
    }    

    
    public final static String DEFAULT_NODELROOT_DIRECTORY = "nodes";

    @Value(name = "nodelRoot", title = "Nodel root directory", order = 250, required = false, 
            desc="(command-line arg '-r')")
    private String nodelRoot = DEFAULT_NODELROOT_DIRECTORY;

    public String getNodelRoot() {
        return this.nodelRoot;
    }

    public void setNodelRoot(String value) {
        this.nodelRoot = value;
    }    
    
    public final static String DEFAULT_CONTENT_DIRECTORY = ".nodel/webui_cache";

    @Value(name = "contentDirectory", title = "Content directory", order = 300, required = true)
    private String contentDirectory = DEFAULT_CONTENT_DIRECTORY;

    public String getContentDirectory() {
        return this.contentDirectory;
    }

    public void setContentDirectory(String value) {
        this.contentDirectory = value;
    }

    public final static String DEFAULT_CACHE_DIRECTORY = "cache";

    @Value(name = "cacheDirectory", title = "Cache directory", order = 500, required = true)
    private String cacheDirectory = DEFAULT_CACHE_DIRECTORY;

    public String getCacheDirectory() {
        return this.cacheDirectory;
    }

    public void setCacheDirectory(String value) {
        this.cacheDirectory = value;
    }

    @Value(name = "networkInterfaces", title = "Network interface", order = 800, required = false, desc = "Use network interface opt in instead of automatic bind all (command-line arg '--interface ... [--interface ...]') Use -? to dump interface list. Specify interface name.")
    private String[] networkInterfaces = null;

    public String[] getNetworkInterfaces() {
        return this.networkInterfaces;
    }

    public void setNetworkInterface(String[] value) {
        this.networkInterfaces = value;
    }

    @Value(name = "disableAdvertisements", title = "Disable advertisements", order = 900, required = false)
    private boolean disableAdvertisements = false;

    public boolean getDisableAdvertisements() {
        return this.disableAdvertisements;
    }

    public void setNetworkInterface(boolean value) {
        this.disableAdvertisements = value;
    }

    
    public final static boolean DEFAULT_ENABLE_PROGRAM_LOGGING = false;

    @Value(name = "enableProgramLogging", title = "Enable program logging", order = 1000, required = false, desc = "(command-line arg '-l')")
    private boolean enableProgramLogging = DEFAULT_ENABLE_PROGRAM_LOGGING;

    public boolean getEnableProgramLogging() {
        return this.enableProgramLogging;
    }
    
    public void setEnableProgramLogging(boolean value) {
        this.enableProgramLogging = value;
    }
    

    public final static String DEFAULT_LOG_DIRECTORY = "logs";

    @Value(name = "logsDirectory", title = "Logs directory", order = 1050, required = false,
            desc = "Can be relative or absolute path.")
    private String logsDirectory = DEFAULT_LOG_DIRECTORY;

    public String getLogsDirectory() {
        return this.logsDirectory;
    }

    public void setLogsDirectory(String value) {
        this.logsDirectory = value;
    }    
    

    @Value(name = "inclFilters", title = "Node inclusions", order = 1100, required = false, 
            desc = "If specified, exclusively hosts nodes (matched using simple glob-based pattern matching, e.g. 'Main Campus*'). " + 
                   "Multiple patterns can be specified. (command-line arg '-I ... [-I ...]')")
    private String[] inclFilters = null;

    public String[] getInclFilters() {
        return this.inclFilters;
    }

    public void setIncludes(String[] include) {
        this.inclFilters = include;
    }
    
    @Value(name = "exclFilters", title = "Node exclusions", order = 1200, required = false, 
            desc = "If specified, opts-out node hosting (matched using simple glob-based pattern matching, e.g. 'Campus 2*'). " + 
                   "Multiple patterns can be specified. (command-line arg '-X ... [-X ...]')")
    private String[] exclFilters = null;

    public String[] getExclFilters() {
        return this.exclFilters;
    }

    public void setExclFilters(String[] exclude) {
        this.exclFilters = exclude;
    }
    

    public final static String DEFAULT_RECIPESROOT_DIRECTORY = "recipes";

    @Value(name = "recipesRoot", title = "Recipes directory", order = 1300, required = false, 
            desc="(command-line arg '--recipes')")
    public String recipesRoot = DEFAULT_RECIPESROOT_DIRECTORY;
    
    
    /**
     * Overrides current instances fields with command-line like ones.
     * TODO: ideally this should be a generic command-line args parser via Serialisation class and ValueInfos.
     */
    public void overrideWith(String[] args) {
        if (args == null || args.length == 0)
            // nothing to do
            return;

        // specially handle list-type arguments
        Map<Character, List<String>> lists = new HashMap<Character, List<String>>();

        for (int a = 0; a < args.length; a++) {
            String arg = args[a];

            // the subsequent arg (if present)
            String nextArg = null;

            if (a + 1 < args.length)
                nextArg = args[a + 1];
            
            if ("-p".equals(arg) || "--NodelHostPort".equalsIgnoreCase(arg)) {
                this.nodelHostPort = Integer.parseInt(nextArg);

            } else if ("-r".equals(arg) || "--nodelRoot".equalsIgnoreCase(arg)) {
                this.nodelRoot = nextArg;

            } else if ("--interface".equalsIgnoreCase(arg)) {
                List<String> list = lists.get('i');
                if (list == null) {
                    list = new ArrayList<String>();
                    lists.put('i', list);
                }

                list.add(nextArg);
                
            } else if ("-l".equals(arg) || "--enableProgramLogging".equalsIgnoreCase(arg)) {
                this.enableProgramLogging = true;

            } else if ("--contentDirectory".equalsIgnoreCase(arg)) {
                this.contentDirectory = nextArg;

            } else if ("--cacheDirectory".equalsIgnoreCase(arg)) {
                this.cacheDirectory = nextArg;

            } else if ("--disableAdvertisements".equalsIgnoreCase(arg)) {
                this.disableAdvertisements = true;

            } else if ("--logsDirectory".equalsIgnoreCase(arg)) {
                this.logsDirectory = nextArg;

            } else if ("--recipes".equalsIgnoreCase(arg)) {
                this.recipesRoot = nextArg;
                
            } else if ("--messagingPort".equalsIgnoreCase(arg)) {
                this.messagingPort = Integer.parseInt(nextArg);

            } else if ("-I".equals(arg) || "--inclFilter".equalsIgnoreCase(arg)) {
                List<String> list = lists.get('I');
                if (list == null) {
                    list = new ArrayList<String>();
                    lists.put('I', list);
                }

                list.add(nextArg);
            } else if ("-X".equals(arg) || "--exclFilter".equalsIgnoreCase(arg)) {
                List<String> list = lists.get('X');
                if (list == null) {
                    list = new ArrayList<String>();
                    lists.put('X', list);
                }

                list.add(nextArg);
                
            } else if ("-?".equals(arg) || "/?".equals(arg) || "--help".equalsIgnoreCase(arg)) {
                dumpHelpAndQuit();
            }
        } // (for)

        // go through the lists
        List<String> list = lists.get('X');
        if (list != null)
            this.exclFilters = (String[]) Serialisation.coerce(String[].class, list);

        list = lists.get('I');
        if (list != null)
            this.inclFilters = (String[]) Serialisation.coerce(String[].class, list);

        list = lists.get('i');
        if (list != null)
            this.networkInterfaces = (String[]) Serialisation.coerce(String[].class, list);
    }
    
    /**
     * Dumps bootstrap and command-line argument help and quits.
     */
    private static void dumpHelpAndQuit() {
        System.out.println("// Usage: (also see github.com/museumsvictoria/nodel/wiki)");
        System.out.println();

        System.out.println("// (bootstrap.json structure and command-line arguments,");
        System.out.println("//  also see _bootstrap_example.json and _bootstrap_schema.json)");
        System.out.println("{ \"bootstrap\": {");
        ValueInfo[] options = Reflection.getValueInfos(BootstrapConfig.class);
        for (int i = 0; i < options.length; i++) {
            String name = options[i].name;
            String desc = options[i].desc;
            if (Strings.isBlank(desc))
                desc = String.format("(command-line arg '--%s')", name);
            System.out.format("    \"%s\": %s%n", name, JSONObject.quote(desc));
        }
        System.out.println("} }");

        System.out.println();
        System.out.println("// Available network interfaces:");
        System.out.println("// (use --interface ... [--interface ...] to enable opt-in interface binding)");
        System.out.println("{ \"networkInterfaces\": [");

        String[] names = networkInterfaceNames();
        for (int i = 0; i < names.length; i++) {
            try {
                System.out.format("    \"%s\"%s  // %s%n", names[i], i != names.length - 1 ? "," : "", NetworkInterface.getByName(names[i]).getDisplayName());

            } catch (Exception exc) {
                // ignore
            }

        } // (for)
        System.out.println("}");

        System.out.println();
        System.out.println("// Nodel agent will be:");
        System.out.format("{ \"agent\": %s }%n", JSONObject.quote(Nodel.getAgent()));
        
        System.out.println();
        System.out.println("// Release info:");
        System.out.format("{ \"version\": %s }%n", JSONObject.quote(Version.shared().version));

        System.exit(0);
    }

    /**
     * Dumps the list of interfaces that support multicast.
     * (convenience method)
     */
    private static String[] networkInterfaceNames() {
        List<String> names = new ArrayList<>();

        try {
            for (NetworkInterface nis : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nis.supportsMulticast())
                    continue;
                
                boolean gotIPv4 = false;
                for (InetAddress addr : Collections.list(nis.getInetAddresses())) {
                    if (addr instanceof Inet4Address) {
                        gotIPv4 = true;
                        break;
                    }
                }
                
                if (!gotIPv4)
                    continue;                

                names.add(nis.getName());
            }
            
            return names.toArray(new String[names.size()]);
            
        } catch (Exception exc) {
            return new String[] {};
        }
    }

} // (class)
