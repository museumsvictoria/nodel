package org.nodel.discovery;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import org.nodel.DateTimes;
import org.nodel.Handler;
import org.nodel.Random;
import org.nodel.SimpleName;
import org.nodel.core.NodeAddress;
import org.nodel.discovery.TopologyWatcher.ChangeHandler;
import org.nodel.threading.TimerTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Used for registering / unregistering nodes for discovery / lookup purposes. 
 */
public class NodelAutoDNS extends AutoDNS {
    
    /**
     * The period between probes when actively probing.
     * (millis)
     */
    static final int PROBE_PERIOD = 45000;

    /**
     * Expiry time (allow for at least one missing probe response)
     */
    private static final long STALE_TIME = 2 * PROBE_PERIOD + 10000;
    
    /**
     * The time before probing can be suspended if there haven't been
     * any recent 'list' or 'resolve' operation. 5 minutes.
     * 
     * (millis)
     */
    private static final long LIST_ACTIVITY_PERIOD = 5 * 60 * 1000;    

    /**
     * (logging)
     */
    private Logger _logger = LoggerFactory.getLogger(NodelAutoDNS.class);
    
    /**
     * General purpose lock / signal.
     */
    private Object _advertisementLock = new Object();
    
    /**
     * (locked by '_advertisementLock')
     */
    private Map<InetAddress, NodelAdvertiser> _advertisers = new HashMap<>();    

    /**
     * General purpose lock / signal.
     */    
    private Object _discoveryLock = new Object();
    
    /**
     * (locked by '_discoveryLock')
     */
    private Map<InetAddress, NodelDiscoverer> _discoverers = new HashMap<>();

    /**
     * Used in '_services'. 
     */
    class ServiceItem {
        
        SimpleName _name;
        
        public ServiceItem(SimpleName name) {
            _name = name;
        }
        
    } // (class)
    
    /**
     * Holds the registered service items.
     */
    private ConcurrentMap<SimpleName, ServiceItem> _services = new ConcurrentHashMap<SimpleName, ServiceItem>();
    
    /**
     * Holds the collected advertisements.
     */
    private ConcurrentMap<SimpleName, AdvertisementInfo> _advertisements = new ConcurrentHashMap<SimpleName, AdvertisementInfo>();

    /**
     * Whether or not we're probing for client. It will probe on start up and then deactivate.
     * e.g. 'list' is called.
     * (nanos)
     */
    private AtomicLong _lastList = new AtomicLong(System.nanoTime());
    
    /**
     * The last time a probe occurred.
     * (nanos)
     */
    private AtomicLong _lastProbe = new AtomicLong(0);
    
    /**
     * Whether or not client resolution is being used. This affects whether polling
     * should take place. 
     * (one way switch)
     */
    private volatile boolean _usingResolution = false;

    /**
     * Response for reaping old advertisements
     */
    private TimerTask _cleanupTimer;
    
    /**
     * Probe timing.
     */
    private TimerTask _probeTimer;
    
    /**
     * (graceful logging)
     */
    private boolean _suppressProbeLog = false;

    /**
     * (reference required for cleanup)
     */
    private ChangeHandler _topologyChangeCallback = new TopologyWatcher.ChangeHandler() {

        @Override
        public void handle(List<InetAddress> appeared, List<InetAddress> disappeared) {
            handleTopologyChanged(appeared, disappeared);
        }

    };

    /**
     * (singleton constructor)
     */
    private NodelAutoDNS() {
        // bind to topology manager
        TopologyWatcher.shared().addOnChangeHandler(_topologyChangeCallback);
        
        // kick off the cleanup tasks timer
        _cleanupTimer = Discovery.timerThread().schedule(new TimerTask() {

            @Override
            public void run() {
                handleCleanupTimer();
            }

        }, 60000, 60000);
        
        // kick off the client prober to start
        // after 5s to 10s (randomly chosen)
        _probeTimer = Discovery.timerThread().schedule(new TimerTask() {

            @Override
            public void run() {
                handleProbeTimer();
            }

        }, (long) (5000 + Random.shared().nextDouble() * 5000), PROBE_PERIOD);        

        _logger.info("Nodel discovery services started. probePeriod:{}, stalePeriodAllowed:{}",
                DateTimes.formatShortDuration(PROBE_PERIOD), DateTimes.formatShortDuration(STALE_TIME));
    }

    /**
     * When the network topology changes (network interfaces appear or disappear)
     * (callback)
     */
    protected void handleTopologyChanged(List<InetAddress> appeared, List<InetAddress> disappeared) {
        synchronized (_discoveryLock) {
            for (InetAddress gone : disappeared)
                _discoverers.remove(gone).shutdown();
        }

        synchronized (_discoveryLock) {
            for (InetAddress newAddr : appeared) {
                final NodelDiscoverer discoverer = new NodelDiscoverer(newAddr);
                discoverer.setProbeResponseHandler(new Handler.H1<NameServicesChannelMessage>() {

                    @Override
                    public void handle(NameServicesChannelMessage message) {
                        handleProbeResponse(discoverer, message);
                    }

                });
                _discoverers.put(newAddr, discoverer);
                discoverer.start();
            }
        }
        
        synchronized (_advertisementLock) {
            for (InetAddress gone : disappeared)
                _advertisers.remove(gone).shutdown();
        }

        synchronized (_advertisementLock) {
            for (InetAddress newAddr : appeared) {
                final NodelAdvertiser advertiser = new NodelAdvertiser(newAddr);
                advertiser.setServicesSnapshotProvider(new Handler.F0<Collection<ServiceItem>>() {

                    @Override
                    public Collection<ServiceItem> handle() {
                        // return a thread-safe collection
                        return _services.values();
                    }
                    
                });
                _advertisers.put(newAddr, advertiser);
                advertiser.start();
            }
        }
    }

    /**
     * When a probe response occurs. 
     */
    private void handleProbeResponse(NodelDiscoverer discoverer, NameServicesChannelMessage message) {
        synchronized (_discoveryLock) {
            for (String name : message.present) {
                SimpleName node = new SimpleName(name);
                AdvertisementInfo ad = _advertisements.get(node);
                if (ad == null) {
                    ad = new AdvertisementInfo();
                    ad.name = node;
                    _advertisements.put(node, ad);
                }

                // refresh the name (its original name might be different), time stamp and addresses
                ad.name = node;
                ad.timeStamp = System.nanoTime() / 1000000;
                ad.addresses = message.addresses;
            }
        }
    }

    /**
     * The client timer; determines whether probing is actually necessary
     * (timer entry-point)
     */
    private void handleProbeTimer() {
        if (_usingResolution) {
            // client names are being resolved, so stay probing
            _suppressProbeLog = false;
            sendProbes();
            
        } else {
            // the time difference in millis
            long listDiff = (System.nanoTime() - _lastList.get()) / 1000000L;

            if (listDiff < LIST_ACTIVITY_PERIOD) {
                _suppressProbeLog = false;
                sendProbes();
                
            } else {
                if (!_suppressProbeLog) {
                    _logger.info("Probing is paused because it has been more than {} since a 'list' or 'resolve' (total {}).", 
                            DateTimes.formatShortDuration(LIST_ACTIVITY_PERIOD), DateTimes.formatShortDuration(listDiff));
                }

                _suppressProbeLog = true;
            }
        }
    }

	/**
     * Handle clean-up tasks
     * (timer entry-point)
     */
    private void handleCleanupTimer() {
        long currentTime = System.nanoTime();

        reapStaleRecords(currentTime);
    }
    
    /**
     * Checks for stale records and removes them.
     */
    private void reapStaleRecords(long currentTime) {
        LinkedList<AdvertisementInfo> toRemove = new LinkedList<AdvertisementInfo>();

        synchronized (_discoveryLock) {
            for (AdvertisementInfo adInfo : _advertisements.values()) {
                long timeDiff = (currentTime / 1000000) - adInfo.timeStamp;

                if (timeDiff > STALE_TIME)
                    toRemove.add(adInfo);
            }

            // reap if necessary
            if (toRemove.size() > 0) {
                StringBuilder sb = new StringBuilder();

                for (AdvertisementInfo adInfo : toRemove) {
                    if (sb.length() > 0)
                        sb.append(",");

                    _advertisements.remove(adInfo.name);
                    sb.append(adInfo.name);
                }

                _logger.info("{} stale record{} removed. [{}]",
                        toRemove.size(), toRemove.size() == 1 ? " was" : "s were", sb.toString());
            }
        }
    }

    @Override
    public NodeAddress resolveNodeAddress(SimpleName node) {
    	// indicate client resolution is being used
    	_usingResolution = true;
    	
        AdvertisementInfo adInfo = _advertisements.get(node);
        if(adInfo != null) {
            Collection<String> addresses = adInfo.addresses;
            
            for (String address : addresses) {
                try {
                    if (address == null || !address.startsWith("tcp://"))
                        continue;

                    int indexOfPort = address.lastIndexOf(':');
                    if (indexOfPort < 0 || indexOfPort >= address.length() - 2)
                        continue;

                    String addressPart = address.substring(6, indexOfPort);

                    String portStr = address.substring(indexOfPort + 1);

                    int port = Integer.parseInt(portStr);

                    NodeAddress nodeAddress = NodeAddress.create(addressPart, port);

                    return nodeAddress;

                } catch (Exception exc) {
                    _logger.info("'{}' node resolved to a bad address - '{}'; ignoring.", node, address);

                    return null;
                }
            }
        }
        
        return null;
    }

    @Override
    public void registerService(SimpleName node) {
        synchronized (_advertisementLock) {
            if (_services.containsKey(node))
                throw new IllegalStateException(node + " is already being advertised.");

            ServiceItem si = new ServiceItem(node);

            _services.put(node, si);
        }
    } // (method)

    @Override
    public void unregisterService(SimpleName node) {
        synchronized(_advertisementLock) {
            if (!_services.containsKey(node))
                throw new IllegalStateException(node + " is not advertised anyway.");

            _services.remove(node);
        }        
    }    

    @Override
    public Collection<AdvertisementInfo> list() {
    	long now = System.nanoTime();
    	
    	_lastList.set(now);
    	
    	// check how long it has been since the last probe (millis)
        long timeSinceProbe = (now - _lastProbe.get()) / 1000000L;

        if (timeSinceProbe > LIST_ACTIVITY_PERIOD)
            sendProbes();

        // create snap-shot
        List<AdvertisementInfo> ads = new ArrayList<AdvertisementInfo>(_advertisements.size());
        
        synchronized (_discoveryLock) {
            ads.addAll(_advertisements.values());
        }
        
        return ads;
    }
    
    private void sendProbes() {
        _lastProbe.set(System.nanoTime());
        
        synchronized (_discoveryLock) {
            for (NodelDiscoverer discoverer : _discoverers.values())
                discoverer.sendProbe();
        }
    }

    @Override
    public AdvertisementInfo resolve(SimpleName node) {
        // indicate client resolution is being used
        _usingResolution = true;
        
        return _advertisements.get(node);
    }

    /**
     * Permanently shuts down all related resources.
     */
    @Override
    public void close() throws IOException {
        TopologyWatcher.shared().removeOnChangeHandler(_topologyChangeCallback);
        
        _cleanupTimer.cancel();
        
        _probeTimer.cancel();
        
        // shut down all existing discovered
        synchronized(_discoveryLock) {
            for (InetAddress intf : new ArrayList<>(_discoverers.keySet())) {
                _discoverers.remove(intf).shutdown();
            }
        }
    }
    
    /**
     * Creates or returns the shared instance.
     */
    public static AutoDNS create() {
        return Instance.INSTANCE;
    }
    
    /**
     * (singleton, thread-safe, non-blocking)
     */
    private static class Instance {
        private static final NodelAutoDNS INSTANCE = new NodelAutoDNS();
    }
    
    /**
     * Returns the singleton instance of this class.
     */
    public static NodelAutoDNS instance() {
        return Instance.INSTANCE;
    }

}
