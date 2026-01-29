package org.nodel.discovery;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.nodel.SimpleName;
import org.nodel.core.NodeAddress;
import org.nodel.core.Nodel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A local, non-multicast discovery implementation intended for deterministic testing.
 * Uses in-process registrations and the current HTTP addresses from {@link Nodel}.
 */
public class LocalAutoDNS extends AutoDNS {

    private final Logger _logger = LoggerFactory.getLogger(LocalAutoDNS.class);
    private final ConcurrentMap<SimpleName, AdvertisementInfo> _advertisements = new ConcurrentHashMap<>();

    private LocalAutoDNS() {
        _logger.info("LocalAutoDNS enabled (test/local discovery).");
    }

    @Override
    public NodeAddress resolveNodeAddress(SimpleName node) {
        int tcpPort = Nodel.getTCPPort();
        if (tcpPort <= 0) {
            return null;
        }
        return NodeAddress.create("127.0.0.1", tcpPort);
    }

    @Override
    public void registerService(SimpleName node) {
        List<String> addresses = buildHttpAddresses();
        long now = System.nanoTime() / 1000000L;
        AdvertisementInfo info = _advertisements.computeIfAbsent(node, key -> new AdvertisementInfo(node, addresses, now));
        info.refresh(node, addresses, now);
    }

    @Override
    public Collection<AdvertisementInfo> list() {
        return new ArrayList<>(_advertisements.values());
    }

    @Override
    public AdvertisementInfo resolve(SimpleName node) {
        return _advertisements.get(node);
    }

    @Override
    public void unregisterService(SimpleName node) {
        if (_advertisements.remove(node) == null) {
            throw new IllegalStateException(node + " is not advertised anyway.");
        }
    }

    @Override
    public void close() throws IOException {
        _advertisements.clear();
    }

    private List<String> buildHttpAddresses() {
        String[] httpAddresses = Nodel.getHTTPAddresses();
        if (httpAddresses != null && httpAddresses.length > 0) {
            return Arrays.asList(httpAddresses);
        }
        String fallback = String.format("http://127.0.0.1:%s%s", Nodel.getHTTPPort(), Nodel.getHTTPSuffix());
        List<String> addresses = new ArrayList<>(1);
        addresses.add(fallback);
        return addresses;
    }

    public static AutoDNS create() {
        return Instance.INSTANCE;
    }

    private static class Instance {
        private static final LocalAutoDNS INSTANCE = new LocalAutoDNS();
    }

    public static LocalAutoDNS instance() {
        return Instance.INSTANCE;
    }
}
