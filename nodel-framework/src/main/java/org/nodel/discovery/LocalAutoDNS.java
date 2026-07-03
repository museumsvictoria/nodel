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
 *
 * <p>Activated via the {@code org.nodel.discovery.impl} system property read by
 * {@link AutoDNS}, e.g. {@code -Dorg.nodel.discovery.impl=org.nodel.discovery.LocalAutoDNS;instance}
 * (see nodel-jyhost/build.gradle). It lives in main sources (not test sources) so the
 * reflective loader can find it on the host's runtime classpath.
 */
public class LocalAutoDNS extends AutoDNS {

    private final Logger _logger = LoggerFactory.getLogger(LocalAutoDNS.class);
    private final ConcurrentMap<SimpleName, AdvertisementInfo> _advertisements = new ConcurrentHashMap<>();

    private LocalAutoDNS() {
        // deliberately at warn level (the default logging threshold): this line is both
        // an audit trail that production multicast discovery is NOT in use, and how the
        // test suite verifies this implementation actually loaded (see TestBase)
        _logger.warn("LocalAutoDNS enabled (test/local discovery) - not for production use.");
    }

    /**
     * Resolves an advertised node to the local TCP address.
     * Unlike the multicast implementation, this always returns localhost
     * since LocalAutoDNS only tracks nodes within the same process.
     *
     * @param node the node name
     * @return NodeAddress pointing to 127.0.0.1 with the current TCP port, or null if not advertised or TCP is not configured
     */
    @Override
    public NodeAddress resolveNodeAddress(SimpleName node) {
        if (_advertisements.get(node) == null) {
            _logger.debug("Cannot resolve node address for '{}': node is not advertised", node);
            return null;
        }
        int tcpPort = Nodel.getTCPPort();
        if (tcpPort <= 0) {
            _logger.warn("Cannot resolve node address for '{}': TCP port is not configured (port={})", node, tcpPort);
            return null;
        }
        return NodeAddress.create("127.0.0.1", tcpPort);
    }

    @Override
    public void registerService(SimpleName node) {
        List<String> addresses = buildHttpAddresses();
        long now = System.nanoTime() / 1000000L;
        // match NodelAutoDNS' contract exactly so this test double never
        // masks a double-registration bug that would throw in production
        if (_advertisements.putIfAbsent(node, new AdvertisementInfo(node, addresses, now)) != null)
            throw new IllegalStateException(node + " is already being advertised.");
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
        // match NodelAutoDNS' contract exactly (see registerService)
        if (_advertisements.remove(node) == null)
            throw new IllegalStateException(node + " is not advertised anyway.");
    }

    /**
     * Unlike the multicast implementation's permanent shutdown, this simply clears
     * the in-process registrations and the instance remains usable (sufficient for tests).
     */
    @Override
    public void close() throws IOException {
        _advertisements.clear();
    }

    private List<String> buildHttpAddresses() {
        String[] httpAddresses = Nodel.getHTTPAddresses();
        if (httpAddresses != null && httpAddresses.length > 0 && !isDefaultHttpAddresses(httpAddresses)) {
            // copy defensively: Nodel.getHTTPAddresses() exposes its internal array
            return new ArrayList<>(Arrays.asList(httpAddresses));
        }

        // no real addresses are configured; advertise a localhost placeholder
        // (with the HTTP port when known, without it otherwise)
        int httpPort = Nodel.getHTTPPort();
        String fallback = httpPort > 0
                ? String.format("http://127.0.0.1:%s%s", httpPort, Nodel.getHTTPSuffix())
                : String.format("http://127.0.0.1%s", Nodel.getHTTPSuffix());
        // warn (not debug): consumers will see this address and it may not be reachable
        _logger.warn("HTTP addresses not configured; advertising fallback: {}", fallback);
        return Arrays.asList(fallback);
    }

    // mirrors the unconfigured default in Nodel (s_httpAddresses = {"http://127.0.0.1"});
    // if that default ever changes this check must follow
    private static boolean isDefaultHttpAddresses(String[] httpAddresses) {
        if (httpAddresses.length != 1) {
            return false;
        }
        String address = httpAddresses[0];
        if (address == null) {
            return true;
        }
        String trimmed = address.trim();
        return "http://127.0.0.1".equals(trimmed) || "http://127.0.0.1/".equals(trimmed);
    }

    /**
     * Creates (or returns the existing) LocalAutoDNS instance as an AutoDNS.
     * Use this when only the AutoDNS interface is needed.
     *
     * @return the singleton LocalAutoDNS instance as an AutoDNS
     */
    public static AutoDNS create() {
        return Instance.INSTANCE;
    }

    private static class Instance {
        private static final LocalAutoDNS INSTANCE = new LocalAutoDNS();
    }

    /**
     * Returns the singleton instance with the concrete LocalAutoDNS type.
     * Use {@link #create()} when only the AutoDNS interface is needed.
     *
     * @return the singleton LocalAutoDNS instance
     */
    public static LocalAutoDNS instance() {
        return Instance.INSTANCE;
    }
}
