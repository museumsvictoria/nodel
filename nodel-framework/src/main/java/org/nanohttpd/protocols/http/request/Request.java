package org.nanohttpd.protocols.http.request;

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

    public Request(String uri, String method, Properties parms, Properties header, Properties files, byte[] raw, Socket peer) {
        this.uri = uri;
        this.method = method;
        this.parms = parms;
        this.header = header;
        this.files = files;
        this.raw = raw;
        this.peer = peer;
    }

} // (class)
