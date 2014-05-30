package org.nodel.jyhost;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nodel.Handler;
import org.nodel.SimpleName;
import org.nodel.core.Framework;
import org.nodel.core.Nodel;
import org.nodel.core.NodelClients.NodeURL;
import org.nodel.discovery.AdvertisementInfo;
import org.nodel.logging.AtomicLongMeasurementProvider;
import org.nodel.threading.ThreadPool;
import org.nodel.threading.TimerTask;
import org.nodel.threading.Timers;

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
        Framework.shared().registerCounter("nodel_host_nodecount", new AtomicLongMeasurementProvider(s_nodesCounter), false);
    }

    /**
     * (logging related)
     */
    protected Logger _logger = LogManager.getLogger(this.getClass().getName() + "_" + s_instanceCounter.getAndIncrement());
    
    /**
     * (threading)
     */
    private ThreadPool _threadPool = new ThreadPool("nodel_host", 128);
    
    /**
     * (threading)
     */
    private Timers _timerThread = new Timers("nodel_host");
    
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
     * Constructs a new NodelHost and returns immediately.
     */
    public NodelHost(File root) {
        if (root == null)
            _root = new File(".");
        else
            _root = root;
        
        _logger.info("NodelHost initialised. root='{}'", root.getAbsolutePath());
        
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
            // (skip "_" prefixed and 'New folder' names)
            if (file.isDirectory() && !file.getName().startsWith("_") && (!file.getName().equalsIgnoreCase("New folder"))) {
                currentFolders.put(new SimpleName(file.getName()), file);
            }
        } // (for)
        
        synchronized(_signal) {
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
    } // (method)
    
} // (class)
