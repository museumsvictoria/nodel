package org.nodel.jyhost;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.nodel.StartupException;
import org.nodel.Version;
import org.nodel.core.Nodel;
import org.nodel.host.BootstrapConfig;
import org.nodel.host.NanoHTTPD;
import org.nodel.io.Files;
import org.nodel.io.Packages;
import org.nodel.io.Stream;
import org.nodel.json.JSONArray;
import org.nodel.json.JSONException;
import org.nodel.json.JSONObject;
import org.nodel.jyhost.NodelHost;
import org.nodel.jyhost.NodelHostHTTPD;
import org.nodel.logging.slf4j.SimpleLogger;
import org.nodel.reflection.Schema;
import org.nodel.reflection.Serialisation;
import org.nodel.reflection.Value;
import org.python.core.Py;
import org.python.core.PyDictionary;
import org.python.core.PyList;
import org.python.core.PyNone;
import org.python.core.PyObject;
import org.python.core.adapter.PyObjectAdapter;
import org.python.util.PythonInterpreter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main program entry-point.
 */
public class Launch {
    
    /**
     * Program version.
     */
    public final static String VERSION = Version.shared().version;
    
    /**
     * (initialised late in 'initLogging' depending on config)
     */
    private Logger _logger;
    
    /**
     * Contains the least amount of config required to get the program running.
     */
    private BootstrapConfig _bootstrapConfig;
    
    /**
     * The root directory this program will use.
     */
    private File _root = new File(".");
    
    /**
     * The nodel host
     */
    protected NodelHost _nodelHost;

    /**
     * Holds the program arguments.
     */
    private static String[] s_processArgs;
    
    public Launch() throws StartupException, IOException, JSONException {
        bootstrap();
        
        start();
    }
    
    public Launch(String[] args) throws StartupException, IOException, JSONException {
        s_processArgs = args;
        
        bootstrap();
        
        start();
    } // (method)
    
    /**
     * Console launch entry-point.
     */
    public static void main(String[] args) throws IOException, JSONException, StartupException {
        Launch launch = new Launch(args);
        
        System.out.println("Nodel [Jython] v" + VERSION + " is running.");
        System.out.println();
        System.out.println("Press Enter to initiate a shutdown.");
        System.out.println();
        System.in.read();
        
        System.out.println("Shutdown initiated...");
        
        launch.shutdown();
        
        System.out.println("Finished.");
    }
    
    /**
     * Performs entire bootstrap process.
     * (can throw exceptions)
     */
    private void bootstrap() throws IOException, JSONException, StartupException {
        String bootStrapPrefix = "bootstrap";
        
        // dump the bootstrap config schema if it hasn't already been dumped
        Object schema = Schema.getSchemaObject(BootstrapConfig.class);
        String schemaString = Serialisation.serialise(schema, 4);
        
        // load for the bootstrap config schema file    
        File bootstrapSchemaFile = new File(_root, "_" + bootStrapPrefix + "_schema.json");
        String bootstrapSchemaFileString = null;
        if(bootstrapSchemaFile.exists()) {
            bootstrapSchemaFileString = Stream.readFully(bootstrapSchemaFile);
        }
        
        // only write out the schema file if it's different or never been written out
        if(bootstrapSchemaFileString == null || !bootstrapSchemaFileString.equals(schemaString)) {
            Stream.writeFully(bootstrapSchemaFile, schemaString);
        }
        
        // dump the example config schema if it hasn't already been dumped
        String exampleBootstrapString = Serialisation.serialise(BootstrapConfig.Example, 4);
        
        // load for the bootstrap config schema file    
        File exampleBootstrapFile = new File(_root, "_" + bootStrapPrefix + "_example.json");
        String exampleBootstrapFileString = null;
        if(exampleBootstrapFile.exists()) {
            exampleBootstrapFileString = Stream.readFully(exampleBootstrapFile);
        }
        
        // only write out the schema file if it's different or never been written out
        if(exampleBootstrapFileString == null || !exampleBootstrapFileString.equals(exampleBootstrapString)) {
            Stream.writeFully(exampleBootstrapFile, exampleBootstrapString);
        }        
        
        // now look for the bootstrap config itself
        File bootstrapFile = new File(_root, bootStrapPrefix + ".json");
        if (bootstrapFile.exists()) {
            _bootstrapConfig = (BootstrapConfig) Serialisation.coerceFromJSON(BootstrapConfig.class, Stream.readFully(bootstrapFile));
        } else {
            _bootstrapConfig = new BootstrapConfig();
        }
        
        // provide the REST API schema too
        String apiSchema = Serialisation.serialise(Schema.getSchemaObject(NodelHostHTTPD.RESTModel.class), 4);
        File apiSchemaFile = new File(_root, "_API" + "_schema.json");
        String existing = apiSchemaFile.exists() ? Stream.readFully(apiSchemaFile) : null;
        if (!apiSchema.equals(existing))
            // update the file
            Stream.writeFully(apiSchemaFile, apiSchema);

        initLogging();
    } // (method)
    
    private void start() throws IOException {
        // check for multihomed host
        if (_bootstrapConfig.getNetworkInterface() == null) {
            checkForMultihoming(null);
        } else {
            Inet4Address addr = checkForMultihoming(_bootstrapConfig.getNetworkInterface());
            Nodel.setInterfaceToUse(addr);
        }
        
        // check if any "direct" hard links (assisted multicast using unicast) addresses have been set
        if (_bootstrapConfig.getHardLinksAddresses() != null)
            Nodel.setHardLinksAddresses(Arrays.asList(_bootstrapConfig.getHardLinksAddresses()));

        // check if advertisements should be disabled
        if (_bootstrapConfig.getDisableAdvertisements()) {
            Nodel.setDisableServerAdvertisements(true);
        }
        
        initialisePython();

        _logger.info("Nodel [Jython] is starting... version=" + VERSION);

        // Only relative paths will be allowed

        // verify the content directory exists (or can be created)
        File contentDirectory = prepareDirectory("content", _root, _bootstrapConfig.getContentDirectory());

        // prepare 'custom' which holds user and admin folders
        File customRoot = prepareDirectory("custom", _root, "custom");

        // prepare 'custom content' which holds custom content files (used as first preference)
        File customContentDirectory = prepareDirectory("custom content", customRoot, _bootstrapConfig.getContentDirectory());
        
        // now the pyNode httpd end-point
        NodelHostHTTPD nodelHostHTTPD = new NodelHostHTTPD(_bootstrapConfig.getNodelHostPort(), contentDirectory);
        nodelHostHTTPD.setFirstChoiceDir(customContentDirectory);
        _logger.info("Started HTTP interface on port " + _bootstrapConfig.getNodelHostPort());
        
        // start the WebSocket server
        NodelHostWebSocketServer nodelHostWSServer = new NodelHostWebSocketServer(0);
        nodelHostWSServer.start(90000);
        _logger.info("Started WebSocket server on port " + nodelHostWSServer.getListeningPort());
        Nodel.setWebSocketPort(nodelHostWSServer.getListeningPort());
        
        // retrieve '.version' file to determine whether embedded packages need to be extracted
        String version = null;
        File versionFile = new File(_root, ".version");
        if (versionFile.exists()) {
            try {
                version = Stream.readFully(versionFile);
            } catch (Exception exc) {
                // ignore
            }
        }

        if (!VERSION.equalsIgnoreCase(version)) {
            // different versions, so flush and extract
            _logger.info("Previous version (if any) is different to current version. Cleaning out contents of package folders (if present) and extracting current...");

            long deleted;

            deleted = Files.tryFlushDir(contentDirectory);
            _logger.info("For 'content', flushed " + deleted + " file(s).");

            extractEmbeddedPackage("content", contentDirectory);

            _logger.info("'admin' and 'content' packages have been extracted.");
            
            // update the version stamp so extraction isn't done again
            Stream.writeFully(versionFile, VERSION);
        }
        
        // have got config now (default one or one from disk) so
        // fire up pyNode console
        File nodelRoot = prepareDirectory("nodelRoot", _root, _bootstrapConfig.getNodelRoot());

        _nodelHost = new NodelHost(nodelRoot, _bootstrapConfig.getInclFilters(), _bootstrapConfig.getExclFilters());

        nodelHostHTTPD.setNodeHost(_nodelHost);

        Nodel.setHTTPPort(nodelHostHTTPD.getPort());

        // kick off the HTTPDs
        nodelHostHTTPD.start();

        // that's all we need for bootstrap loading.
        // everything else can fail now if it wants to

        _logger.info("All interfaces and end-points have been successfully launched. httpPort=" + nodelHostHTTPD.getPort());

        if (_bootstrapConfig.getDisableAdvertisements())
            _logger.info("(advertisements are disabled)");       
    } // (method)
    
    /**
     * Checks for a multihomed environment.
     */
    private Inet4Address checkForMultihoming(byte[] find) throws SocketException {
        List<NetworkInterface> raw = Collections.list(NetworkInterface.getNetworkInterfaces());
        List<NetworkInterface> filtered = new ArrayList<NetworkInterface>();
        
        List<Inet4Address> inet4Addresses = new ArrayList<Inet4Address>(); 
        
        for (final NetworkInterface inf : raw) {
            if (inf.getMTU() <= 0 || 
                !inf.isUp() ||
                !inf.supportsMulticast())
                continue;
            
            Inet4Address lastAddr = null;
            
            for(InetAddress addr : Collections.list(inf.getInetAddresses())) {
                if (addr instanceof Inet4Address) {
                    lastAddr = (Inet4Address) addr;
                    inet4Addresses.add(lastAddr);
                }
            } // (for)
            
            if (lastAddr != null && find != null && isEqual(inf.getHardwareAddress(), find))
                return lastAddr;
            
            filtered.add(inf);
        } // (for)
        
        if (find == null && inet4Addresses.size() == 1) {
            // all okay
            _logger.info("This host does not appear to be multihomed; binding to only IPv4 interface (current address '" + inet4Addresses.get(0) + "')");
            
            return inet4Addresses.get(0); 
        }        
        
        for (final NetworkInterface inf : filtered) {
            Object wrapper = new Object() {
                
                @Value(name = "displayName")
                public String displayName = inf.getDisplayName();

                @Value(name = "hardwareAddr")
                public byte[] hardwareAddr = inf.getHardwareAddress();

                // JDK 7 only
                // @Value
                // public int index = inf.getIndex();

                @Value(name = "addresses")
                public List<InterfaceAddress> addresses = inf.getInterfaceAddresses();

                @Value(name = "mtu")
                public int mtu = inf.getMTU();

                @Value(name = "name")
                public String name = inf.getName();
                
            };

            System.err.println(Serialisation.serialise(wrapper));
        }
        
        System.err.println();
        System.err.println("NOTE:");
        if (find != null)
            System.err.println("      Could not find specified network interface based on 'hardwareAddr'.");

        System.err.println("      This host appears to be multihomed; a network interface must be chosen.");
        System.err.println("      Please use a 'hardwareAddr' from one of the above adapters and update");
        System.err.println("      your 'bootstrap.config' file.");
        
        System.exit(-1);
        
        // dead code:
        return null;
    } // (method)
    
    /**
     * Compares two buffers
     */
    private static boolean isEqual(byte[] buffer1, byte[] buffer2) {
        if (buffer1 == null)
            if (buffer2 != null)
                return false;
        
        if (buffer2 == null)
            if (buffer1 != null)
                return false;
        
        int length = buffer1.length;
        
        if (buffer2.length != length)
            return false;
        
        for (int a=0; a<length; a++)
            if (buffer1[a] != buffer2[a])
                return false;
        
        return true;
    } // (method)

    /**
     * Utility method to ensure a directory exist, failing if it's not possible
     * to create one.
     */
    private static File prepareDirectory(String type, File root, String dirString) throws IOException {
        File dir = new File(dirString);
        if (!dir.isAbsolute())
            dir = new File(root, dirString);
        
        if (dir.exists() && !dir.isDirectory())
            throw new IOException("'" + type + "' directory exists but is not a directory.");

        // make the admin directory if one doesn't already exist
        if (!dir.exists())
            if (!dir.mkdirs())
                throw new IOException("Could not create '" + type + "' directory.");

        return dir;
    }

    /**
     * Extracts an embedded package, will generally only be called once.
     * @throws IOException 
     */
    private void extractEmbeddedPackage(String packageType, File outDirectory) throws IOException {
        // use the default package if necessary
        _logger.info("Unzipping embedded  '" + packageType + "' package...");

        InputStream is = null;
        try {
            is = NanoHTTPD.class.getResourceAsStream(packageType + ".zip");
            if (is != null) {
                if (!Packages.unpackZip(is, outDirectory))
                    throw new IOException("Could not extract package '" + packageType + "'");
            }
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException exc) {
                }
            }
        }
    }
    
    /**
     * Permanently shuts down the nodel host
     */
    public void shutdown() {
        if (_nodelHost != null)
            _nodelHost.shutdown();
    }
    
    /**
     * Configures all Python adapters.
     * Needs to be called once before using any interpreters.
     */
    private static void initialisePython() {
        PythonInterpreter.initialize(System.getProperties(), null, s_processArgs);
        
        // JSONObject.NULL
        Py.getAdapter().addPostClass(new PyObjectAdapter() {
            
            @Override
            public boolean canAdapt(Object o) {
                return JSONObject.NULL.equals(o);
            }
            
            @Override
            public PyObject adapt(Object o) {
                return PyNone.TYPE;
            }
            
        });
        
        // JSONArray
        Py.getAdapter().addPostClass(new PyObjectAdapter() {
            
            @Override
            public boolean canAdapt(Object o) {
                return o instanceof JSONArray;
            }

            @Override
            public PyObject adapt(Object o) {
                JSONArray jsonArray = (JSONArray) o;

                int len = jsonArray.length();
                
                PyList pyList = new PyList();

                for (int a = 0; a < len; a++) {
                    try {
                        Object obj = jsonArray.get(a);
                        pyList.append(Py.java2py(obj));
                    } catch (JSONException e) {
                        // should never get here, just add 'None'
                        pyList.append(PyNone.TYPE);
                    }
                } // (for)

                return pyList;
            }
            
        });
        
        // JSONDict
        Py.getAdapter().addPostClass(new PyObjectAdapter() {
            
            @Override
            public boolean canAdapt(Object o) {
                return o instanceof JSONObject;
            }
            
            @Override
            public PyObject adapt(Object o) {
                JSONObject jsonObject = (JSONObject) o;

                PyDictionary pyDictionary = new PyDictionary();

                for (String key : jsonObject.keySet()) {
                    Object value;
                    try {
                        value = jsonObject.get(key);
                    } catch (JSONException e) {
                        // should never get here, just add 'None'
                        value = PyNone.TYPE;
                    }
                    pyDictionary.put(key, Py.java2py(value));
                } // (for)

                return pyDictionary;
            }
            
        });
    } // (method)
    
    /**
     * Logging related initialisation.
     */
    private void initLogging() {
        try {
            File loggingDir = null;

            if (_bootstrapConfig.getEnableProgramLogging()) {
                loggingDir = prepareDirectory("logs", _root, _bootstrapConfig.getLogsDirectory());

                SimpleLogger.setFolderOut(loggingDir);
            }
            
            _logger = LoggerFactory.getLogger(Launch.class.getName());

            if (loggingDir != null)
                System.out.println("    (file logging is enabled [" + loggingDir.getAbsolutePath() + "])\n");
            
        } catch (Exception exc) {
            System.err.println("Logging configuration failure; logging may or may not be functional.");
            exc.printStackTrace();
        }
    }

}