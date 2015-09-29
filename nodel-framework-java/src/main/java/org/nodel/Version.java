package org.nodel;

import java.io.InputStream;

import org.nodel.io.Stream;
import org.nodel.reflection.Serialisation;
import org.nodel.reflection.Value;

/**
 * Example:
 * { "project" : "nodel-framework-java",
 *   "branch" : "Stable",
 *   "version" : "2.0.10-stable_r381",
 *   "id" : "d13377df0031+ (Stable) tip",
 *   "host" : "THEBUILDSERVER",
 *   "date" : "25-Feb-2015 13:43:35" }
 */

public class Version {
    
    @Value(name = "project", order = 1)
    public String project = "unset";

    @Value(name = "branch", order = 2)
    public String branch = "unset";

    @Value(name = "version", order = 3)
    public String version = "unset";

    @Value(name = "id", order = 4)
    public String id = "unset";

    @Value(name = "date", order = 5)
    public String date = "unset";
    
    /**
     * Extracts a version object. Should only be used once.
     */
    private static Version extractVersion() {
        Version result = null;
        InputStream is = null;

        try {
            is = Version.class.getResourceAsStream("build.json");

            if (is != null) {
                String versionStr = Stream.readFully(is);
                result = (Version) Serialisation.deserialise(Version.class, versionStr);
            }
        } catch (Exception exc) {
            // ignore
        } finally {
            Stream.safeClose(is);
        }

        return (result != null ? result : new Version());
    }
    
    /**
     * (singleton, thread-safe, non-blocking)
     */
    private static class Instance {

        private static final Version INSTANCE = extractVersion();

    }

    /**
     * Returns the singleton instance of this class.
     */
    public static Version shared() {
        return Instance.INSTANCE;
    }

}
