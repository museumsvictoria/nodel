package org.nodel.jyhost;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.nodel.Handler;
import org.nodel.SimpleName;
import org.nodel.Strings;
import org.nodel.Threads;
import org.nodel.Tuple;
import org.nodel.Version;
import org.nodel.core.Nodel;
import org.nodel.core.NodelClients.NodeURL;
import org.nodel.core.NodelServerEvent;
import org.nodel.diagnostics.AtomicLongMeasurementProvider;
import org.nodel.diagnostics.Diagnostics;
import org.nodel.discovery.AdvertisementInfo;
import org.nodel.discovery.TopologyWatcher;
import org.nodel.host.BaseNode;
import org.nodel.io.Files;
import org.nodel.io.UTF8Charset;
import org.nodel.reflection.Reflection;
import org.nodel.reflection.Serialisation;
import org.nodel.rest.REST;
import org.nodel.threading.ThreadPool;
import org.nodel.threading.TimerTask;
import org.nodel.threading.Timers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A nodel host is responsible for spawning and managing nodes. 
 */
public class NodelHost {
    
    /**
     * The maintenance period (10 sec)
     */
    private static final long PERIOD_MAINTENANCE = 10000;

    /**
     * (logging related)
     */
    private static AtomicLong s_instanceCounter = new AtomicLong();
    
    /**
     * (diagnostics)
     */    
    private static AtomicLong s_nodesCounter = new AtomicLong();
    
    /**
     * (diagnostics)
     */
    static {
        Diagnostics.shared().registerCounter("Nodel host.Node count", new AtomicLongMeasurementProvider(s_nodesCounter), false);
    }

    /**
     * (logging related)
     */
    protected Logger _logger = LoggerFactory.getLogger(this.getClass().getName() + "_" + s_instanceCounter.getAndIncrement());
    
    /**
     * (threading)
     */
    private ThreadPool _threadPool = new ThreadPool("Nodel host", 4);
    
    /**
     * (threading)
     */
    private Timers _timerThread = new Timers("Nodel host");
    
    /**
     * General purpose lock / signal.
     */
    private Object _signal = new Object();
    
    /**
     * When permanently closed (disposed)
     */
    private boolean _closed;
    
    /**
     * (see public getter, init. in constructor.)
     */
    private File _root;
    
    /**
     * Holds the root directory that contains the other nodes, typically 'nodes'.
     */
    public File getRoot() {
        return _root;
    }
    
    /**
     * Additional roots (see 'root')
     */
    private List<File> _otherRoots = new ArrayList<File>();
    
    /**
     * Reflects the current running node configuration.
     */
    private Map<SimpleName, PyNode> _nodeMap = Collections.synchronizedMap(new HashMap<SimpleName, PyNode>());

    /**
     * regex for node name templates, e.g. "Special Display ($MODEL, ${MAC ADDRESS})" or "${HOSTNAME} Computer"
     */
    private static Pattern NODE_NAME_TEMPLATE_REGEX = Pattern.compile("\\$\\{(.+?)\\}|\\$([a-zA-Z\\d]+)");

    /**
     * Convenience map value
     */
    class FolderInfo {

        /**
         * The actual folder name (never changes)
         */
        final SimpleName folderName;

        /**
         * Has a $NAME or ${...} present (never changes, see dynamic naming below)
         */
        final boolean isTemplate;

        /**
         * The node associated with this folder (to be updated)
         */
        PyNode node;

        FolderInfo(SimpleName folderName) {
            this.folderName = folderName;
            this.isTemplate = NODE_NAME_TEMPLATE_REGEX.matcher(folderName.getOriginalName()).find();
        }

    }

    /**
     * The node folders in use.
     */
    private Map<File, FolderInfo> _nodeFolders = new HashMap<>();
    
    /**
     * As specified by user.
     */
    private String[] _origInclFilters;

    /**
     * (will/must never be null)
     */
    private List<String[]> _inclTokensFilters = Collections.emptyList();
    
    /**
     * As specified by user.
     */    
    private String[] _origExclFilters;
    
    /**
     * (will/must never be null)
     */
    private List<String[]> _exclTokensFilters = Collections.emptyList();

    /**
     * Constructs a new NodelHost and returns immediately.
     */
    public NodelHost(File root, String[] inclFilters, String[] exclFilters, File recipesRoot) {
        _root = (root == null ? new File(".") : root);
        
        _recipes = new RecipesEndPoint(recipesRoot);
        
        _logger.info("NodelHost initialised. root='{}', recipesRoot='{}'", root.getAbsolutePath(), recipesRoot.getAbsoluteFile());
        
        Nodel.setHostPath(new File(".").getAbsolutePath());
        Nodel.setNodesRoot(_root.getAbsolutePath());
        
        setHostingFilters(inclFilters, exclFilters);
        
        // attach to some error handlers
        Nodel.attachNameRegistrationFaultHandler(new Handler.H2<SimpleName, Exception>() {
            
            @Override
            public void handle(SimpleName node, Exception exception) {
                handleNameRegistrationFault(node, exception);
            }
            
        });
        
        // schedule a maintenance immediately after topology is known
        TopologyWatcher.shared().addOnChangeHandler(new TopologyWatcher.ChangeHandler() {

            @Override
            public void handle(List<InetAddress> appeared, List<InetAddress> disappeared) {
                doMaintenance();

                // periodic maintenance scheduling is taken care of elsewhere so decouple
                TopologyWatcher.shared().removeOnChangeHandler(this);
            }

        });

        s_instance = this;
    } // (constructor)
    
    /**
     * Sets other (secondary) roots
     */
    public void setOtherRoots(List<String> roots) {
        List<File> fileRoots = new ArrayList<File>();
        for (String root : roots)
            fileRoots.add(new File(root));
        
        _otherRoots = fileRoots;
    }
    
    /**
     * (see setter)
     */
    public List<File> getOtherRoots() {
        return _otherRoots;
    }

    /**
     * Used to adjust filters on-the-fly.
     */
    public void setHostingFilters(String[] inclFilters, String[] exclFilters) {
        _origInclFilters = inclFilters;
        _origExclFilters = exclFilters;
        
        StringBuilder hostingRule = new StringBuilder();
        hostingRule.append("Include ")
                   .append(inclFilters == null || inclFilters.length == 0 ? "everything" : Serialisation.serialise(inclFilters))
                   .append(", exclude ")
                   .append(exclFilters == null || exclFilters.length == 0 ? "nothing" : Serialisation.serialise(exclFilters));
        Nodel.setHostingRule(hostingRule.toString());
        
        // 'compile' the tokens list
        _inclTokensFilters = intoTokensList(inclFilters);
        _exclTokensFilters = intoTokensList(exclFilters);
    }
    
    /**
     * Returns the current hosting filters.
     */
    public String[][] getHostingFilters() {
        return new String[][] { _origInclFilters, _origExclFilters };
    }
    
    /**
     * Fault handler for name registration issues.
     */
    protected void handleNameRegistrationFault(SimpleName node, Exception exc) {
        synchronized(_signal) {
            // get the node if it is present
            PyNode pyNode = _nodeMap.get(node);
            if (pyNode == null)
                return;
            
            pyNode.notifyOfError(exc);
        }
    } // (method)

    /**
     * Returns the map of the nodes.
     */
    public Map<SimpleName, PyNode> getNodeMap() {
        return _nodeMap;
    } // (method)

    /**
     * Performs background maintenance including spinning up and winding down nodes.
     * (timer entry-point)
     */
    private void doMaintenance() {
        if (_closed)
            return;
        
        // get all directories
        // (do this outside synchronized loop because it is IO dependent)
        Map<SimpleName, File> currentFolders = new HashMap<>();
        
        checkRoot(currentFolders, _root);
        for (File root : _otherRoots)
            checkRoot(currentFolders, root);

        synchronized (_signal) {
            // find all those that are not in current folder list
            Map<SimpleName, File> newFolders = new HashMap<>();
            
            for(Entry<SimpleName, File> entry : currentFolders.entrySet()) {
                SimpleName name = entry.getKey();
                File folder = entry.getValue();
                if (!_nodeFolders.containsKey(folder))
                    newFolders.put(name, folder);
            }
            
            // find all those not in the new map
            Map<File, SimpleName> deletedFolders = new HashMap<>();
            
            for(Entry<File, FolderInfo> existing : _nodeFolders.entrySet()) {
                File existingFolder = existing.getKey();
                FolderInfo folderInfo = existing.getValue();
                SimpleName existingName = folderInfo.folderName;
                
                if (!currentFolders.containsValue(existingFolder))
                    deletedFolders.put(existingFolder, existingName);
            }
            
            // stop all the removed nodes
            
            for (Entry<File, SimpleName> entry : deletedFolders.entrySet()) {
                File folder = entry.getKey();
                SimpleName name = entry.getValue();
                
                PyNode node = _nodeFolders.get(folder).node;
                
                _logger.info("Stopping and removing node [{}]", name);
                node.close();
                
                // count the removed node
                s_nodesCounter.decrementAndGet();
                
                _nodeMap.remove(node.getName());
                _nodeFolders.remove(folder);
            } // (for)
            
            // start all the new nodes
            for (Entry<SimpleName, File> entry : newFolders.entrySet()) {
                SimpleName folderName = entry.getKey();
                File folder = entry.getValue();
                FolderInfo folderInfo = new FolderInfo(folderName);
                
                SimpleName nodeName = folderInfo.isTemplate ? SimpleName.intoSimple(tryExpandTemplate(folderInfo)) : folderName;  
                
                _logger.info("Spinning up node [" + nodeName + "]...");
                
                try {
                    PyNode node = new PyNode(this, nodeName, folder);
                    folderInfo.node = node;
                    
                    // count the new node
                    s_nodesCounter.incrementAndGet();
                    
                    // place into the map
                    _nodeMap.put(entry.getKey(), node);
                    _nodeFolders.put(folder, folderInfo);
                    
                } catch (Exception exc) {
                    _logger.warn("Node creation failed; ignoring", exc);
                }
            } // (for)
            
            // to avoid a race condition, only do template expansion if no new nodes were started on this pass
            if (newFolders.size() == 0)
                checkForTemplates();
        }
        
        // schedule a maintenance run into the future
        if (!_closed) {
            _timerThread.schedule(_threadPool, new TimerTask() {
                
                @Override
                public void run() {
                    doMaintenance();
                }
                
            }, PERIOD_MAINTENANCE);
        }
    } // (method)

    /**
     * Check for any name templates
     */
    private void checkForTemplates() {
        List<Tuple.T2<FolderInfo, SimpleName>> toRename = new ArrayList<>(); // 2nd item is 'expandedName'

        // deal with nodes with name templates
        for (FolderInfo folderInfo : _nodeFolders.values()) {
            if (!folderInfo.isTemplate)
                // skip
                continue;

            PyNode node = folderInfo.node;
            SimpleName existingName = node.getName(); // e.g. "$HOSTNAME Computer"

            CharSequence newName = tryExpandTemplate(folderInfo); // e.g. "DESKTOP-VVCUA Computer"
            
            if (newName == null || existingName.getOriginalName().contentEquals(newName))
                // name is not different, nothing to do
                continue;

            // needs renaming (must be done safely outside of enumeration loop)
            toRename.add(new Tuple.T2<>(folderInfo, SimpleName.intoSimple(newName)));
        }

        // safely rename if required
        for (Tuple.T2<FolderInfo, SimpleName> entry : toRename) {
            FolderInfo folderInfo = entry.getItem1();
            SimpleName expandedName = entry.getItem2();

            PyNode node = folderInfo.node;
            File root = node.getRoot();
            SimpleName existingName = node.getName();

            try {
                _logger.info("Node name expansion resulted in new node (was [{}]); shutting down...", existingName);
                node.close();
                _nodeMap.remove(existingName);
                folderInfo.node = null;

                _logger.info("... changing name to [{}] and starting node", expandedName);
                node = new PyNode(this, expandedName, root);

                // update field to new node instance
                folderInfo.node = node;

                // and replace in map
                _nodeMap.put(expandedName, folderInfo.node);

            } catch (Exception exc) {
                _logger.warn("Node creation failed during name expansion; will retry on next round", exc);
            }
        } // (for)
    }

    private void checkRoot(Map<SimpleName, File> currentFolders, File root) {
        File[] files = root.listFiles();
        
        // cannot assume the order is stable
        Arrays.sort(files, new Comparator<File>() {

            @Override
            public int compare(File o1, File o2) {
                return o1.getName().compareTo(o2.getName());
            }});
        
        for (File file : files) {
            // only include directories
            if (file.isDirectory()) {
                String filename = file.getName();
                SimpleName name = decodeFilenameIntoName(filename);
                
                // skip '_*' nodes...
                if (!filename.startsWith("_")
                        // ... skip 'New folder' folders and
                        && !filename.equalsIgnoreCase("New folder")

                        // ... skip '.*' nodes
                        && !filename.startsWith(".")

                        // apply any applicable inclusion / exclusion filters
                        && shouldBeIncluded(name)
                        
                        // node names might reduce to the same thing, so select first one only
                        && !currentFolders.containsKey(name)) {
                    
                    currentFolders.put(name, file);
                }
            }
        } // (for)
    }
    
    /**
     * (init. in constructor)
     */
    private RecipesEndPoint _recipes;
    
    /**
     * The Recipes end-point 
     */
    public RecipesEndPoint recipes() { return _recipes; }

    /**
     * Creates a new node.
     */
    public void newNode(String base, SimpleName name) {
        testNameFilters(name);

        // if here, name does not break any filtering rules
        
        // since the node may contain multiple files, create the node using in an 'atomic' way using
        // a temporary folder
        
        // TODO: should be able to select which root is applicable
        File newNodeDir = new File(_root, encodeIntoSafeFilename(name));

        if (_nodeMap.containsKey(name) || newNodeDir.exists())
            throw new RuntimeException("A node with the name '" + name + "' already exists.");

        if (base == null) {
            // not based on existing node, so just create an empty folder
            // and the node will do the rest
            if (!newNodeDir.mkdir())
                throw new RuntimeException("The platform did not allow the creation of the node folder for unspecified reasons (security issues?).");
            
        } else {
            // based on an existing node (from recipes folder or self nodes)
            File baseDir = _recipes.getRecipeFolder(base);

            if (baseDir == null)
                throw new RuntimeException("Could not locate base recipe - " + base);

            // copy the entire folder
            Files.copyDir(baseDir, newNodeDir);
        }
    }

    /**
     * Same as 'shouldBeIncluded' but throws exception with naming conflict error details.
     */
    public void testNameFilters(SimpleName name) {
        if (!shouldBeIncluded(name)) {
            String[] ins = _origInclFilters == null ? new String[] {} : _origInclFilters;
            String[] outs = _origExclFilters == null ? new String[] {} : _origExclFilters;
            throw new RuntimeException("Name rejected because this node host applies node filtering (includes: " +
                    Serialisation.serialise(ins) + ", excludes: " + Serialisation.serialise(outs) + ")");
        }
    }

    /**
     * Processes a name through inclusion and exclusion lists. (convenience instance function)
     */
    public boolean shouldBeIncluded(SimpleName simpleName) {
        // using filtering? 
        boolean filteringIn = false;

        // by default, it's included
        boolean include = true;

        // check if any exclusive inclusion filters apply
        List<String[]> inclTokensFilters = _inclTokensFilters;
        if (inclTokensFilters != null) {
            int len = inclTokensFilters.size();

            for (int a = 0; a < len; a++) {
                // got at least one inclusion filter, so flip the mode
                // and wait for inclusion filter to match
                if (!filteringIn) {
                    filteringIn = true;
                    include = false;
                }

                String[] inclTokens = inclTokensFilters.get(a);

                if (SimpleName.wildcardMatch(simpleName, inclTokens)) {
                    // flag it can be included and...
                    include = true;

                    // ...can bail out after first match
                    break;
                }
            }
        }

        // now check if there are any "opt-outs"
        List<String[]> exclTokensFilters = _exclTokensFilters;
        if (exclTokensFilters != null) {
            int len = exclTokensFilters.size();

            for (int a = 0; a < len; a++) {
                String[] exclTokens = exclTokensFilters.get(a);

                if (SimpleName.wildcardMatch(simpleName, exclTokens)) {
                    // flag it must be excluded
                    include = false;

                    // ... bail out
                    break;
                }
            }
        }

        return include;
    }
    
    /**
     * Renames a node.
     */
    public void renameNode(PyNode node, SimpleName newName) {
        if (newName == null)
            throw new RuntimeException("No node name was provided");

        File root = node.getRoot();

        File newNodeDir = new File(_root, NodelHost.encodeIntoSafeFilename(newName));

        if (newNodeDir.exists())
            throw new RuntimeException("A node with the name '" + newName + "' already exists.");

        // this will throw an exception if name filtering rules are broken
        testNameFilters(newName);

        // close the node
        node.close();

        // try a few times in case of blocking cleanup
        int triesLeft = 5;
        for (; triesLeft > 0; triesLeft--) {
            if (root.renameTo(newNodeDir))
                break;

            Threads.safeWait(_signal, 1000);
            
            // and try some more...
        }

        if (triesLeft <= 0)
            throw new RuntimeException("The platform did not allow the renaming of the node folder for unspecified reasons.");
    }
    
    public Collection<AdvertisementInfo> getAdvertisedNodes() {
        return Nodel.getAllNodes();
    }
    
    public List<NodeURL> getNodeURLs() throws IOException {
        return getNodeURLs(null);
    }
    
    public List<NodeURL> getNodeURLs(String filter) throws IOException {
        return Nodel.getNodeURLs(filter);
    }
    
    public List<NodeURL> getNodeURLsForNode(SimpleName name) throws IOException {
        return Nodel.getNodeURLsForNode(name);
    }    
    
    /**
     * Permanently shuts down this nodel host
     */
    public void shutdown() {
        _logger.info("Shutdown called.");
        
        _closed = true;
        
        synchronized(_signal) {
            for(PyNode node : _nodeMap.values()) {
                try {
                    node.close();
                } catch (Exception exc) {
                }
            } // (for)            
        }
        
        Nodel.shutdown();
    }
    
    /**
     * Converts a list of simple filters into tokens that are used more efficiently later when matching.
     */
    private static List<String[]> intoTokensList(String[] filters) {
        if (filters == null)
            return Collections.emptyList();

        List<String[]> list = new ArrayList<String[]>();

        for (int a = 0; a < filters.length; a++) {
            String filter = filters[a];
            if (Strings.isBlank(filter))
                continue;

            String[] tokens = SimpleName.wildcardMatchTokens(filter);
            list.add(tokens);
        }

        return list;
    }
    
    /**
     * Avoiding strict UTF-8 encoding by treating same as safe for ALL OSs
     * 
     * NOTE: These are not allowed in Windows: \ / : * ? " < >|
     */
    private final static char[] TREAT_AS_SAFE = " ()[]{}'&^$#@!`~;.+=-_,".toCharArray();
    
    /**
     * Encodes a name into a multi-platform friendly version using URL encoding (%) as little as possible. 
     */
    protected static String encodeIntoSafeFilename(SimpleName name) {
        StringBuilder sb = new StringBuilder();
        String originalName = name.getOriginalName();
        int len = originalName.length();
        
        for (int a = 0; a < len; a++) {
            char c = originalName.charAt(a);
            
            if (SimpleName.isPresent(c, TREAT_AS_SAFE)) {
                sb.append(c);
                
            } else {
                try {
                    sb.append(URLEncoder.encode(String.valueOf(c), "UTF-8"));
                    
                } catch (UnsupportedEncodingException e) {
                    throw new RuntimeException("Encoding unexpectedly failed.", e);
                }
            }
        }
        
        return sb.toString();
    }
    
    /**
     * Uses URL encoding ('%' escaping) to decode the filename.
     */
    protected static SimpleName decodeFilenameIntoName(String raw) {
        boolean encoded = false;
        
        int len = raw.length();
        
        for (int a = 0; a < len; a++) {
            if (raw.charAt(a) == '%') {
                encoded = true;
                break;
            }
        }
        
        if (!encoded)
            return new SimpleName(raw);
        
        // otherwise go to the effort of decoding
        
        StringBuilder sb = new StringBuilder(len);
        
        // for building up the raw UTF-8 bytes
        byte[] buffer = new byte[len];
        int bIndex = 0;
        
        for (int i = 0; i < len; i++) {
            char c = raw.charAt(i);
            
            if (c == '%') {
                try {
                    // grab next 2 bytes; encoded byte values to follow so add or extend the buffer...
                    buffer[bIndex++] = (byte) (Integer.parseInt(raw.substring(i + 1, i + 3), 16) & 0xff);
                    
                    i += 2;
                    
                } catch (Exception exc) {
                    // gracefully continue
                    sb.append(c);
                }
                
            } else {
                // pass through the characters but ...
                
                if (bIndex > 0) {
                    // ...if there's anything in the buffer, UTF-8 decode it
                    sb.append(new String(buffer, 0, bIndex, UTF8Charset.instance()));
                    
                    // reset collection
                    bIndex = 0;
                }
                
                // add the character
                sb.append(c);
            }
        }
        
        // ... must do at end too
        if (bIndex > 0)
            sb.append(new String(buffer, 0, bIndex, UTF8Charset.instance()));
        
        return new SimpleName(sb.toString());
    }
    
    /**
     * Performs variable substitution using the name template regex.
     * 
     * @param folderInfo pre-checked the 'folderInfo.isTemplate' is true
     * @return a valid string
     */
    private static CharSequence tryExpandTemplate(FolderInfo folderInfo) {
        String template = folderInfo.folderName.getOriginalName();

        // get all variables ('folderInfo.isTemplate' is pre-checked as true so a match will always be found)
        Matcher matcher = NODE_NAME_TEMPLATE_REGEX.matcher(template);

        StringBuffer result = new StringBuffer(template.length() * 2);
        
        while (matcher.find()) {
            String varName = matcher.group(1);
            if (varName == null)
                varName = matcher.group(2);

            SimpleName simpleName = new SimpleName(varName); // won't be sensitive to "${}" characters

            Object value = tryLookupAndSubstitute(simpleName, folderInfo.node);

            if (value != null)
                matcher.appendReplacement(result, Matcher.quoteReplacement(value.toString()));
            else
                matcher.appendReplacement(result, Matcher.quoteReplacement(varName));
        }

        matcher.appendTail(result);

        return result;
    }

    /**
     * Tries to lookup up a variable value from convenient sources
     */
    private static Object tryLookupAndSubstitute(SimpleName name, BaseNode node) {
        // try as a signal name fist
        if (node != null) {
            NodelServerEvent signal = node.getLocalEvents().get(name);
            if (signal != null)
                return signal.getArg();
        }

        // otherwise try some other convenient things to lookup

        String original = name.getOriginalName();
        String reduced = name.getReducedForMatchingName();

        if (reduced.equals("hostname")) {
            String result = TopologyWatcher.shared().getHostname();
            // and use matching case
            return original.startsWith("HOST") ? result.toUpperCase() : (original.startsWith("host") ? result.toLowerCase() : result);
        }

        if (reduced.equals("ipaddress") || reduced.equals("ipaddresses") || reduced.equals("ip"))
            return String.join(", ", TopologyWatcher.shared().getIPAddresses());

        if (reduced.equals("macaddress") || reduced.equals("macaddresses") || reduced.equals("mac")) {
            String result = String.join(", ", TopologyWatcher.shared().getMACAddresses());
            // and use matching case
            return original.startsWith("MAC") ? result.toUpperCase() : (original.startsWith("mac") ? result.toLowerCase() : result);
        }

        if (reduced.equals("httpport"))
            return Nodel.getHTTPPort();

        if (reduced.equals("version"))
            return Version.shared().version;

        if (reduced.equals("hostingrule"))
            return Nodel.getHostingRule();

        // try against Diagnostics (which shadows most of Nodel class static methods)
        try {
            if (Reflection.getValueInfosByName(Diagnostics.class, original) != null) // test first to avoid NotFoundException below
                return REST.resolveRESTcall(Diagnostics.shared(), new String[] {original}, null, null, false);
        } catch (Exception e) {
            // ignore and fall-through
        }

        // try host environment variables
        String value = System.getenv(original);
        if (!Strings.isBlank(value))
            return value;

        // try Java platform property
        value = System.getProperty(original);
        if (!Strings.isBlank(value))
            return value;
        
        // RESERVED for further lookups

        return null;
    }

    /**
     * (init. in constructor)
     */
    public static NodelHost s_instance;
    
    /**
     * Returns the first instance.
     */
    public static NodelHost instance() {
        return s_instance; 
    }
    
}
