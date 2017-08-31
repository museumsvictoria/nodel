package org.nodel.jyhost;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.nodel.Handler;
import org.nodel.SimpleName;
import org.nodel.Strings;
import org.nodel.core.Nodel;
import org.nodel.core.NodelClients.NodeURL;
import org.nodel.diagnostics.AtomicLongMeasurementProvider;
import org.nodel.diagnostics.Diagnostics;
import org.nodel.discovery.AdvertisementInfo;
import org.nodel.io.Files;
import org.nodel.reflection.Serialisation;
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
    private ThreadPool _threadPool = new ThreadPool("Nodel host", 128);
    
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
        
        // schedule a maintenance run immediately
        _threadPool.execute(new Runnable() {

            @Override
            public void run() {
                doMaintenance();
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
        Map<SimpleName, File> currentFolders = new HashMap<SimpleName, File>();
        
        checkRoot(currentFolders, _root);
        for (File root : _otherRoots)
            checkRoot(currentFolders, root);

        synchronized (_signal) {
            // find all those that are not in current node map
            Map<SimpleName,File> newFolders = new HashMap<SimpleName,File>();
            
            for(Entry<SimpleName, File> entry : currentFolders.entrySet()) {
                if (!_nodeMap.containsKey(entry.getKey())) {
                    newFolders.put(entry.getKey(), entry.getValue());
                }
            } // (for)
            
            // find all those not in the new map
            Set<SimpleName> deletedNodes = new HashSet<SimpleName>();
            
            for(SimpleName name : _nodeMap.keySet()) {
                if (!currentFolders.containsKey(name))
                    deletedNodes.add(name);
            } // (for)
            
            // stop all the removed nodes
            
            for (SimpleName name : deletedNodes) {
                PyNode node = _nodeMap.get(name);
                
                _logger.info("Stopping and removing node '{}'", name);
                node.close();
                
                // count the removed node
                s_nodesCounter.decrementAndGet();
                
                _nodeMap.remove(name);
            } // (for)
            
            // start all the new nodes
            for (Entry<SimpleName, File> entry : newFolders.entrySet()) {
                _logger.info("Spinning up node " + entry.getKey() + "...");
                
                try {
                    PyNode node = new PyNode(this, entry.getValue());
                    
                    // count the new node
                    s_nodesCounter.incrementAndGet();
                    
                    // place into the map
                    _nodeMap.put(entry.getKey(), node);
                    
                } catch (Exception exc) {
                    _logger.warn("Node creation failed; ignoring." + exc);
                }
            } // (for)
        }
        
        // schedule a maintenance run into the future
        if (!_closed) {
            _timerThread.schedule(new TimerTask() {
                
                @Override
                public void run() {
                    doMaintenance();
                }
                
            }, PERIOD_MAINTENANCE);
        }
    } // (method)

    private void checkRoot(Map<SimpleName, File> currentFolders, File root) {
        for (File file : root.listFiles()) {
            // only include directories
            if (file.isDirectory()) {
                String filename = file.getName();
                
                // skip '_*' nodes...
                if (!filename.startsWith("_")
                        // ... skip 'New folder' folders and
                        && !filename.equalsIgnoreCase("New folder")

                        // ... skip '.*' nodes
                        && !filename.startsWith(".")

                        // apply any applicable inclusion / exclusion filters
                        && shouldBeIncluded(filename))
                    
                    currentFolders.put(new SimpleName(file.getName()), file);
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
    public void newNode(String base, String name) {
        if (Strings.isNullOrEmpty(name))
            throw new RuntimeException("No node name was provided");

        testNameFilters(name);

        // if here, name does not break any filtering rules
        
        // since the node may contain multiple files, create the node using in an 'atomic' way using
        // a temporary folder
        
        // TODO: should be able to select which root is applicable
        File newNodeDir = new File(_root, name);

        if (newNodeDir.exists())
            throw new RuntimeException("A node with the name '" + name + "' already exists.");

        if (Strings.isNullOrEmpty(base)) {
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
    public void testNameFilters(String name) {
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
    public boolean shouldBeIncluded(String name) {
        SimpleName simpleName = new SimpleName(name);
        
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
            if (Strings.isNullOrEmpty(filter))
                continue;

            String[] tokens = SimpleName.wildcardMatchTokens(filter);
            list.add(tokens);
        }

        return list;
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
