package org.nodel.jyhost;

import java.io.IOException;
import java.io.InputStream;

import org.nodel.io.Stream;
import org.nodel.io.UnexpectedIOException;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

public class ExampleScript {
    
    private String _generated;
    
    /**
     * Loads from the embedded resource.
     */
    private void initOnce() {
        try {
            try (InputStream is = PyNode.class.getResourceAsStream("example_script.py")) {
                String data = Stream.readFully(is);
                _generated = data.replace("%VERSION%", Launch.VERSION);
            }
        } catch (IOException exc) {
            throw new UnexpectedIOException("While opening 'example_script.py'", exc);
        }
    }
    
    /**
     * Generate an example script file.
     */
    public static String get() {
        return Instance.INSTANCE._generated;
    }
    
    /**
     * (private constructor)
     */
    private ExampleScript() {
        initOnce();
    }

    /**
     * (singleton, thread-safe, non-blocking)
     */
    private static class Instance {
        private static final ExampleScript INSTANCE = new ExampleScript();
    }

}
