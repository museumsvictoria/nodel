package org.nodel.toolkit.windows;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import org.nodel.Strings;
import org.nodel.Version;
import org.nodel.io.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This uses the C-Sharp compiler to create an executable that utilises Windows native Job objects when
 * processes are created. It results in strictly controlled process trees and prevents process orphaning.
 */
public class ProcessSandboxExecutable {
    
    // (e.g. C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe)
    private final static String COMPILER_PATH_FORMAT = "%s\\Microsoft.NET\\Framework%s\\v4.0.30319\\csc.exe";
    
    private static Logger s_logger = LoggerFactory.getLogger(ProcessSandboxExecutable.class.getName());
    
    private File _path;
    
    /**
     * Will return the path to the EXE or null if one could not be compiled
     */
    public File tryGetPath() {
        return _path;
    }
    
    /**
     *  
     * (called once)
     */
    private ProcessSandboxExecutable() {
        // check if Windows
        String WINDIR = System.getenv("WINDIR");
        if (Strings.isBlank(WINDIR))
            return;
        
        // target temp exe (e.g. C:\Temp\ProcessSandbox-v2.1.233.exe)
        File tmpExeFile = new File(System.getProperty("java.io.tmpdir"), "nodel-proc-sandbox-" + Version.shared().version + ".exe");
        
        // if it exists, use it
        if (tmpExeFile.exists() && tmpExeFile.length() > 0) {
            _path = tmpExeFile;
            return;
        }
        
        // from here, assume correct conditions and work fully OR fail and consume exception
        
        Process process = null;
        File src = null;
        
        try {
            // extract file, writing out to temporary spot
            try (InputStream is = ProcessSandboxExecutable.class.getResourceAsStream("src_process-sandbox.cs")) {
                src = File.createTempFile("src_process-sandbox", ".cs");
                src.deleteOnExit();

                Stream.writeFully(is, src);
                
            } catch (IOException exc) {
                throw new RuntimeException("Could not extract process-sandbox.cs source file");
            }            
            
            // entry-point to compiler
            String PROCESSOR_ARCHITECTURE = System.getenv("PROCESSOR_ARCHITECTURE");
            String compilerLocation = String.format(COMPILER_PATH_FORMAT, WINDIR, Strings.isBlank(PROCESSOR_ARCHITECTURE) ? "" : "64");
            
            // start compiler
            List<String> args = Arrays.asList(compilerLocation, "-out:" + tmpExeFile.getAbsolutePath(), src.getAbsolutePath());
            s_logger.info("Using arg list: " + args);
            
            process = new ProcessBuilder(args)
                    .start();
            
            // wait for completion
            int result = process.waitFor();
            if (result != 0)
                throw new RuntimeException("No SUCCESS from compiler");
            
            // update path field
            _path = tmpExeFile;
            
        } catch (Exception exc) {
            s_logger.info("Failed while dynamically compiling ProcessSandbox.exe", exc);
            return;
            
        } finally {
            if (process != null)
                process.destroy();
            
            if (src != null)
                src.delete();
        }
    }
    
    /**
     * If on Windows, compiles ProcessSandbox.exe. Blocking, but always gracefully returns    
     */
    public static ProcessSandboxExecutable instance() {
        return Lazy.INSTANCE;
    }    

    /**
     * Singleton related
     */
    private static class Lazy {
        private static final ProcessSandboxExecutable INSTANCE = new ProcessSandboxExecutable();
    }

}
