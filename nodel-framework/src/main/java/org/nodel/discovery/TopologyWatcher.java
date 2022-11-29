package org.nodel.discovery;

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
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.nodel.Formatting;
import org.nodel.core.Nodel;
import org.nodel.threading.ThreadPool;
import org.nodel.threading.TimerTask;
import org.nodel.threading.Timers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tracks changes to the network interface topology e.g. networks appearing and disappearing.
 * 
 * Starts after a short delay and then periodically (every ~2 mins)
 */
public class TopologyWatcher {
    
    /**
     * (convenience)
     */
    public static InetAddress IPv4Loopback = Discovery.parseNumericalIPAddress("127.0.0.1");
    
    /**
     * (convenience)
     */
    public static InetAddress AllInterface = Discovery.parseNumericalIPAddress("0.0.0.0");    

    /**
     * (logging)
     */
    private Logger _logger = LoggerFactory.getLogger(TopologyWatcher.class);
    
    /**
     * (convenience reference)
     */
    private ThreadPool _threadPool = Discovery.threadPool();
    
    /**
     * (convenience reference)
     */
    private Timers _timerThread = Discovery.timerThread();
    
    /**
     * The last active ones.
     */
    private Set<InetAddress> _lastActiveSet = new HashSet<InetAddress>();
    
    /**
     * (see addChangeHandler())
     */
    public static interface ChangeHandler {
        
        public void handle(List<InetAddress> appeared, List<InetAddress> disappeared);
        
    }

    /**
     * Newly registered callbacks. For thread safety, these move to 'onChangeHandlers' on first fire.
     * (synchronized)
     */
    private final Set<ChangeHandler> _newOnChangeHandlers = new HashSet<ChangeHandler>();
    
    /**
     * Holds unregistered handlers
     * (synchronized)
     */
    private final Set<ChangeHandler> _removedOnChangeHandlers = new HashSet<ChangeHandler>();
    
    /**
     * Active set callbacks
     * (only modified by one thread)
     */
    private final List<ChangeHandler> _onChangeHandlers = new ArrayList<ChangeHandler>();
    
    /**
     * The snapshot of the active interfaces list (null until first hardware poll)
     */
    private InetAddress[] _interfacesSnapshot;
    
    /**
     * Snapshot of related MAC addresses (colon separated strings) (never null)
     */
    private List<String> _macAddressesSnapshot = Collections.emptyList();
    
    /**
     * Snapshot of IP addresses (dotted numerical strings)
     */
    private List<String> _ipAddressesSnapshot = Collections.emptyList();    
    
    /**
     * Snapshot of hostname if known else "UNKNOWN")
     */
    private String _hostnameSnapshot = "UNKNOWN";
    
    /**
     * Hostname snapshot (or "UNKNOWN")
     */
    public String getHostname() {
        return _hostnameSnapshot;
    }    

    /**
     * Adds a callback for topology changes (order is "new interfaces", "old interfaces")
     */
    public void addOnChangeHandler(ChangeHandler handler) {
        synchronized (_newOnChangeHandlers) {
            _newOnChangeHandlers.add(handler);
        }
    }

    /**
     * (see related 'add__Handler')
     */
    public void removeOnChangeHandler(ChangeHandler handler) {
        synchronized (_removedOnChangeHandlers) {
            _removedOnChangeHandlers.add(handler);
        }
        // in case of 'add' then immediate 'remove'
        synchronized (_newOnChangeHandlers) {
            _newOnChangeHandlers.remove(handler);
        }
    }

    /**
     * (private constructor)
     */
    private TopologyWatcher() {
        kickOffTimer();
    }

    /**
     * (singleton, thread-safe, non-blocking)
     */
    private static class Instance {

        private static final TopologyWatcher INSTANCE = new TopologyWatcher();

    }

    /**
     * Returns the singleton instance of this class.
     */
    public static TopologyWatcher shared() {
        return Instance.INSTANCE;
    }
    
    /**
     * For adjustable polling intervals.
     */
    private int[] POLLING_INTERVALS = new int[] {2000, 5000, 15000, 15000, 60000, 120000};
    
    /**
     * (relates to POLLING_INTERVALS)
     */
    private int _pollingSlot = 0;
    
    /**
     * kick off a monitor after 2 seconds, then gradually up to every 4 minutes to cover a typical
     * fresh system boot-up sequence where interfaces may take some time to settle.
     */
    private void kickOffTimer() {
        _timerThread.schedule(_threadPool, new TimerTask() {

            @Override
            public void run() {
                // move the polling "slot" up
                _pollingSlot = Math.min(_pollingSlot + 1, POLLING_INTERVALS.length - 1);
               
                tryMonitorInterfaces();

                // then after a minute...
                _timerThread.schedule(_threadPool, this, POLLING_INTERVALS[_pollingSlot]);
            }

        }, 2000);
    }
    
    /**
     * Enumerates the interfaces and performs the actual before/after checks.
     * (involves blocking I/O)
     */
    private void monitorInterfaces() {
        Set<InetAddress> activeSet = new HashSet<>(4);

        if (!usingOptInMode(activeSet)) {
            // automatic binding mode (all-in):
            listValidInterfaces(activeSet);
        }

        // add the IPv4 Loopback interface if no interfaces present
        // (whether automatic- or opt-in- mode)
        if (activeSet.size() == 0)
            activeSet.add(IPv4Loopback);

        // new interfaces
        List<InetAddress> newly = new ArrayList<>(activeSet.size());

        for (InetAddress newIntf : activeSet) {
            if (!_lastActiveSet.contains(newIntf))
                newly.add(newIntf);
        }

        // disappeared interfaces
        List<InetAddress> gone = new ArrayList<>(activeSet.size());

        for (InetAddress lastActiveIntf : _lastActiveSet) {
            if (!activeSet.contains(lastActiveIntf))
                gone.add(lastActiveIntf);
        }

        boolean hasChanged = false;

        // update 'last active' with new and old

        for (InetAddress intf : newly) {
            _lastActiveSet.add(intf);
            hasChanged = true;

            _logger.info("{}: interface appeared!", intf);
        }

        for (InetAddress intf : gone) {
            _lastActiveSet.remove(intf);
            hasChanged = true;

            _logger.info("{}: interface disappeared! ", intf);
        }
                
        // do internal test first before notifying anyone
        if (hasChanged) {
            // update the snapshot
            _interfacesSnapshot = activeSet.toArray(new InetAddress[activeSet.size()]);
            
            // and temporarily poll quicker again (might be general NIC activity)
            _pollingSlot = 0;
        }
 
        // now notify handlers...

        // remove previous handlers
        synchronized (_removedOnChangeHandlers) {
            if (_removedOnChangeHandlers.size() > 0) {
                for (ChangeHandler handler : _removedOnChangeHandlers) {
                    _onChangeHandlers.remove(handler);
                }
                _removedOnChangeHandlers.clear();
            }
        }

        // move any new handlers into this set
        List<ChangeHandler> newHandlers = null;

        synchronized (_newOnChangeHandlers) {
            if (_newOnChangeHandlers.size() > 0) {
                newHandlers = new ArrayList<ChangeHandler>(_newOnChangeHandlers);

                // no longer new now...
                _newOnChangeHandlers.clear();
            }
        }

        // notify recently new handlers first of the full active set so they can synchronise their lists
        if (newHandlers != null) {
            // 'lastActiveSet' is only modified by one thread so thread-safe
            List<InetAddress> activeList = new ArrayList<>(_lastActiveSet);

            for (ChangeHandler handler : newHandlers)
                handler.handle(activeList, Collections.<InetAddress>emptyList());
        }

        // notify existing handlers of changes
        if (hasChanged) {
            List<ChangeHandler> cachedList;

            cachedList = new ArrayList<>(_onChangeHandlers);

            for (ChangeHandler handler : cachedList)
                handler.handle(newly, gone);
        }

        // move 'new' to existing
        if (newHandlers != null) {
            for (ChangeHandler handler : newHandlers)
                _onChangeHandlers.add(handler);
        }
    }

    /**
     * (exceptionless)
     */
    private void tryMonitorInterfaces() {
        try {
            monitorInterfaces();
            
        } catch (Exception exc) {
            // still 'catch' to provide guarantee otherwise timer could fail            
            _logger.warn("An exception should not occur within method.", exc);
        }
    }
    
    /**
     * Scans for 'up', non-loopback, multicast-supporting, network interfaces with at least one IPv4 address. Also updates
     * hostname and MAC addressess snapshot.
     */
    private void listValidInterfaces(Set<InetAddress> refNicSet) {
        List<String> ipAddresses = new ArrayList<>();
        List<String> macAddresses = new ArrayList<>();
        
        String hostname = "UNKNOWN";
        
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();

            for (NetworkInterface intf : Collections.list(interfaces)) {
                try {
                    if (!intf.supportsMulticast() || intf.isLoopback() || !intf.isUp())
                        continue;

                    byte[] macAddress = null;
                    
                    // check for at least one IPv4 address and check loopback status again for good measure
                    for (InetAddress address : Collections.list(intf.getInetAddresses())) {
                        if (address instanceof Inet4Address) {
                            refNicSet.add(address);
                            ipAddresses.add(address.getHostAddress());
                            if (macAddress == null)
                                macAddress = intf.getHardwareAddress(); 
                        }
                    }
                    
                    if (macAddress != null)
                        macAddresses.add(Formatting.formatFewBytes(macAddress));

                } catch (Exception exc) {
                    // skip this interface
                }
            }
            
            hostname = InetAddress.getLocalHost().getHostName();
            
        } catch (Exception exc) {
            warn("intf_enumeration", "Was not able to enumerate network interfaces or get hostname", exc);
        }
        
        _hostnameSnapshot = hostname;
        
        if (!macAddresses.equals(_macAddressesSnapshot))
            _macAddressesSnapshot = macAddresses;
        
        if (!ipAddresses.equals(_macAddressesSnapshot))
            _ipAddressesSnapshot = ipAddresses;
    }
    
    /**
     * (logging)
     */
    private boolean _suppressInterfaceWarning = false;

    /**
     * This applied if network interfaces are specified (opt-in mode)
     */
    private boolean usingOptInMode(Set<InetAddress> refNicSet) {
        String[] interfaces = Nodel.getInterfacesToUse();

        if (interfaces == null || interfaces.length == 0)
            return false;

        // add all the IP addresses related to the interfaces whether they're available or not
        for (String name : interfaces) {
            try {
                NetworkInterface networkInterface = NetworkInterface.getByName(name);
                if (networkInterface == null) {
                    if (!_suppressInterfaceWarning) {
                        _logger.warn("\"{}\": network interface not available (yet?); will use loopback if no interfaces are resolved; this warning will be suppressed from now on.", name);
                        _suppressInterfaceWarning = true;
                    }

                    continue;
                }

                for (InetAddress addr : Collections.list(networkInterface.getInetAddresses())) {
                    if (addr instanceof Inet4Address)
                        refNicSet.add(addr);
                }

            } catch (Exception exc) {
                // ignore
                warn("intf_enumeration", "(opt-in mode) Could not query interface '" + name + "'", exc);
            }
        }

        return true;
    }
    
    /**
     * Returns the last snapshot of the interfaces.
     */
    public InetAddress[] getInterfaces() {
        return _interfacesSnapshot;
    }
    
    /**
     * Snapshot of MAC addresses (related to interfaces)
     */
    public List<String> getMACAddresses() {
        return _macAddressesSnapshot;
    }
    
    /**
     * Same as 'getInterfaces' but as strings
     */
    public List<String> getIPAddresses() {
        return _ipAddressesSnapshot;
    }

    /**
     * Warning with suppression.
     */
    private void warn(String category, String msg, Exception exc) {
        _logger.warn(msg, exc);
    }
    
}
