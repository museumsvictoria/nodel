package org.nanohttpd.protocols.http;

/*
 * #%L
 * NanoHttpd-Core
 * %%
 * Copyright (C) 2012 - 2016 nanohttpd
 * %%
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the nanohttpd nor the names of its contributors
 *    may be used to endorse or promote products derived from this software without
 *    specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

import java.io.*;
import java.net.*;
import java.security.KeyStore;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.regex.Pattern;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import org.nanohttpd.protocols.http.request.Request;
import org.nanohttpd.protocols.http.response.Response;
import org.nanohttpd.protocols.http.response.Status;
import org.nanohttpd.protocols.http.sockets.DefaultServerSocketFactory;
import org.nanohttpd.protocols.http.sockets.SecureServerSocketFactory;
import org.nanohttpd.protocols.http.tempfiles.DefaultTempFileManagerFactory;
import org.nanohttpd.protocols.http.tempfiles.ITempFileManager;
import org.nanohttpd.protocols.http.threading.DefaultAsyncRunner;
import org.nanohttpd.protocols.http.threading.IAsyncRunner;
import org.nanohttpd.util.IFactory;
import org.nanohttpd.util.IFactoryThrowing;
import org.nanohttpd.util.IHandler;

import org.nodel.Base64;
import org.nodel.core.Nodel;
import org.nodel.diagnostics.CountableInputStream;
import org.nodel.diagnostics.CountableOutputStream;
import org.nodel.diagnostics.Diagnostics;
import org.nodel.diagnostics.SharableMeasurementProvider;
import org.nodel.io.Stream;
import org.nodel.io.UnexpectedIOException;
import org.nodel.net.Credentials;

/**
 * A simple, tiny, nicely embeddable HTTP server in Java
 * <p/>
 * <p/>
 * NanoHTTPD
 * <p>
 * Copyright (c) 2012-2013 by Paul S. Hawke, 2001,2005-2013 by Jarno Elonen,
 * 2010 by Konstantinos Togias
 * </p>
 * <p/>
 * <p/>
 * <b>Features + limitations: </b>
 * <ul>
 * <p/>
 * <li>Only one Java file</li>
 * <li>Java 5 compatible</li>
 * <li>Released as open source, Modified BSD licence</li>
 * <li>No fixed config files, logging, authorization etc. (Implement yourself if
 * you need them.)</li>
 * <li>Supports parameter parsing of GET and POST methods (+ rudimentary PUT
 * support in 1.25)</li>
 * <li>Supports both dynamic content and file serving</li>
 * <li>Supports file upload (since version 1.2, 2010)</li>
 * <li>Supports partial content (streaming)</li>
 * <li>Supports ETags</li>
 * <li>Never caches anything</li>
 * <li>Doesn't limit bandwidth, request time or simultaneous connections</li>
 * <li>Default code serves files and shows all HTTP parameters and headers</li>
 * <li>File server supports directory listing, index.html and index.htm</li>
 * <li>File server supports partial content (streaming)</li>
 * <li>File server supports ETags</li>
 * <li>File server does the 301 redirection trick for directories without '/'</li>
 * <li>File server supports simple skipping for files (continue download)</li>
 * <li>File server serves also very long files without memory overhead</li>
 * <li>Contains a built-in list of most common MIME types</li>
 * <li>All header names are converted to lower case so they don't vary between
 * browsers/clients</li>
 * <p/>
 * </ul>
 * <p/>
 * <p/>
 * <b>How to use: </b>
 * <ul>
 * <p/>
 * <li>Subclass and implement serve() and embed to your own program</li>
 * <p/>
 * </ul>
 * <p/>
 * See the separate "LICENSE.md" file for the distribution license (Modified BSD
 * licence)
 */
public abstract class NanoHTTPD {

    public static final String CONTENT_DISPOSITION_REGEX = "([ |\t]*Content-Disposition[ |\t]*:)(.*)";

    public static final Pattern CONTENT_DISPOSITION_PATTERN = Pattern.compile(CONTENT_DISPOSITION_REGEX, Pattern.CASE_INSENSITIVE);

    public static final String CONTENT_TYPE_REGEX = "([ |\t]*content-type[ |\t]*:)(.*)";

    public static final Pattern CONTENT_TYPE_PATTERN = Pattern.compile(CONTENT_TYPE_REGEX, Pattern.CASE_INSENSITIVE);

    public static final String CONTENT_DISPOSITION_ATTRIBUTE_REGEX = "[ |\t]*([a-zA-Z]*)[ |\t]*=[ |\t]*['|\"]([^\"^']*)['|\"]";

    public static final Pattern CONTENT_DISPOSITION_ATTRIBUTE_PATTERN = Pattern.compile(CONTENT_DISPOSITION_ATTRIBUTE_REGEX);

    public static final class ResponseException extends Exception {

        private static final long serialVersionUID = 6569838532917408380L;

        private final Status status;

        public ResponseException(Status status, String message) {
            super(message);
            this.status = status;
        }

        public ResponseException(Status status, String message, Exception e) {
            super(message, e);
            this.status = status;
        }

        public Status getStatus() {
            return this.status;
        }
    }

    /**
     * Maximum time to wait on Socket.getInputStream().read() (in milliseconds)
     * This is required as the Keep-Alive HTTP connections would otherwise block
     * the socket reading thread forever (or as long the browser is open).
     */
    public static final int SOCKET_READ_TIMEOUT = 60000;

    /**
     * Common MIME type for dynamic content: plain text
     */
    public static final String MIME_PLAINTEXT = "text/plain";

    /**
     * Common MIME type for dynamic content: html
     */
    public static final String MIME_HTML = "text/html";

    /**
     * Common MIME type for dynamic content
     */
    public static final String MIME_DEFAULT_BINARY = "application/octet-stream";

    /**
     * Pseudo-Parameter to use to store the actual query string in the
     * parameters map for later re-processing.
     */
    private static final String QUERY_STRING_PARAMETER = "NanoHttpd.QUERY_STRING";

    /**
     * logger to log to.
     */

    private static AtomicLong s_instanceCounter = new AtomicLong(0);

    private Logger logger = Logger.getLogger(NanoHTTPD.class.getName() + "_" + s_instanceCounter.getAndIncrement());

    /**
     * logger to log to.
     */
    public static final Logger LOG = Logger.getLogger(NanoHTTPD.class.getName() + "_STATIC");

    /**
     * (can only be started once)
     */
    private boolean _stopped = false;

    /**
     * Hashtable mapping (String)FILENAME_EXTENSION -> (String)MIME_TYPE
     */
    protected static Map<String, String> MIME_TYPES;

    public static Map<String, String> mimeTypes() {
        if (MIME_TYPES == null) {
            MIME_TYPES = new HashMap<String, String>();
            loadMimeTypes(MIME_TYPES, "META-INF/nanohttpd/default-mimetypes.properties");
            loadMimeTypes(MIME_TYPES, "META-INF/nanohttpd/mimetypes.properties");
            if (MIME_TYPES.isEmpty()) {
                LOG.log(Level.SEVERE, "no mime types found in the classpath! please provide mimetypes.properties");
            }
        }
        return MIME_TYPES;
    }

    @SuppressWarnings({
            "unchecked",
            "rawtypes"
    })
    private static void loadMimeTypes(Map<String, String> result, String resourceName) {
        try {
            Enumeration<URL> resources = NanoHTTPD.class.getClassLoader().getResources(resourceName);
            while (resources.hasMoreElements()) {
                URL url = (URL) resources.nextElement();
                Properties properties = new Properties();
                InputStream stream = null;
                try {
                    stream = url.openStream();
                    properties.load(stream);
                } catch (IOException e) {
                    LOG.log(Level.SEVERE, "could not load mimetypes from " + url, e);
                } finally {
                    safeClose(stream);
                }
                result.putAll((Map) properties);
            }
        } catch (IOException e) {
            LOG.log(Level.INFO, "no mime types available at " + resourceName);
        }
    }

    ;

    /**
     * Creates an SSLSocketFactory for HTTPS. Pass a loaded KeyStore and an
     * array of loaded KeyManagers. These objects must properly
     * loaded/initialized by the caller.
     */
    public static SSLServerSocketFactory makeSSLSocketFactory(KeyStore loadedKeyStore, KeyManager[] keyManagers) throws IOException {
        SSLServerSocketFactory res = null;
        try {
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(loadedKeyStore);
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(keyManagers, trustManagerFactory.getTrustManagers(), null);
            res = ctx.getServerSocketFactory();
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        }
        return res;
    }

    /**
     * Creates an SSLSocketFactory for HTTPS. Pass a loaded KeyStore and a
     * loaded KeyManagerFactory. These objects must properly loaded/initialized
     * by the caller.
     */
    public static SSLServerSocketFactory makeSSLSocketFactory(KeyStore loadedKeyStore, KeyManagerFactory loadedKeyFactory) throws IOException {
        try {
            return makeSSLSocketFactory(loadedKeyStore, loadedKeyFactory.getKeyManagers());
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        }
    }

    /**
     * Creates an SSLSocketFactory for HTTPS. Pass a KeyStore resource with your
     * certificate and passphrase
     */
    public static SSLServerSocketFactory makeSSLSocketFactory(String keyAndTrustStoreClasspathPath, char[] passphrase) throws IOException {
        try {
            KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
            InputStream keystoreStream = NanoHTTPD.class.getResourceAsStream(keyAndTrustStoreClasspathPath);

            if (keystoreStream == null) {
                throw new IOException("Unable to load keystore from classpath: " + keyAndTrustStoreClasspathPath);
            }

            keystore.load(keystoreStream, passphrase);
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keystore, passphrase);
            return makeSSLSocketFactory(keystore, keyManagerFactory);
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        }
    }

    /**
     * Get MIME type from file name extension, if possible
     *
     * @param uri the string representing a file
     * @return the connected mime/type
     */
    public static String getMimeTypeForFile(String uri) {
        int dot = uri.lastIndexOf('.');
        String mime = null;
        if (dot >= 0) {
            mime = mimeTypes().get(uri.substring(dot + 1).toLowerCase());
        }
        return mime == null ? "application/octet-stream" : mime;
    }

    public static final void safeClose(Object closeable) {
        try {
            if (closeable != null) {
                if (closeable instanceof Closeable) {
                    ((Closeable) closeable).close();
                } else if (closeable instanceof Socket) {
                    ((Socket) closeable).close();
                } else if (closeable instanceof ServerSocket) {
                    ((ServerSocket) closeable).close();
                } else {
                    throw new IllegalArgumentException("Unknown object to close");
                }
            }
        } catch (IOException e) {
            NanoHTTPD.LOG.log(Level.SEVERE, "Could not close", e);
        }
    }

    /**
     * URL-encodes everything between "/"-characters. Encodes spaces as '%20'
     * instead of '+'.
     */
    protected String encodeUri(String uri) {
        String newUri = "";
        StringTokenizer st = new StringTokenizer(uri, "/ ", true);
        while (st.hasMoreTokens()) {
            String tok = st.nextToken();
            if (tok.equals("/")) {
                newUri += "/";
            } else if (tok.equals(" ")) {
                newUri += "%20";
            } else {
                try {
                    newUri += URLEncoder.encode(tok, "UTF-8");
                } catch (UnsupportedEncodingException uee) {
                }
            }
        } // (while)

        return newUri;
    } // (method)

    public final String hostname;

    public final int myPort;

    public int getPort() {
        return this.myPort;
    }

    private volatile ServerSocket myServerSocket;

    public ServerSocket getMyServerSocket() {
        return myServerSocket;
    }

    private IFactoryThrowing<ServerSocket, IOException> serverSocketFactory = new DefaultServerSocketFactory();

    private Thread myThread;

    private IHandler<IHTTPSession, Response> httpHandler;

    protected List<IHandler<IHTTPSession, Response>> interceptors = new ArrayList<IHandler<IHTTPSession, Response>>(4);

    /**
     * Pluggable strategy for asynchronously executing requests.
     */
    protected IAsyncRunner asyncRunner;

    /**
     * Pluggable strategy for creating and cleaning up temporary files.
     */
    private IFactory<ITempFileManager> tempFileManagerFactory;

    private File myRootDir;

    public File getRootDir() {
        return this.myRootDir;
    }

    /**
     * (see public method)
     */
    private File firstChoiceDir;

    /**
     * Allows for first-choice overriding (fallback web-server)
     */
    public void setFirstChoiceDir(File value) {
        this.logger.info("Instructed to use first choice directory. firstChoiceDir='" + value + "'");

        this.firstChoiceDir = value;
    }

    /**
     * (see setter)
     */
    public File getFirstChoiceDir() {
        return this.firstChoiceDir;
    }

    private boolean allowBrowsing;

    /**
     * Only bind to the local interface.
     */
    private boolean _localInterfaceOnly = false;

    /**
     * Forces to bind to only local interface i.e. no remote browsing.
     * Must be done before 'Start'.
     */

    public boolean getLocalHostOnly() {
        return _localInterfaceOnly;
    }

    public void setLocalHostOnly(boolean value) {
        _localInterfaceOnly = value;
    }

    /**
     * Constructs an HTTP server on given port.
     */
    private NanoHTTPD(int port) {
        this(null, port);
    }

    // -------------------------------------------------------------------------------
    // //
    //
    // Threading Strategy.
    //
    // -------------------------------------------------------------------------------
    // //

    /**
     * Constructs an HTTP server on given hostname and port.
     */
    private NanoHTTPD(String hostname, int port) {
        this.hostname = hostname;
        this.myPort = port;
        setTempFileManagerFactory(new DefaultTempFileManagerFactory());
        setAsyncRunner(new DefaultAsyncRunner());

        this.httpHandler = new IHandler<IHTTPSession, Response>() {

            @Override
            public Response handle(IHTTPSession input) {

                try {
                    String uri = input.getUri();
                    String methodName = input.getMethod().name();

                    Properties params = new Properties();
                    params.putAll(input.getParms()); // input.getParameters();

                    Properties headers = new Properties();
                    headers.putAll(input.getHeaders());

                    Map<String, String> _files = new HashMap<>();
                    input.parseBody(_files);
                    Properties files = new Properties();
                    files.putAll(_files);

                    // should be called after parseBody()
                    byte[] fBuf = input.getBodyBytes();

                    Request req = new Request(
                            uri,
                            methodName,
                            params,
                            headers,
                            files,
                            fBuf,
                            input.getAcceptSocket()
                    );

                    return serve(uri, null, methodName, params, req);

                } catch (Exception ex) {
                }

                return new Response(Status.INTERNAL_ERROR, MIME_PLAINTEXT, "INTERNAL ERROR: httpHandler(): cannot handle request.");
            }
        };
    }

    public NanoHTTPD(int port, File wwwroot, boolean allowBrowsing) {
        this(null, port);
        this.myRootDir = wwwroot;
        this.allowBrowsing = allowBrowsing;
    }

    public NanoHTTPD(int port, File wwwroot, boolean allowBrowsing, boolean localInterfaceOnly) {
        this(null, port);
        this.myRootDir = wwwroot;
        this.allowBrowsing = allowBrowsing;
        _localInterfaceOnly = localInterfaceOnly;
    }

    public void setHTTPHandler(IHandler<IHTTPSession, Response> handler) {
        this.httpHandler = handler;
    }

    public void addHTTPInterceptor(IHandler<IHTTPSession, Response> interceptor) {
        interceptors.add(interceptor);
    }

    /**
     * Forcibly closes all connections that are open.
     */
    public synchronized void closeAllConnections() {
        stop();
    }

    /**
     * create a instance of the client handler, subclasses can return a subclass
     * of the ClientHandler.
     *
     * @param finalAccept the socket the cleint is connected to
     * @param inputStream the input stream
     * @return the client handler
     */
    protected ClientHandler createClientHandler(final Socket finalAccept, final InputStream inputStream) {
        return new ClientHandler(this, inputStream, finalAccept);
    }

    /**
     * Instantiate the server runnable, can be overwritten by subclasses to
     * provide a subclass of the ServerRunnable.
     *
     * @param timeout the socet timeout to use.
     * @return the server runnable.
     */
    protected ServerRunnable createServerRunnable(final int timeout) {
        return new ServerRunnable(this, timeout);
    }

    /**
     * Decode parameters from a URL, handing the case where a single parameter
     * name might have been supplied several times, by return lists of values.
     * In general these lists will contain a single element.
     *
     * @param parms original <b>NanoHTTPD</b> parameters values, as passed to the
     *              <code>serve()</code> method.
     * @return a map of <code>String</code> (parameter name) to
     * <code>List&lt;String&gt;</code> (a list of the values supplied).
     */
    protected static Map<String, List<String>> decodeParameters(Map<String, String> parms) {
        return decodeParameters(parms.get(NanoHTTPD.QUERY_STRING_PARAMETER));
    }

    // -------------------------------------------------------------------------------
    // //

    /**
     * Decode parameters from a URL, handing the case where a single parameter
     * name might have been supplied several times, by return lists of values.
     * In general these lists will contain a single element.
     *
     * @param queryString a query string pulled from the URL.
     * @return a map of <code>String</code> (parameter name) to
     * <code>List&lt;String&gt;</code> (a list of the values supplied).
     */
    protected static Map<String, List<String>> decodeParameters(String queryString) {
        Map<String, List<String>> parms = new HashMap<String, List<String>>();
        if (queryString != null) {
            StringTokenizer st = new StringTokenizer(queryString, "&");
            while (st.hasMoreTokens()) {
                String e = st.nextToken();
                int sep = e.indexOf('=');
                String propertyName = sep >= 0 ? decodePercent(e.substring(0, sep)).trim() : decodePercent(e).trim();
                if (!parms.containsKey(propertyName)) {
                    parms.put(propertyName, new ArrayList<String>());
                }
                String propertyValue = sep >= 0 ? decodePercent(e.substring(sep + 1)) : null;
                if (propertyValue != null) {
                    parms.get(propertyName).add(propertyValue);
                }
            }
        }
        return parms;
    }

    /**
     * Decode percent encoded <code>String</code> values.
     *
     * @param str the percent encoded <code>String</code>
     * @return expanded form of the input, for example "foo%20bar" becomes
     * "foo bar"
     */
    public static String decodePercent(String str) {
        String decoded = null;
        try {
            decoded = URLDecoder.decode(str, "UTF8");
        } catch (UnsupportedEncodingException ignored) {
            NanoHTTPD.LOG.log(Level.WARNING, "Encoding not supported, ignored", ignored);
        }
        return decoded;
    }

    public final int getListeningPort() {
        return this.myServerSocket == null ? -1 : this.myServerSocket.getLocalPort();
    }

    public final boolean isAlive() {
        return wasStarted() && !this.myServerSocket.isClosed() && this.myThread.isAlive();
    }

    public IFactoryThrowing<ServerSocket, IOException> getServerSocketFactory() {
        return serverSocketFactory;
    }

    public void setServerSocketFactory(IFactoryThrowing<ServerSocket, IOException> serverSocketFactory) {
        this.serverSocketFactory = serverSocketFactory;
    }

    public String getHostname() {
        return hostname;
    }

    public IFactory<ITempFileManager> getTempFileManagerFactory() {
        return tempFileManagerFactory;
    }

    /**
     * Call before start() to serve over HTTPS instead of HTTP
     */
    public void makeSecure(SSLServerSocketFactory sslServerSocketFactory, String[] sslProtocols) {
        this.serverSocketFactory = new SecureServerSocketFactory(sslServerSocketFactory, sslProtocols);
    }

    /**
     * This is the "master" method that delegates requests to handlers and makes
     * sure there is a response to every request. You are not supposed to call
     * or override this method in any circumstances. But no one will stop you if
     * you do. I'm a Javadoc, not Code Police.
     *
     * @param session the incoming session
     * @return a response to the incoming session
     */
    public Response handle(IHTTPSession session) {
        for (IHandler<IHTTPSession, Response> interceptor : interceptors) {
            Response response = interceptor.handle(session);
            if (response != null)
                return response;
        }
        return httpHandler.handle(session);
    }

    /**
     * Override this to customize the server.
     * <p/>
     * <p/>
     * (By default, this returns a 404 "Not Found" plain text error response.)
     *
     * @param session The HTTP session
     * @return HTTP response, see class Response for details
     */
    @Deprecated
    protected Response serve(IHTTPSession session) {
        return Response.newFixedLengthResponse(Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "Not Found");
    }

    public Response serve(String uri, File customRoot, String method, Properties parms, Request request) {
        return serve(uri, customRoot, method, parms, request, false);
    }

    /**
     * @param noFallback Ignores 'etag', partial responses and directory browsing. (None-standard NanoHTTP)
     */
    public Response serve(String uri, File customRoot, String method, Properties parms, Request request, boolean noFallback) {
        Properties header = request.header;
        Properties files = request.files;

        myOut.println(method + " '" + uri + "' ");

        Enumeration<?> e = header.propertyNames();
        while (e.hasMoreElements()) {
            String value = (String) e.nextElement();
            myOut.println("  HDR: '" + value + "' = '" + header.getProperty(value) + "'");
        }
        e = parms.propertyNames();
        while (e.hasMoreElements()) {
            String value = (String) e.nextElement();
            myOut.println("  PRM: '" + value + "' = '" + parms.getProperty(value) + "'");
        }
        e = files.propertyNames();
        while (e.hasMoreElements()) {
            String value = (String) e.nextElement();
            myOut.println("  UPLOADED: '" + value + "' = '" + files.getProperty(value) + "'");
        }

        // try custom root first (can be null)
        Response response = tryServeFromFolder(customRoot, uri, header, noFallback);

        if (response == null)
            response = tryServeFromFolder(this.firstChoiceDir, uri, header, noFallback);

        if (response == null)
            response = serveFile(uri, header, this.myRootDir, this.allowBrowsing, noFallback);

        return response;
    }

    /**
     * Resolves a URI into the target file given the resolution order.
     */
    public File resolveFile(String uri, File customRoot) {
        String relPath = uriIntoPath(uri);

        File file = new File(customRoot, relPath);
        if (file.exists() && file.isFile())
            return file;

        file = new File(this.firstChoiceDir, relPath);
        if (file.exists() && file.isFile())
            return file;

        file = new File(this.myRootDir, relPath);
        if (file.exists() && file.isFile())
            return file;

        return null;
    }

    /**
     * Try to serve from a custom directory. Returns null if the attempt fails for
     * any reason.
     */
    private Response tryServeFromFolder(File dir, String uri, Properties header, boolean noFallback) {
        if (dir == null)
            return null;

        Response response;

        try {
            response = serveFile(uri, header, dir, this.allowBrowsing, noFallback);

            if (Status.NOT_FOUND.equals(response.getStatus()))
                response = null;

            else if (Status.INTERNAL_ERROR.equals(response.getStatus()))
                response = null;

            else if (Status.FORBIDDEN.equals(response.getStatus()))
                response = null;

        } catch (Exception exc) {
            response = null;
            // ignore, 'response == null' so allow fallback
        }

        return response;
    }

    // ==================================================
    // File server code
    // ==================================================

    /**
     * Serves file from homeDir and its' subdirectories (only). Uses only URI,
     * ignores all headers and HTTP parameters.
     */
    public Response serveFile(String uri, Properties header, File homeDir, boolean allowDirectoryListing, boolean noFallback) {
        Response res = null;

        // Make sure we won't die of an exception later
        if (homeDir == null || !homeDir.isDirectory())
            res = new Response(Status.INTERNAL_ERROR, MIME_PLAINTEXT, "INTERNAL ERRROR: serveFile(): given homeDir is not a directory.");

        if (res == null) {
            // Remove URL arguments
            uri = uri.trim().replace(File.separatorChar, '/');
            if (uri.indexOf('?') >= 0)
                uri = uri.substring(0, uri.indexOf('?'));

            // Prohibit getting out of current directory
            if (uri.startsWith("..") || uri.endsWith("..") || uri.indexOf("../") >= 0)
                res = new Response(Status.FORBIDDEN, MIME_PLAINTEXT, "FORBIDDEN: Won't serve ../ for security reasons.");
        }

        File f = new File(homeDir, uri);

        if (res == null && !f.exists())
            res = new Response(Status.NOT_FOUND, MIME_PLAINTEXT, "Error 404, file not found.");

        // List the directory, if necessary
        if (!noFallback && res == null && f.isDirectory()) {
            // Browsers get confused without '/' after the
            // directory, send a redirect.
            if (!uri.endsWith("/")) {
                uri += "/";
                res = new Response(Status.REDIRECT, MIME_HTML, "<html><body>Redirected: <a href=\"" + uri + "\">" + uri + "</a></body></html>");
                res.addHeader("Location", uri);
            }

            if (res == null) {
                // First try index.html and index.htm
                if (new File(f, "index.html").exists()) {
                    f = new File(homeDir, uri + "/index.html");
                } else if (new File(f, "index.htm").exists()) {
                    f = new File(homeDir, uri + "/index.htm");
                }
                // No index file, list the directory if it is readable
                else if (allowDirectoryListing && f.canRead()) {
                    String[] files = f.list();
                    String msg = "<html><body><h1>Directory " + uri + "</h1><br/>";

                    if (uri.length() > 1) {
                        String u = uri.substring(0, uri.length() - 1);
                        int slash = u.lastIndexOf('/');
                        if (slash >= 0 && slash < u.length())
                            msg += "<b><a href=\"" + uri.substring(0, slash + 1) + "\">..</a></b><br/>";
                    }

                    if (files != null) {
                        for (int i = 0; i < files.length; ++i) {
                            File curFile = new File(f, files[i]);
                            boolean dir = curFile.isDirectory();
                            if (dir) {
                                msg += "<b>";
                                files[i] += "/";
                            }

                            msg += "<a href=\"" + encodeUri(uri + files[i]) + "\">" + files[i] + "</a>";

                            // Show file size
                            if (curFile.isFile()) {
                                long len = curFile.length();
                                msg += " &nbsp;<font size=2>(";
                                if (len < 1024)
                                    msg += len + " bytes";
                                else if (len < 1024 * 1024)
                                    msg += len / 1024 + "." + (len % 1024 / 10 % 100) + " KB";
                                else
                                    msg += len / (1024 * 1024) + "." + len % (1024 * 1024) / 10 % 100 + " MB";

                                msg += ")</font>";
                            }
                            msg += "<br/>";
                            if (dir)
                                msg += "</b>";
                        }
                    }
                    msg += "</body></html>";
                    res = new Response(Status.OK, MIME_HTML, msg);
                } else {
                    res = new Response(Status.FORBIDDEN, MIME_PLAINTEXT, "FORBIDDEN: No directory browsing permitted.");
                }
            }
        }

        try {
            if (res == null) {
                // Get MIME type from file name extension, if possible
                String mime = null;
                int dot = f.getCanonicalPath().lastIndexOf('.');
                if (dot >= 0) {
                    mime = getMimeTypeForFile(f.getCanonicalPath());
                }
                if (mime == null) {
                    mime = MIME_DEFAULT_BINARY;
                }
                // Calculate etag
                String etag = Integer.toHexString((f.getAbsolutePath() + f.lastModified() + "" + f.length()).hashCode());

                // Support (simple) skipping:
                long startFrom = 0;
                long endAt = -1;
                String range = header.getProperty("range");
                if (range != null) {
                    if (!noFallback) {
                        return new Response(Status.INTERNAL_ERROR, MIME_PLAINTEXT, "INTERNAL ERROR: 'range' not supported in this context.");
                    }
                    if (range.startsWith("bytes=")) {
                        range = range.substring("bytes=".length());
                        int minus = range.indexOf('-');
                        try {
                            if (minus > 0) {
                                startFrom = Long.parseLong(range.substring(0, minus));
                                endAt = Long.parseLong(range.substring(minus + 1));
                            }
                        } catch (NumberFormatException nfe) {
                        }
                    }
                }

                // Change return code and add Content-Range header when skipping
                // is requested
                long fileLen = f.length();
                if (range != null && startFrom >= 0) {
                    if (startFrom >= fileLen) {
                        res = new Response(Status.RANGE_NOT_SATISFIABLE, MIME_PLAINTEXT, "");
                        res.addHeader("Content-Range", "bytes 0-0/" + fileLen);
                        res.addHeader("ETag", etag);
                    } else {
                        if (endAt < 0)
                            endAt = fileLen - 1;
                        long newLen = endAt - startFrom + 1;
                        if (newLen < 0)
                            newLen = 0;

                        final long dataLen = newLen;
                        FileInputStream fis = new FileInputStream(f) {
                            public int available() throws IOException {
                                return (int) dataLen;
                            }
                        };
                        fis.skip(startFrom);

                        res = new Response(Status.PARTIAL_CONTENT, mime, fis, dataLen);
                        res.addHeader("Content-Length", "" + dataLen);
                        res.addHeader("Content-Range", "bytes " + startFrom + "-" + endAt + "/" + fileLen);
                        res.addHeader("ETag", etag);
                    }
                } else {
                    if (!noFallback && etag.equals(header.getProperty("if-none-match"))) {
                        res = new Response(Status.NOT_MODIFIED, mime, "");
                        res.addHeader("ETag", etag);
                    } else {
                        res = new Response(Status.OK, mime, new FileInputStream(f), fileLen);
                        res.addHeader("Content-Length", "" + fileLen);
                        res.addHeader("ETag", etag);
                    }
                }
            }
        } catch (IOException ioe) {
            res = new Response(Status.FORBIDDEN, MIME_PLAINTEXT, "FORBIDDEN: Reading file failed.");
        }

        if (!noFallback) {
            res.addHeader("Accept-Ranges", "bytes"); // Announce that the file
        }
        // server accepts partial
        // content requests
        return res;
    }

    /**
     * Prepares a very plain HTML redirect response.
     */
    protected Response prepareRedirectResponse(String uri) {
        Response res = new Response(Status.REDIRECT, MIME_HTML, "<html><body>Redirected: <a href=\"" + uri + "\">" + uri + "</a></body></html>");
        res.addHeader("Location", uri);

        return res;
    }

    protected Response prepareFoundResponse(String uri) {
        Response res = new Response(Status.FOUND, MIME_HTML, "<html><body>Redirected: <a href=\"" + uri + "\">" + uri + "</a></body></html>");
        res.addHeader("Location", uri);

        return res;
    }

    /**
     * (Overloaded)
     *
     * @param header containing 'Authorization'
     */
    public static Credentials getCredentials(Properties header) {
        // get 'Authorization' field
        String authorizationField = header.getProperty("authorization");

        return getCredentials(authorizationField);
    }

    /**
     * Returns a set of credentials from a given header property bag.
     */
    public static Credentials getCredentials(String authorizationField) {
        // header line might look like this:
        // Authorization: Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==

        if (authorizationField == null)
            return null;

        if (!authorizationField.startsWith("Basic"))
            return null;

        // split by first space
        int firstSpace = authorizationField.indexOf(' ');
        if (firstSpace < 0 && firstSpace < authorizationField.length() - 1)
            return null;

        // get the raw base64 encoded 'username:password' string
        String rawString = authorizationField.substring(firstSpace + 1);

        byte[] rawAuthorisationField = Base64.decode(rawString);
        String authorisationField;
        try {
            authorisationField = new String(rawAuthorisationField, "UTF8");
        } catch (UnsupportedEncodingException e) {
            return null;
        }

        // get username and password parts separated by ':'
        int colonIndex = authorisationField.indexOf(':');
        if (colonIndex < 0 || colonIndex >= authorisationField.length() - 1)
            return null;

        String usernamePart = authorisationField.substring(0, colonIndex);
        String passwordPart = authorisationField.substring(colonIndex + 1, authorisationField.length());

        Credentials result = new Credentials(usernamePart, passwordPart);
        return result;
    } // (method)

    /**
     * Pluggable strategy for asynchronously executing requests.
     *
     * @param asyncRunner new strategy for handling threads.
     */
    public void setAsyncRunner(IAsyncRunner asyncRunner) {
        this.asyncRunner = asyncRunner;
    }

    /**
     * Pluggable strategy for creating and cleaning up temporary files.
     *
     * @param tempFileManagerFactory new strategy for handling temp files.
     */
    public void setTempFileManagerFactory(IFactory<ITempFileManager> tempFileManagerFactory) {
        this.tempFileManagerFactory = tempFileManagerFactory;
    }

    /**
     * Start the server.
     *
     * @throws IOException if the socket is in use.
     */
    public void start() throws IOException {
        start(NanoHTTPD.SOCKET_READ_TIMEOUT);
    }

    /**
     * Starts the server (in setDaemon(true) mode).
     */
    public void start(final int timeout) throws IOException {
        start(timeout, true);
    }

    /**
     * Start the server.
     *
     * @param timeout timeout to use for socket connections.
     * @param daemon  start the thread daemon or not.
     * @throws IOException if the socket is in use.
     */
    public void start(final int timeout, boolean daemon) throws IOException {
        this.myServerSocket = this.getServerSocketFactory().create();
        this.myServerSocket.setReuseAddress(true);

        ServerRunnable serverRunnable = createServerRunnable(timeout);
        this.myThread = new Thread(serverRunnable);
        this.myThread.setDaemon(daemon);
        this.myThread.setName("NanoHttpd Main Listener");
        this.myThread.start();

        while (!serverRunnable.hasBinded() && serverRunnable.getBindException() == null) {
            try {
                Thread.sleep(10L);
            } catch (Throwable e) {
                // on android this may not be allowed, that's why we
                // catch throwable the wait should be very short because we are
                // just waiting for the bind of the socket
            }
        }
        if (serverRunnable.getBindException() != null) {
            throw serverRunnable.getBindException();
        }
    }

    /**
     * Stop the server.
     */
    public void stop() {
        try {
            safeClose(this.myServerSocket);
            this.asyncRunner.closeAll();
            if (this.myThread != null) {
                this.myThread.join();
            }
        } catch (Exception e) {
            NanoHTTPD.LOG.log(Level.SEVERE, "Could not stop all connections", e);
        }
    }

    public final boolean wasStarted() {
        return this.myServerSocket != null && this.myThread != null;
    }

    private static class NullOutputStream extends OutputStream {

        @Override
        public void write(int arg0) throws IOException {
            // do nothing
        }

        @Override
        public void write(byte[] b) throws IOException {
            // do nothing
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            // do nothing
        }

    } // (class)

    private static NullOutputStream staticNullOutputStream = new NullOutputStream();

    private static PrintStream staticNullPrintStream = new PrintStream(staticNullOutputStream);

    // Change these if you want to log to somewhere else than stdout
    protected static PrintStream myOut = staticNullPrintStream;
    protected static PrintStream myErr = staticNullPrintStream;

    public static String uriIntoPath(String uri) {
        // Remove URL arguments
        uri = uri.trim().replace(File.separatorChar, '/');
        if (uri.indexOf('?') >= 0)
            uri = uri.substring(0, uri.indexOf('?'));

        // Prohibit getting out of current directory
        if (uri.startsWith("..") || uri.endsWith("..") || uri.indexOf("../") >= 0)
            return null;

        return uri;
    }
}
