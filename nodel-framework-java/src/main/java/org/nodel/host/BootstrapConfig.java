package org.nodel.host;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import org.nodel.Base64;
import org.nodel.reflection.Value;

/**
 * Critical configuration that is used before general initialisation. 
 */
public class BootstrapConfig {
    
    public final static BootstrapConfig Default;
    
    static {
        Default = new BootstrapConfig();
        Default.networkInterface = Base64.decode("Enc3lXyJ");
    }
    
    public final static int DEFAULT_NODELHOST_PORT = 8085;
    
    @Value(name = "NodelHostPort", title = "NodelHost HTTP port", order = 200, required = true)
    private int nodelHostPort = DEFAULT_NODELHOST_PORT;

    public int getNodelHostPort() {
        return this.nodelHostPort;
    }

    public void setPyNodePort(int value) {
        this.nodelHostPort = value;
    }
    
    
    public final static String DEFAULT_NODELROOT_DIRECTORY = "nodes";

    @Value(name = "nodelRoot", title = "Nodel root directory", order = 250, required = false)
    private String nodelRoot = DEFAULT_NODELROOT_DIRECTORY;

    public String getNodelRoot() {
        return this.nodelRoot;
    }

    public void setNodelRoot(String value) {
        this.nodelRoot = value;
    }    
    
    public final static String DEFAULT_CONTENT_DIRECTORY = "content";

    @Value(name = "contentDirectory", title = "Content directory", order = 300, required = true)
    private String contentDirectory = DEFAULT_CONTENT_DIRECTORY;

    public String getContentDirectory() {
        return this.contentDirectory;
    }

    public void setContentDirectory(String value) {
        this.contentDirectory = value;
    }

    public final static String DEFAULT_CONFIG_DIRECTORY = "config";

    @Value(name = "configDirectory", title = "Config directory", order = 400, required = true)
    private String configDirectory = DEFAULT_CONFIG_DIRECTORY;

    public String getConfigDirectory() {
        return this.configDirectory;
    }

    public void setConfigDirectory(String value) {
        this.configDirectory = value;
    }

    public final static String DEFAULT_CACHE_DIRECTORY = "cache";

    @Value(name = "cacheDirectory", title = "Cache directory", order = 500, required = true)
    private String cacheDirectory = DEFAULT_CACHE_DIRECTORY;

    public String getCacheDirectory() {
        return this.cacheDirectory;
    }

    public void setCacheDirectory(String value) {
        this.cacheDirectory = value;
    }

    @Value(name = "deviceFingerprintOverride", title = "Device fingerprint", order = 600, required = true)
    private String deviceFingerprintOverride;

    public String getDeviceFingerprintOverride() {
        return this.deviceFingerprintOverride;
    }

    public void SetDeviceFingerprintOverride(String value) {
        this.deviceFingerprintOverride = value;
    }
    
    // was previously 'http://lumirdnet.appspot.com/serve/nodelHost-current.zip'
    @Value(name = "nodelPackage", title = "Nodel package URL", order = 700, required = true)
    private String nodelPackage = "";

    public String getNodelPackage() {
        return this.nodelPackage;
    }

    public void setNodelPackage(String value) {
        this.nodelPackage = value;
    }
    
    @Value(name = "networkInterface", title = "Network interface", order = 800, required = false)
    private byte[] networkInterface = null;

    public byte[] getNetworkInterface() {
        return this.networkInterface;
    }

    public void setNetworkInterface(byte[] value) {
        this.networkInterface = value;
    }
    
    @Value(name = "disableAdvertisements", title = "Disable advertisements", order = 900, required = false)
    private boolean disableAdvertisements = false;

    public boolean getDisableAdvertisements() {
        return this.disableAdvertisements;
    }

    public void setNetworkInterface(boolean value) {
        this.disableAdvertisements = value;
    }
    
    
    public final static boolean DEFAULT_ENABLE_PROGRAM_LOGGING = false;

    @Value(name = "enableProgramLogging", title = "Enable program logging", order = 1000, required = false)
    private boolean enableProgramLogging = DEFAULT_ENABLE_PROGRAM_LOGGING;

    public boolean getEnableProgramLogging() {
        return this.enableProgramLogging;
    }
    
    public void setEnableProgramLogging(boolean value) {
        this.enableProgramLogging = value;
    }    

} // (class)
