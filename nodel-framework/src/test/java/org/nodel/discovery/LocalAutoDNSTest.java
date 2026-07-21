package org.nodel.discovery;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nodel.SimpleName;
import org.nodel.core.NodeAddress;
import org.nodel.core.Nodel;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the deterministic, in-process discovery implementation.
 * LocalAutoDNS is a singleton over global Nodel state, so each test snapshots
 * and restores that state and clears the registrations via close().
 */
public class LocalAutoDNSTest {

    private final LocalAutoDNS dns = LocalAutoDNS.instance();

    private int savedTcpPort;
    private int savedHttpPort;
    private String[] savedHttpAddresses;
    private String[] savedHttpNodeAddresses;

    @BeforeEach
    public void snapshotNodelState() {
        savedTcpPort = Nodel.getTCPPort();
        savedHttpPort = Nodel.getHTTPPort();
        savedHttpAddresses = Nodel.getHTTPAddresses();
        savedHttpNodeAddresses = Nodel.getHTTPNodeAddress();
    }

    @AfterEach
    public void restoreNodelState() throws IOException {
        Nodel.updateTCPPort(savedTcpPort);
        Nodel.setHTTPPort(savedHttpPort);
        Nodel.updateHTTPAddresses(savedHttpAddresses, savedHttpNodeAddresses);
        dns.close(); // clears registrations only; instance stays usable
    }

    @Test
    public void createAndInstanceReturnTheSameSingleton() {
        assertSame(LocalAutoDNS.instance(), LocalAutoDNS.create());
    }

    @Test
    public void resolveReturnsNullWhenNotAdvertised() {
        assertNull(dns.resolveNodeAddress(new SimpleName("Unknown Node")));
        assertNull(dns.resolve(new SimpleName("Unknown Node")));
    }

    @Test
    public void resolveReturnsLoopbackWithConfiguredTcpPort() {
        Nodel.updateTCPPort(12345);
        SimpleName node = new SimpleName("Resolvable Node");
        dns.registerService(node);

        NodeAddress address = dns.resolveNodeAddress(node);
        assertNotNull(address);
        assertEquals("127.0.0.1", address.getHost());
        assertEquals(12345, address.getPort());
    }

    @Test
    public void resolveReturnsNullWhenTcpPortUnset() {
        Nodel.updateTCPPort(0);
        SimpleName node = new SimpleName("Portless Node");
        dns.registerService(node);

        assertNull(dns.resolveNodeAddress(node));
    }

    @Test
    public void duplicateRegistrationThrowsLikeTheMulticastImplementation() {
        SimpleName node = new SimpleName("Twice Registered");
        dns.registerService(node);

        assertThrows(IllegalStateException.class, () -> dns.registerService(node));
    }

    @Test
    public void unregisteringUnknownNodeThrowsLikeTheMulticastImplementation() {
        assertThrows(IllegalStateException.class, () -> dns.unregisterService(new SimpleName("Never Registered")));
    }

    @Test
    public void unregisterRemovesTheAdvertisement() {
        Nodel.updateTCPPort(12345);
        SimpleName node = new SimpleName("Transient Node");
        dns.registerService(node);
        assertNotNull(dns.resolveNodeAddress(node));

        dns.unregisterService(node);
        assertNull(dns.resolveNodeAddress(node));
        assertTrue(dns.list().isEmpty());
    }

    @Test
    public void listReturnsAllAdvertisements() {
        dns.registerService(new SimpleName("Node A"));
        dns.registerService(new SimpleName("Node B"));

        assertEquals(2, dns.list().size());
    }

    @Test
    public void advertisedAddressUsesConfiguredHttpAddresses() {
        Nodel.updateHTTPAddresses(new String[] { "http://10.0.0.5:8085" }, new String[] { "http://10.0.0.5:8085/node" });
        SimpleName node = new SimpleName("Configured Node");
        dns.registerService(node);

        assertEquals(List.of("http://10.0.0.5:8085"), advertisedAddresses(node));
    }

    @Test
    public void advertisedAddressFallsBackToLoopbackWithHttpPort() {
        // the unconfigured default placeholder should not be advertised as-is
        Nodel.updateHTTPAddresses(new String[] { "http://127.0.0.1" }, new String[] { "http://127.0.0.1/node" });
        Nodel.setHTTPPort(8085);
        SimpleName node = new SimpleName("Fallback Node");
        dns.registerService(node);

        assertEquals(List.of("http://127.0.0.1:8085" + Nodel.getHTTPSuffix()), advertisedAddresses(node));
    }

    @Test
    public void advertisedAddressFallsBackToLoopbackWithoutHttpPort() {
        Nodel.updateHTTPAddresses(new String[] { "http://127.0.0.1" }, new String[] { "http://127.0.0.1/node" });
        Nodel.setHTTPPort(0);
        SimpleName node = new SimpleName("Portless Fallback Node");
        dns.registerService(node);

        assertEquals(List.of("http://127.0.0.1" + Nodel.getHTTPSuffix()), advertisedAddresses(node));
    }

    private List<String> advertisedAddresses(SimpleName node) {
        AdvertisementInfo info = dns.resolve(node);
        assertNotNull(info);
        return info.getAllAddresses().stream()
                .flatMap(entry -> entry.getAddresses().stream())
                .collect(Collectors.toList());
    }
}
