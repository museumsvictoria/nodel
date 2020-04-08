package org.nodel.discovery;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.nodel.SimpleName;
import org.nodel.reflection.Value;

public class AdvertisementInfo {
    
    /**
     * Different sets of addresses for different nodes 
     */
    public static class Addresses {
        
        SimpleName _node;
        
        final List<String> _addresses;
        
        long _timestamp;
        
        /**
         * (will call refresh)
         */
        public Addresses(SimpleName node, List<String> addresses, long timestamp) {
            _addresses = addresses;
            refresh(node, timestamp);
        }
        
        @Value(name = "name")
        public SimpleName getNode() {
            return _node;
        }
        
        /**
         * (returns immutable collection)
         */
        @Value(name = "addresses")
        public List<String> getAddresses() {
            return _addresses;
        }
        
        /**
         * (millis, uses title-caps for backwards compatibility)
         */
        @Value(name = "timeStamp")
        public long getTimeStamp() {
            return _timestamp;
        }
        
        void refresh(SimpleName node, long timestamp) {
            _node = node;
            _timestamp = timestamp;
        }

        @Override
        public String toString() {
            return String.format("[AddressEntry %s]", _addresses);
        }
        
    }
    
    private int _currentIndex = -1;
    
    /**
     * (copy-on-write for thread safety) 
     */
    private List<Addresses> _addressesEntries = new CopyOnWriteArrayList<>();
    
    /**
     * (result is enumeration-thread-safe)
     */
    public List<Addresses> getAllAddresses() {
        return _addressesEntries;
    }
    
    /**
     * Returns the next address entry rolling over if necessary (or null if empty)
     * (not thread safe)
     */
    public Addresses getNextAddresses() {
        int size = _addressesEntries.size();
        
        if (size == 0)
            return null;
        
        _currentIndex++;
        
        if (_currentIndex >= size)
            _currentIndex = 0;
        
        return _addressesEntries.get(_currentIndex);
    }
    
    /**
     * Returns the current address entry
     * (not thread safe)
     */
    public Addresses getCurrentAddressEntry() {
        if (_currentIndex >= _addressesEntries.size() || _currentIndex < 0)
            _currentIndex = 0;
        
        return _addressesEntries.get(_currentIndex);
    }
    
    /**
     * ('refresh' will called)
     */
    public AdvertisementInfo(SimpleName node, List<String> addresses, long timestamp) {
        refresh(node, addresses,timestamp);
    }
    
    /**
     * Refreshes / updates node name, addresses and timestamp ('nanoTime' base) 
     * (not thread safe)
     */
    public void refresh(SimpleName node, List<String> addresses, long timestamp) {
        // look for existing
        int size = _addressesEntries.size();
        Addresses found = null;
        for (int i = 0; i < size; i++) {
            Addresses item = _addressesEntries.get(i);
            if (item._addresses.equals(addresses))
                found = item;
        }
        
        if (found == null) {
            // create new
            found = new Addresses(node, addresses, timestamp);
            _addressesEntries.add(found);
            
        } else {
            // just refresh
            found.refresh(node, timestamp);
        }
    }
    
    /**
     * Removes all the stale address entries
     * (not thread safe)
     */
    public void removeStaleAddressesEntries(long currentTime) {
        int size = _addressesEntries.size();
        
        // start from end so can remove item from list in-situ
        for (int i = size - 1; i >= 0; i--) {
            Addresses item = _addressesEntries.get(i);
            
            long timeDiff = (currentTime / 1000000) - item._timestamp;
            
            if (timeDiff > NodelAutoDNS.STALE_TIME)
                _addressesEntries.remove(i);
        }
    }
    
    /**
     * It's stale if there are no fresh addresses
     */
    public boolean isStale() {
        return _addressesEntries.size() == 0;
    }
    
}
