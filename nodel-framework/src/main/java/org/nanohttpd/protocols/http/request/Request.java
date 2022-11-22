package org.nanohttpd.protocols.http.request;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Properties;

/**
 * Holds data related to an HTTP request.
 */
public class Request {

    public String uri;

    public String method;

    public Properties parms;

    public Properties header;

    public Properties files;

    public byte[] raw;

    public Socket peer;

    public final String remoteHost;

    public final int remotePort;

    public final String localHost;

    public final int localPort;

    public Request(String uri, String method, Properties parms, Properties header, Properties files, byte[] raw, Socket peer) {
        this.uri = uri;
        this.method = method;
        this.parms = parms;
        this.header = header;
        this.files = files;
        this.raw = raw;
        this.peer = peer;

        InetSocketAddress remoteSocketAddr = (InetSocketAddress) peer.getRemoteSocketAddress();
        this.remoteHost = remoteSocketAddr.getHostString();
        this.remotePort = remoteSocketAddr.getPort();

        InetSocketAddress localSocketAddr = (InetSocketAddress) peer.getLocalSocketAddress();
        this.localHost = localSocketAddr.getHostString();
        this.localPort = localSocketAddr.getPort();
    }

} // (class)
