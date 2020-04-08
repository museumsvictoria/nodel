package org.nodel.toolkit;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.nodel.Handler;
import org.nodel.Handler.H0;
import org.nodel.Handler.H1;
import org.nodel.core.Nodel;
import org.nodel.Strings;
import org.nodel.host.BaseDynamicNode;
import org.nodel.host.BaseNode;
import org.nodel.io.Stream;
import org.nodel.io.UTF8Charset;
import org.nodel.reflection.Serialisation;
import org.nodel.reflection.Value;
import org.nodel.threading.ThreadPool;
import org.nodel.threading.TimerTask;
import org.nodel.threading.Timers;

public class QuickProcess implements Closeable {
    
    /**
     * (threading)
     */
    private Object _lock = new Object();
    
    /**
     * Permanently closed flag
     */
    private boolean _closed = false;

    /**
     * (toolkit related)
     */
    private H0 _threadStateHandler;
    
    /**
     * (toolkit related)
     */
    private ThreadPool _threadPool;

    /**
     * (toolkit related)
     */
    private Timers _timers;
    
    /**
     * (toolkit related)
     */
    H1<Exception> _callbackExceptionHandler;
    
    /**
     * (toolkit related)
     */
    private Handler.H0 _closedHandler;
    
    /**
     * (from toolkit)
     */
    private BaseNode _parentNode;    

    /**
     * (arg instance)
     */
    private List<String> _command;

    /**
     * (arg instance)
     */
    private String _stdinPush;

    /**
     * (arg instance)
     */
    private H1<Integer> _onStarted;

    /**
     * (arg instance)
     */
    private H1<FinishedArg> _onFinished;

    /**
     * (arg instance)
     */
    private long _timeout;

    /**
     * (arg instance)
     */
    private String _working;
    
    /**
     * (arg instance)
     */    
    private Map<String, String> _env;

    /**
     * (arg instance)
     */
    private boolean _mergeErr;

    /**
     * The process object.
     */
    private Process _process;

    /**
     * Kills the process on timeout.
     */
    private TimerTask _timeoutTimer;

    public static class FinishedArg {
        
        @Value(name = "code", order = 1, desc = "The exit code (or null if timed out)")
        public Integer code;

        @Value(name = "stdout", order = 3)
        public String stdout;
        
        @Value(name = "stderr", order = 4)
        public String stderr;
        
        @Override
        public String toString() {
            return Serialisation.serialise(this);
        }
        
    }
    
    /**
     * Constructs a new quick process.
     */
    public QuickProcess(H0 threadStateHandler, ThreadPool threadPool, Timers timers, H1<Exception> callbackExceptionHandler,  BaseDynamicNode parentNode,
            List<String> command, String stdinPush, H1<Integer> onStarted, H1<FinishedArg> onFinished, long timeout, String working, boolean mergeErr, Map<String, String> env) {
        
        // validate command list
        if (command == null || command.size() < 1 || Strings.isBlank(command.get(0)))
            throw new RuntimeException("The command list was missing or empty.");
        
        _threadStateHandler = threadStateHandler;
        _callbackExceptionHandler = callbackExceptionHandler;
        _threadPool = threadPool;
        _timers = timers;
        _parentNode = parentNode;
        
        _command = command;
        _stdinPush = stdinPush;
        _onStarted = onStarted;
        _onFinished = onFinished;
        _timeout = timeout;
        _working = working;
        _mergeErr = mergeErr;
        _env = env;
    }
    
    /**
     * Callback for when closed and clean up can be taken.
     */
    protected void setClosedHandler(Handler.H0 handler) {
        _closedHandler = handler;
    }
    
    /**
     * Starts the quick-process (allowing for handlers to have been wired up)
     * (returns immediately)
     */
    protected void go() {
        _threadPool.execute(new Runnable() {

            @Override
            public void run() {
                threadMain();
            }

        });
    }

    /**
     * (thread entry-point)
     */
    private void threadMain() {
        // need to be run for every thread entry
        _threadStateHandler.handle();
        
        List<String> origCommand = _command;
        
        List<String> command; // the command list that will be used
        
        // if on Windows, use the ProcessSandbox.exe utility if it can be found...
        File processSandboxFile = ManagedProcess.resolveProcessSandbox(_parentNode, origCommand);

        if (processSandboxFile != null) {
            // prepend the sandbox and the arguments it needs
            ArrayList<String> list = new ArrayList<String>();
            list.add(processSandboxFile.getAbsolutePath());
            list.add("--ppid");
            list.add(String.valueOf(Nodel.getPID()));
            list.addAll(origCommand);

            command = list;
        } else {
            // just use the original
            command = origCommand;
        }        
        
        ProcessBuilder pb = new ProcessBuilder(command);
        
        // merge the error stream?
        if (_mergeErr)
            pb.redirectErrorStream(true);
        
        // modify the environment variables
        if (_env != null)
            pb.environment().putAll(_env);
        
        // adjust the working directory?
        if (!Strings.isBlank(_working)) {
            File workingDir = new File(_working);
            if (!workingDir.exists() || !workingDir.isDirectory()) {
                // (error will be logged)
                _callbackExceptionHandler.handle(new FileNotFoundException("Working directory '" + _working + "' could not be found or is not a directory."));
                
                // nothing to do but abort
                doClose();
                return;
            }
            pb.directory(workingDir);
            
        } else {
            // (node's root)
            pb.directory(_parentNode.getRoot());
        }
        
        Process process;

        try {
            // actually start the process
            process = pb.start();

            synchronized (_lock) {
                // kick off timeout timer
                if (_timeout > 0) {
                    _timeoutTimer = _timers.schedule(new TimerTask() {

                        @Override
                        public void run() {
                            synchronized (_lock) {
                                if (_closed)
                                    return;
                                
                                // a process still exists, so destroy it
                                doClose();
                                
                                // send through a null argument to indicate timeout
                                Handler.tryHandle(_onFinished, null, _callbackExceptionHandler);
                            }
                        }

                    }, _timeout);
                }

                if (_closed) {
                    process.destroy();
                    return;
                } else {
                    _process = process;
                }
            }

            // get all streams
            InputStream stdout = process.getInputStream();
            OutputStream stdin = process.getOutputStream();
            
            // get process ID?
            // NOTE: THIS IS NOT THE PROCESS ID, NATIVE JAVA DOES NOT SUPPORT IT
            int processID = process.hashCode();
            
            // fire 'started' callback
            Handler.tryHandle(_onStarted, processID, _callbackExceptionHandler); 
            
            // anything to push out stdin?
            if (!Strings.isEmpty(_stdinPush)) {
                stdin.write(_stdinPush.getBytes(UTF8Charset.instance()));
            }
            
            // read all of stdout
            byte[] stdoutBuffer = Stream.readFullyIntoBuffer(stdout);
            String stdoutCapture = new String(stdoutBuffer, 0, stdoutBuffer.length, UTF8Charset.instance());
            
            // does 'stderr' also need to be captured?
            String stderrCapture = null;
            if (!_mergeErr) {
                InputStream stderr = process.getErrorStream();
                byte[] stderrBuffer = Stream.readFullyIntoBuffer(stderr);
                stderrCapture = new String(stderrBuffer, 0, stderrBuffer.length, UTF8Charset.instance());
            }
            
            
            FinishedArg arg = new FinishedArg();
            arg.code = process.waitFor();
            arg.stdout = stdoutCapture;
            arg.stderr = stderrCapture;
            
            // fire 'finished' handler if not close
            if (!_closed)
                Handler.tryHandle(_onFinished, arg, _callbackExceptionHandler);
            
            doClose();
            
        } catch (Exception exc) {
            // gracefully exit if closed
            if (_closed)
                return;
           
            Handler.tryHandle(_callbackExceptionHandler, new RuntimeException("quick-launch error: " + exc.getMessage()));
            
            doClose();            
        }
    }

    @Override
    public void close() throws IOException {
        doClose();
    }
    
    /**
     * (exception less version)
     */
    private void doClose() {
        synchronized (_lock) {
            if (_closed)
                return;

            _closed = true;
            
            if (_timeoutTimer != null)
                _timeoutTimer.cancel();

            if (_process != null) {
                final Process process = _process;
                
                _process = null;
                
                _threadPool.execute(new Runnable() {

                    @Override
                    public void run() {
                        try {
                            process.destroy();
                        } finally {
                            _closedHandler.handle();
                        }
                    }

                });
            } else {
                _closedHandler.handle();
            }
        }
    }
    
}
