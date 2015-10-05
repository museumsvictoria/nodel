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
     * Holds the root directory that contains the other nodes, typically 'nodes'.
     * (initialised in constructor.)
     */
    private File _root;
    
    /**
     * Reflects the current running node configuration.
     */
    private Map<SimpleName, PyNode> _nodeMap = Collections.synchronizedMap(new HashMap<SimpleName, PyNode>());

    /**
     * (will/must never be null)
     */
    private List<String[]> _inclTokensFilters = Collections.emptyList();
    
    /**
     * (will/must never be null)
     */
    private List<String[]> _exclTokensFilters = Collections.emptyList();
    
    /**
     * Constructs a new NodelHost and returns immediately.
     */
    public NodelHost(File root, String[] inclFilters, String[] exclFilters) {
        if (root == null)
            _root = new File(".");
        else
            _root = root;
        
        _logger.info("NodelHost initialised. root='{}'", root.getAbsolutePath());
        
        // 'compile' the tokens list
        _inclTokensFilters = intoTokensList(inclFilters);
        _exclTokensFilters = intoTokensList(exclFilters);
        
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
    } // (constructor)
    
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
        for (File file : _root.listFiles()) {
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
                    PyNode node = new PyNode(entry.getValue());
                    
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

    /**
     * Creates a new node.
     */
    public void newNode(String name) {
        if (Strings.isNullOrEmpty(name))
            throw new RuntimeException("No node name was provided");

        File newNodeDir = new File(_root, name);

        if (newNodeDir.exists())
            throw new RuntimeException("A node with the name '" + name + "' already exists.");

        if (!newNodeDir.mkdir())
            throw new RuntimeException("The platform did not allow the creation of the node folder for unspecified reasons.");

        // the folder is created!
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
        if (_inclTokensFilters != null) {
            int len = _inclTokensFilters.size();

            for (int a = 0; a < len; a++) {
                // got at least one inclusion filter, so flip the mode
                // and wait for inclusion filter to match
                if (!filteringIn) {
                    filteringIn = true;
                    include = false;
                }

                String[] inclTokens = _inclTokensFilters.get(a);

                if (SimpleName.wildcardMatch(simpleName, inclTokens)) {
                    // flag it can be included and...
                    include = true;

                    // ...can bail out after first match
                    break;
                }
            }
        }

        // now check if there are any "opt-outs"
        if (_exclTokensFilters != null) {
            int len = _exclTokensFilters.size();

            for (int a = 0; a < len; a++) {
                String[] exclTokens = _exclTokensFilters.get(a);

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
    
}
