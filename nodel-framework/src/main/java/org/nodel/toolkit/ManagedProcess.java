package org.nodel.toolkit;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

import org.nodel.Handler;
import org.nodel.Handler.H0;
import org.nodel.Handler.H1;
import org.nodel.core.Nodel;
import org.nodel.Strings;
import org.nodel.Threads;
import org.nodel.diagnostics.CountableInputStream;
import org.nodel.diagnostics.CountableOutputStream;
import org.nodel.diagnostics.Diagnostics;
import org.nodel.diagnostics.SharableMeasurementProvider;
import org.nodel.host.BaseNode;
import org.nodel.io.BufferBuilder;
import org.nodel.io.Stream;
import org.nodel.threading.CallbackQueue;
import org.nodel.threading.ThreadPool;
import org.nodel.threading.TimerTask;
import org.nodel.threading.Timers;
import org.nodel.toolkit.windows.ProcessSandboxExecutable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * For managed OS processes.
 * 
 *  Features include:
 *  - staggered start up (prevent startup storms)
 *  - event-based (asynchronous)
 *  - efficient stream filtering
 *    - minimisation of String object fragmentation 
 *    - automatic delimiting
 *    - UTF8 decoded
 *    - trimmed
 *  - exponential back-off
 */
public class ManagedProcess implements Closeable {
    
    /**
     * On Windows a Process Sandbox utility can be used which, at a minimum, should manage
     * the clean up of child processes.
     * 
     * Expected usage:
     * [--ppid PARENT_PROCESS_ID] args...
     * 
     * (non-final to allow runtime changes)
     */
    public static String PROCESS_SANDBOX = "ProcessSandbox.exe";
    
    private static AtomicLong s_instanceCounter = new AtomicLong();
    
    /**
     * (used by 'logger' and thread name)
     */
    private long _instance = s_instanceCounter.getAndIncrement();
    
    /**
     * (logging related)
     */
    private Logger _logger = LoggerFactory.getLogger(String.format("%s.instance%d", this.getClass().getName(), _instance));
    
    /**
     * The kick-off delay (randomized)
     */
    private final static int KICKOFF_DELAY = 5000;
    
    /**
     * To randomise the kick-off delay
     */
    private final static Random s_random = new Random();
    
    /**
     * The maximum segment allowed between delimiters (default 2 MB)
     */
    private final static int MAX_SEGMENT_ALLOWED = 2 * 1024 * 1024;

    /**
     * The minimum gap between automatic process relaunches (500ms).
     */
    private final static int MIN_START_GAP = 500;
    
    /**
     * How long to backoff if the process fails to start (15s)
     */
    private final static int BACKOFF_ON_FAULT = 15000;

    /**
     * (synchronisation / locking)
     */
    private Object _lock = new Object();
    
    /**
     * The parent node.
     */
    private BaseNode _parentNode;
    
    /**
     * The shared thread-pool
     */
    private ThreadPool _threadPool;
    
    /**
     * The safe queue as provided by a host
     */
    private CallbackQueue _callbackHandler;    
    
    /**
     * If there was a recent start of the process
     */
    private boolean _gracefulStart = false;

    /**
     * (see setter)
     */
    private H0 startedCallback;

    /**
     * (see setter)
     */
    private H1<Integer> _stoppedCallback;

    /**
     * (see setter)
     */
    private H1<String> _stdoutCallback;
    
    /**
     * (see setter)
     */
    private H1<String> _stderrCallback;    

    /**
     * (see setter)
     */
    private H1<String> _stdinCallback;

    /**
     * (see setter)
     */
    private H0 _timeoutCallback;
    
    /**
     * When errors occur during callbacks.
     */
    private H1<Exception> _callbackErrorHandler;

    /**
     * Permanently shut down?
     */
    private boolean _shutdown;
    
    /**
     * Start-only once?
     */
    private boolean _startOnce = false;
    
    private enum State {
        Stopped, Started
    }
    
    /**
     * Started or stopped
     * (synchronised around 'lock')
     */
    private State _state = State.Started;

    /**
     * The receive thread.
     */
    private Thread _thread;
    
    /**
     * Shared timer framework to use.
     */
    private Timers _timerThread;
    
    /**
     * The current socket.
     * (must be set and cleared with 'outputStream')
     * (synced around 'lock')
     */
    private Process _process;
    
    /**
     * The output stream
     * (if a field because its accessed by other threads)
     * (must be set and clear with 'outputStream')
     */
    private OutputStream _outputStream;
    
    /**
     * The start timer.
     * (synced around 'lock')
     */
    private TimerTask _startTimer;
    
    /**
     * Holds the full list of launch args, process path included.
     * (may be null)
     */
    private List<String> _command;
    
    /**
     * (see setter)
     */
    private String _working;
    
    /**
     * (see setter)
     */
    private boolean _mergeError;
    
    /**
     * (see setter)
     */
    private Map<String, String> _env;    
    
    /**
     * The delimiters to split the receive data on.
     */
    private String _receiveDelimiters = "\r\n";
    
    /**
     * The delimiters to split the send data on.
     */
    private String _sendDelimiters = "\n";
    
    /**
     * Simple parse modes.
     */
    private enum Modes {
        UnboundedRaw,
        CharacterDelimitedText
    }
    
    /**
     * If length delimited mode is being used with start / stop flags
     */
    private Modes _mode = Modes.CharacterDelimitedText;
    
    /**
     * The default request timeout value (timed from respective 'send')
     */
    private int _requestTimeout = 10000;
    
    /**
     * The request timeout value (timed from respective 'queue')
     */
    private int _longTermRequestTimeout = 60000;
    
    /**
     * (Response for handling thread-state)
     */
    private H0 _threadStateHandler;

    /**
     * (diagnostics)
     */
    private SharableMeasurementProvider _counterLaunches;
    
    /**
     * (diagnostics)
     */    
    private SharableMeasurementProvider _counterStdoutRate;
    
    /**
     * (diagnostics)
     */    
    private SharableMeasurementProvider _counterStdinRate;
    
    /**
     * (diagnostics)
     */    
    private SharableMeasurementProvider _counterStderrRate;
    
    /**
     * The request queue
     */
    private ConcurrentLinkedQueue<QueuedRequest> _requestQueue = new ConcurrentLinkedQueue<QueuedRequest>();
    
    /**
     * Holds the queue length ('ConcurrentLinkedQueue' not designed to handle 'size()' efficiently)
     */
    private int _queueLength = 0;
    
    /**
     * The active request
     */
    private QueuedRequest _activeRequest;

    /**
     * Gets initialised once, during connection loop and then never again.
     */
    private byte[] _buffer;

    /**
     * (constructor)
     */
    public ManagedProcess(BaseNode node, List<String> command, H0 threadStateHandler, H1<Exception> callbackExceptionHandler, CallbackQueue callbackQueue, ThreadPool threadPool, Timers timers) {
        _parentNode = node;
        
        _command = command;
        
        _threadStateHandler = threadStateHandler;
        _callbackErrorHandler = callbackExceptionHandler;
        _callbackHandler = callbackQueue;
        _threadPool = threadPool;
        _timerThread = timers;
        
        // set up the connect and receive thread
        _thread = new Thread(new Runnable() {

            @Override
            public void run() {
                begin();
            }
            
        });
        _thread.setName(node.getName().getReducedName() + "_processLaunchAndReceive_" + _instance);
        _thread.setDaemon(true);
        
        // register the counters
        String counterName = "'" + node.getName().getReducedName() + "'";
        _counterLaunches = Diagnostics.shared().registerSharableCounter(counterName + ".Process launches", true);
        _counterStdoutRate = Diagnostics.shared().registerSharableCounter(counterName + ".Process stdout rate", true);
        _counterStderrRate = Diagnostics.shared().registerSharableCounter(counterName + ".Process stderr rate", true);
        _counterStdinRate = Diagnostics.shared().registerSharableCounter(counterName + ".Process stdin rate", true);
    }
    
    /**
     * When the process has been started.
     */
    public void setStartedHandler(H0 handler) {
        startedCallback = handler;
    }

    /**
     * When the connected moves into a disconnected state.
     */
    public void setStoppedHandler(H1<Integer> handler) {
        _stoppedCallback = handler;
    }

    /**
     * When a data from stdout arrives.
     */
    public void setOutHandler(H1<String> handler) {
        _stdoutCallback = handler;
    }
    
    /**
     * When a data to stdin is sent
     */
    public void setInHandler(H1<String> handler) {
        _stdinCallback = handler;
    }
    
    /**
     * When a data from stderr arrives.
     */
    public void setErrHandler(H1<String> handler) {
        _stderrCallback = handler;
    }    
    
    /**
     * When a a connector a request timeout occurs.
     */
    public void setTimeoutHandler(H0 handler) {
        _timeoutCallback = handler;
    }
    
    /**
     * Sets the process launch args (path is first element).
     */
    public void setCommand(List<String> value) {
        synchronized(_lock) {
            _command = value;
        }
    }
    
    /**
     * Sets the send delimiters.
     */
    public void setSendDelimeters(String delims) {
        synchronized (_lock) {
            if (delims == null)
                _sendDelimiters = "";
            else
                _sendDelimiters = delims;
        }
    }
    
    /**
     * Sets the receive delimiters.
     */
    public void setReceiveDelimeters(String delims) {
        synchronized(_lock) {
            if (delims == null)
                _receiveDelimiters = "";
            else
                _receiveDelimiters = delims;
            
            if (Strings.isEmpty(_receiveDelimiters))
                _mode = Modes.UnboundedRaw;
        }
    }
    
    /**
     * The request timeout (millis)
     */
    public int getRequestTimeout() {
        return _requestTimeout;
    }
    
    /**
     * The request timeout (millis)
     */
    public void setRequestTimeout(int value) {
        _requestTimeout = value;
    }
    
    /**
     * Returns the current queue length size.
     */
    public int getQueueLength() {
        synchronized (_lock) {
            return _queueLength;
        }
    }
    
    /**
     * Sets the working directory, otherwise leaves as node's root.
     */
    public void setWorking(String value) {
        _working = value;
    }
    
    /**
     * Merge stderr with stdout.
     */
    public void setMergeError(boolean value) {
        _mergeError = value;
    }
    
    /**
     * (see setter)
     */
    public boolean getMergeError() {
        return _mergeError;
    }
    
    /**
     * (see setter)
     */
    public String getWorking() {
        return _working;
    }
    
    /**
     * Sets/extends/overrides environment variables
     */
    public void setEnv(Map<String, String> value) {
        _env = value;
    }
    
    /**
     * (see setter)
     */
    public Map<String, String> getEnv() {
        return _env;
    }    
    
    /**
     * Performs necessary initialisation before either actually starting or stopping
     */
    public void init() {
        synchronized(_lock) {
            if (_startTimer == null && !_shutdown) {
                // kick off after a random amount of time to avoid resource usage spikes
                int kickoffTime = 1000 + s_random.nextInt(KICKOFF_DELAY);

                _startTimer = _timerThread.schedule(_threadPool, new TimerTask() {

                    @Override
                    public void run() {
                        _thread.start();
                    }

                }, kickoffTime);
            }
        }
    }
    
    /**
     * Safely starts this process
     */
    public void start() {
        synchronized(_lock) {
            if (_shutdown || _state == State.Started)
                return;
            
            _state = State.Started;
            
            _lock.notifyAll();
        }
    }
    
    /**
     * Safely starts this process once
     */
    public void startOnce() {
        synchronized(_lock) {
            if (_shutdown || _state == State.Started)
                return;
            
            _startOnce = true;
            
            _state = State.Started;
            
            _lock.notifyAll();
        }
    }    
    
    /**
     * Stops automatically recycling the process
     */
    public void stop() {
        synchronized(_lock) {
            if (_shutdown || _state == State.Stopped)
                return;
            
            _state = State.Stopped;
            
            safeClose(_process);
        }
    }
    
    /**
     * The main thread.
     */
    private void begin() {
        // only need to set the thread state once here
        _threadStateHandler.handle();
        
        while (!_shutdown) {
            boolean exitedCleanly = true;
            
            long backoffTime = MIN_START_GAP;
            
            try {
                // reset flag
                _gracefulStart = false;
                
                // this can be safely used out of sync because fail-safe checking occurs deeper within the method anyway
                // and timing is not critical here
                if (_state == State.Started)
                    launchAndRead();

            } catch (Exception exc) {
                if (_shutdown) {
                    // thread can gracefully exit
                    return;
                }

                exitedCleanly = false;
            }
            
            synchronized(_lock) {
                if (_startOnce) {
                    // force the state change
                    _state = State.Stopped;

                    // and reset the flag
                    _startOnce = false;
                    
                    // (will fall through and wait indefinitely)
                }
                
                if (_state == State.Started) {
                    // still in START mode
                    if (!exitedCleanly) {
                        if (_gracefulStart)
                            backoffTime = MIN_START_GAP;
                        else
                            backoffTime = BACKOFF_ON_FAULT;
                    }

                    Threads.wait(_lock, backoffTime);
                } else {
                    // STOP requested so wait indefinitely (or until signal)
                    Threads.waitOnSync(_lock);
                }
            }
        } // (while)
    }
    
    /**
     * Launches the process and continually reads stdin.
     */
    private void launchAndRead() throws Exception {
        Process process = null;
        OutputStream os = null;
        
        String workingStr = _working;
        
        try {
            List<String> origCommand = _command;

            // ensure enough arguments (at least 1)
            if (origCommand == null || origCommand.size() == 0)
                throw new RuntimeException("No launch arguments were provided.");
            
            // do a quick scan for any nulls or non-strings to avoid a mystifying exception message 
            // when Java Process builder fails
            for (int a = 0; a < origCommand.size(); a++) {
                Object cmd = origCommand.get(a);
                if (cmd == null || !(cmd instanceof CharSequence))
                    throw new IllegalArgumentException("Argument in position " + a + " of the list is missing or not a string");
            }
            
            List<String> command; // the command list that will be used
            
            // if on Windows, use the ProcessSandbox.exe utility if it can be found...
            File processSandboxFile = resolveProcessSandbox(_parentNode, origCommand);

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

            ProcessBuilder processBuilder = new ProcessBuilder(command);

            // set the working directory if it's specified, or to the node's root
            if (!Strings.isBlank(workingStr)) {
                File workingDir = new File(workingStr);
                if (!workingDir.exists() || !workingDir.isDirectory())
                    throw new FileNotFoundException("Working directory specifed does exist or is not a directory.");
                
                processBuilder.directory(workingDir);
                
            } else {
                // (node's root)
                processBuilder.directory(_parentNode.getRoot());
            }
            
            // should merge stderr with stdout?
            if (_mergeError) {
                processBuilder.redirectErrorStream(_mergeError);
            }
            
            if (_env != null)
                processBuilder.environment().putAll(_env);
            
            process = processBuilder.start();
            
            _counterLaunches.incr();
            
            // 'inject' countable stream
            OutputStream stdin = process.getOutputStream();
            os = new CountableOutputStream(stdin, SharableMeasurementProvider.Null.INSTANCE, _counterStdinRate);
            
            synchronized (_lock) {
                if (_shutdown)
                    return;
                    
                _process = process;
                _outputStream = os;
                
                // connection has been successful so reset variables
                // related to safe backing

                _gracefulStart = true;
            }
            
            // fire the started event
            _callbackHandler.handle(startedCallback, _callbackErrorHandler);
            
            // start reading
            if (_mode == Modes.CharacterDelimitedText) {
                readTextLoop(process);

            } else { // mode is 'UnboundedRaw'
                readUnboundedRawLoop(process);
            }

            // (any unexpected exceptions will be propagated to caller...)

        } catch (Exception exc) {
            // indicate error only on unusual termination
            if (_state == State.Started)
                Handler.tryHandle(_callbackErrorHandler, exc);

            // fire the STOPPED handler if it had fully started previously
            if (os != null)
                // .waitFor() is required instead of .exitValue() because it is possible (but rare) to get here
                // with the process not fully dead and cleaned up yet
                Handler.tryHandle(_stoppedCallback, process.waitFor(), _callbackErrorHandler);

            // propagate exception only on unusual termination
            if (_state == State.Started)
                throw exc;

        } finally {
            // always gracefully cleanup the process and invalidate related fields
            synchronized(_lock) {
                _process = null;
                _outputStream = null;
            }
            
            safeClose(process);
        }
    }

    /**
     * (convenience instance method for Windows environment)
     * 
     * Returns null if could not locate the process sandbox otherwise
     * attempts to resolves it using some rules.
     * 1) next to the EXE itself
     * 2) within the node root folder
     * 3) within the host root folder
     * 
     * (origCommand list will have at least one item)
     */
    public static File resolveProcessSandbox(BaseNode parentNode, List<String> origCommand) {
        File result;

        // ... next to the executable itself (which might be missing path info)

        // can the EXE be located?
        File processExe = new File(origCommand.get(0));
        if (processExe.exists()) {
            result = new File(processExe.getParent(), PROCESS_SANDBOX);
            if (result.exists())
                return result;
        }

        // ... in the node root?
        result = new File(parentNode.getRoot(), PROCESS_SANDBOX);
        if (result.exists())
            return result;

        // ... in the host root?
        result = new File(Nodel.getHostPath(), PROCESS_SANDBOX);
        if (result.exists())
            return result;
        
        // ... or try get on-the-fly compiled version
        result = ProcessSandboxExecutable.instance().tryGetPath();
        if (result != null && result.exists())
            return result;

        // ...else
        return null;
    }

    /**
     * The reading loop will continually read until an error occurs 
     * or the stream is gracefully ended by the peer.
     * 
     * ("resource" warning suppression applies to 'bis'. It's not valid because socket itself gets closed) 
     */
    @SuppressWarnings("resource")
    private void readTextLoop(Process process) throws Exception {
        InputStream in = process.getInputStream();
        BufferedInputStream bis = new BufferedInputStream(new CountableInputStream(in, SharableMeasurementProvider.Null.INSTANCE, _counterStdoutRate), 1024);

        // check if stderr needs to be dealt with i.e. 'merge error' not flagged AND a callback is set up
        if (!_mergeError) {
            InputStream stderr = process.getErrorStream();
            BufferedInputStream biserr = new BufferedInputStream(new CountableInputStream(stderr, SharableMeasurementProvider.Null.INSTANCE, _counterStderrRate), 1024);

            // this is ugly, but a new thread has to be started otherwise polling has to be done
            Thread thread = new Thread(new StderrHandler(biserr), _parentNode.getName().getReducedName() + "_stderr");
            thread.start();

            // (thread will gracefully stop after its associated process dies)
        }

        // create a buffer that'll be reused
        // start off small, will grow as needed
        BufferBuilder bb = new BufferBuilder(256);

        while (!_shutdown) {
            int c = bis.read();

            if (c < 0)
                break;

            if (charMatches((char) c, _receiveDelimiters)) {
                String str = bb.getTrimmedString();
                if (str != null)
                    handleReceivedData(str);

                bb.reset();
                
            } else {
                if (bb.getSize() >= MAX_SEGMENT_ALLOWED) {
                    // dump what's in the buffer and reset
                    Handler.tryHandle(_callbackErrorHandler, new IOException("Too much data arrived (at least " + bb.getSize() / 1024 + " KB) before any delimeter was present; dumping buffer and continuing."));
                    bb.reset();
                }

                bb.append((byte) c);
            }
        } // (while)
        
        // the peer has gracefully closed down the connection or we're shutting down
        
        if (!_shutdown) {
            // send out last data
            String str = bb.getTrimmedString();
            if (str != null)
                handleReceivedData(str);
            
            // then fire the stopped event and pass through the exit value
            Handler.tryHandle(_stoppedCallback, process.waitFor(), _callbackErrorHandler);
        }
    }
    
    /**
     * (thread handler)
     */
    private class StderrHandler implements Runnable {
        
        /**
         * (reference for this instance)
         */
        private InputStream __is;

        /**
         * (constructor)
         */
        public StderrHandler(InputStream is) {
            __is = is;
        }

        @Override
        public void run() {
            try {
                // (required once)
                _threadStateHandler.handle();
                
                // create a buffer that'll be reused
                // start off small, will grow as needed
                BufferBuilder bb = new BufferBuilder(256);

                while (!_shutdown) {
                    int c = __is.read();

                    if (c < 0)
                        break;

                    if (charMatches((char) c, _receiveDelimiters)) {
                        String str = bb.getTrimmedString();
                        if (str != null)
                            _callbackHandler.handle(_stderrCallback, str, _callbackErrorHandler);

                        bb.reset();
                        
                    } else {
                        if (bb.getSize() >= MAX_SEGMENT_ALLOWED) {
                            // dump what's in the buffer and reset
                            Handler.tryHandle(_callbackErrorHandler, new IOException("STDERR: Too much data arrived (at least " + bb.getSize() / 1024 + " KB) before any delimeter was present; dumping buffer and continuing."));
                            bb.reset();
                        }

                        bb.append((byte) c);
                    }
                } // (while)
            } catch (Exception exc) {
                // gracefully cleanup and end
                Stream.safeClose(__is);
            }
        }

    } 
    
    /**
     * No read-delimiters specified, so fire events as data segments arrive.
     * (suppress was used with resource-free CountableInputStream)
     */
    private void readUnboundedRawLoop(Process process) throws Exception {
        InputStream stdout = process.getInputStream();
        
        @SuppressWarnings("resource")
        CountableInputStream cis = new CountableInputStream(stdout, SharableMeasurementProvider.Null.INSTANCE, _counterStdoutRate);
        
        // create a buffer that'll be reused
        if (_buffer == null)
            _buffer = new byte[1024 * 10 * 2];

        while (!_shutdown) {
            int bytesRead = cis.read(_buffer);
            
            if (bytesRead <= 0)
                break;
            
            String segment = bufferToString(_buffer, 0, bytesRead);
            
            // fire the handler
            handleReceivedData(segment);
        }
        
        // the peer has gracefully closed down the connection or we're shutting down
        
        if (!_shutdown) {
            // then fire the stopped callback
            Handler.tryHandle(_stoppedCallback, process.waitFor(), _callbackErrorHandler);
        }        
    }    

    /**
     * (convenience method to check when a character appears in a list of characters (in the form of a String) 
     */
    private static boolean charMatches(char c, String chars) {
        int len = chars.length();
        for (int a = 0; a < len; a++)
            if (chars.charAt(a) == c)
                return true;

        return false;
    }
    
    /**
     * When an actual data segment was received; deals with request callbacks if necessary
     */
    private void handleReceivedData(String data) {
        // deal with any queued callbacks first
        
        QueuedRequest request = null;
        
        // then check for any requests
        // (should release lock as soon as possible)
        synchronized (_lock) {
            if (_activeRequest != null) {
                request = _activeRequest;
                _activeRequest = null;
            }
        }

        if (request != null && request.timeout > 0) {
            // make sure it hasn't been too long i.e. timeout
            if (request.isExpired()) {
                _logger.debug("Active request has expired");
                
                // fire the timeout handler
                _callbackHandler.handle(_timeoutCallback, _callbackErrorHandler);
            } else {
                // fire the response request's response handler
                request.setResponse(data);
                
                _callbackHandler.handle(request.responseHandler, data, _callbackErrorHandler);
            }            
        }
        
        // ...then fire the 'received' callback next
        _callbackHandler.handle(_stdoutCallback, data, _callbackErrorHandler);
        
        processQueue();
    }
    
    private class QueuedRequest {
        
        /**
         * The buffer that's already been validated i.e. not empty and UTF8 encoded
         * (or null if send data wasn't used in the first place)
         */
        public byte[] requestBuffer;
        
        /**
         * The original request/send data (only need 'sent' callback)
         * (can be null)
         */
        public String request;
        
        /**
         * For long term expiry detection.
         * 
         * (based on 'nanoTime')
         */
        public long timeQueued = System.nanoTime();
        
        /**
         * (millis)
         */
        public int timeout;
        
        /**
         * (based on System.nanoTime)
         */
        public long timeStarted;
        
        /**
         * The (optional) callback 
         */
        public H1<String> responseHandler;
        
        /**
         * Stores the response itself (for synchronous operation)
         */
        public String response;
        
        public QueuedRequest(byte[] requestBuffer, String origData, int timeout, H1<String> responseHandler) {
            this.requestBuffer = requestBuffer;
            this.request = origData;
            this.timeout = timeout;
            this.responseHandler = responseHandler;
        }
        
        /**
         * Sets response value and fire any callbacks
         */
        public void setResponse(String data) {
            synchronized (this) {
                this.response = data;

                // (for synchronous responses)
                this.notify();
            }
        }

        /**
         * Starts the timeout timer.
         */
        public void startTimeout() {
            this.timeStarted = System.nanoTime();
        }
        
        /**
         * Checks whether request has expired.
         */
        public boolean isExpired() {
            long timeDiff = (System.nanoTime() - this.timeStarted) / 1000000L;
            return timeDiff > this.timeout;
        }
        
        /**
         * For detecting long term delayed requests. 
         */
        public boolean isLongTermExpired() {
            long timeDiff = (System.nanoTime() - this.timeQueued) / 1000000L;
            return timeDiff > _longTermRequestTimeout;
        }
    }
    
    /**
     * For complete control of a request
     */
    public void queueRequest(String requestData, int timeout, H1<String> responseHandler) {
        byte[] buffer = prepareBuffer(requestData);
        
        // buffer can be null, which means a 'send' is not necessary, but a response is
        
        QueuedRequest request = new QueuedRequest(buffer, requestData, timeout, responseHandler);
        
        doQueueRequest(request);
    }

    public void doQueueRequest(QueuedRequest request) {
        // whether or not this entry had to be queued
        boolean queued = false; 
        
        synchronized (_lock) {
            if (_activeRequest == null) {
                _logger.debug("Active request made. data:[{}]", request.request);
                
                // make it the active request and don't queue it
                _activeRequest = request;
                
                // will be sent next, so start timing
                _activeRequest.startTimeout();
                
            } else {
                _logger.debug("Queued a request. data:[{}]", request.request);
                
                // a request is active, so queue this new one
                _requestQueue.add(request);
                _queueLength++;
                queued = true;
            }
        }
        
        if (!queued && request.requestBuffer != null) {
            sendBufferNow(request.requestBuffer, request.request, true);
        }
        
        // without a timer, the queue needs to serviced on both send and receive
        processQueue();
    }
    
    /**
     * (Overloaded) (uses default timeout value)
     */
    public void request(String requestData, H1<String> responseHandler) {
        // don't bother doing anything if empty or missing
        if (Strings.isEmpty(requestData))
            return;
        
        queueRequest(requestData, _requestTimeout, responseHandler);
    }

    /**
     * (synchronous version)
     */
    public String requestWaitAndReceive(String requestData) {
        int recvTimeout = getRequestTimeout();

        final String[] response = new String[1];

        synchronized (response) {
            queueRequest(requestData, recvTimeout, new H1<String>() {

                @Override
                public void handle(String value) {
                    synchronized (response) {
                        response[0] = value;

                        response.notify();
                    }
                }

            });

            // wait for a while or until notified when a response is received
            Threads.waitOnSync(response, recvTimeout);
        }

        return response[0];
    }

    /**
     * Receives data and invokes a callback
     */
    public void receive(H1<String> responseHandler) {
        queueRequest(null, _requestTimeout, responseHandler);
    }
    
    /**
     * Synchronous version.
     */
    public String waitAndReceive() {
        int recvTimeout = getRequestTimeout();
        QueuedRequest request = new QueuedRequest(null, null, recvTimeout, null);
        
        synchronized(request) {
            queueRequest(null, _requestTimeout, null);
            
            // wait for a while or until notified when a response is received
            Threads.waitOnSync(request, recvTimeout);
        }
        
        return request.response;
    }
    
    /**
     * Clears the active request and any queue requests.
     */
    public void clearQueue() {
        synchronized (_lock) {
            boolean activeRequestCleared = false;
            
            // clear the active request
            if (_activeRequest != null) {
                _activeRequest = null;
                activeRequestCleared = true;
            }

            int count = 0;

            // clear the queue
            while (_requestQueue.poll() != null) {
                count++;
                _queueLength--;
            }
            
            _logger.debug("Cleared queue. activeRequest={}, queueCount={}", activeRequestCleared, count);
        }
    }
    
    /**
     * Expires and initiates requests in the queue
     * (assumes not synced)
     */
    private void processQueue() {
        // if any new requests are found
        QueuedRequest nextRequest = null;
        
        // if a timeout callback needs to be fired
        boolean callTimeout = false;

        synchronized (_lock) {
            // check if any active requests need expiring
            if (_activeRequest != null) {
                if (_activeRequest.responseHandler == null || _activeRequest.timeout <= 0) {
                    // was a blind request or 
                    _activeRequest = null;
                    
                } else if (_activeRequest.isExpired()) {
                    _logger.debug("Active request has expired");
                    
                    // timeout callback must be fired
                    callTimeout = true;

                    // clear active request
                    _activeRequest = null;
                    
                } else if (_activeRequest.isLongTermExpired()) {
                    _logger.debug("Active request has long term expired");
                    
                    callTimeout = true;
                    
                    _activeRequest = null;
                } else {
                    // there is still an valid active request,
                    // so just get out of here
                    return;
                }
            }
        }

        // call the timeout callback, (there's an opportunity for request queue to be cleared by callback handler)
        if (callTimeout)
            _callbackHandler.handle(_timeoutCallback, _callbackErrorHandler);

        // an active request might have come in
        synchronized (_lock) {
            if (_activeRequest != null) {
                // there's an active request, so leave it to play out
                return;
            }
            // record this for logging
            int longTermDropped = 0;

            try {
                for (;;) {
                    // no active request, check for queued ones
                    nextRequest = _requestQueue.poll();

                    if (nextRequest == null) {
                        // no more requests either, so nothing more to do
                        _logger.debug("No new requests in queue.");

                        return;
                    }

                    _queueLength--;

                    if (!nextRequest.isLongTermExpired())
                        break;

                    // otherwise, continue to expire the long term queued
                    longTermDropped++;
                }
            } finally {
                if (longTermDropped > 0)
                    _logger.debug("(dropped {} long term queued requests.)", longTermDropped);
            }
            
            // set active request and start timeout *before* sending
            _activeRequest = nextRequest;
            nextRequest.startTimeout();
        }

        // if the request has send data 'data' send now
        if (nextRequest.requestBuffer != null)
            sendBufferNow(nextRequest.requestBuffer, nextRequest.request, false);
    }
    
    /**
     * Safely sends data without overlapping any existing requests 
     */
    public void send(String data) {
        byte[] buffer = prepareBuffer(data);

        QueuedRequest request = new QueuedRequest(buffer, data, 0, null);

        doQueueRequest(request);
    }
    
    /**
     * Prepares a buffer for sending, null if it's not sendable.
     */
    private byte[] prepareBuffer(String data) {
        if (Strings.isEmpty(data))
            return null;
        
        // (data will be at least 1 character in length)
        
        byte[] buffer = null;

        // append the send delimiter(s) if not already present
        int delimsCount = _sendDelimiters.length();

        // go through the send delimiters
        for (int a = 0; a < delimsCount; a++) {
            // compare last characters
            if (data.charAt(data.length() - 1) == _sendDelimiters.charAt(a)) {
                buffer = stringToBuffer(data, null);
                break;
            }
        }

        // has no existing delimiters, so append them
        if (buffer == null)
            buffer = stringToBuffer(data, _sendDelimiters);

        return buffer;
    }
    
    /**
     * Sends data. Returns immediately. Will not throw any exceptions.
     */
    public void sendNow(final String data) {
        byte[] buffer = prepareBuffer(data);

        if (buffer == null)
            return;
        
        sendBufferNow(buffer, data, true);
    }
    
    /**
     * Sends a prepared buffer immediately, optionally using a thread-pool
     */
    private void sendBufferNow(final byte[] buffer, final String origData, boolean onThreadPool) {
        final OutputStream os;
        
        synchronized(_lock) {
            os = _outputStream;
        }        
        
        if (os != null) {
            if (onThreadPool) {
                _threadPool.execute(new Runnable() {

                    @Override
                    public void run() {
                        _threadStateHandler.handle();
                        sendBufferNow0(os, buffer, origData);
                    }

                });
            } else {
                sendBufferNow0(os, buffer, origData);
            }
        }
    }

    /**
     * (convenience method)
     */
    private void sendBufferNow0(OutputStream os, byte[] buffer, String origData) {
        try {
            os.write(buffer);
            os.flush();
        } catch (Exception exc) {
            // ignore
        }

        Handler.tryHandle(_stdinCallback, origData, _callbackErrorHandler);
    }

    /**
     * Converts a direct char-to-byte conversion, and includes a suffix 
     */
    private static byte[] stringToBuffer(String str, String suffix) {
        int strLen = (str != null ? str.length() : 0);
        int suffixLen = (suffix != null ? suffix.length() : 0);

        byte[] buffer = new byte[strLen + suffixLen];
        for (int a = 0; a < strLen; a++)
            buffer[a] = (byte) (str.charAt(a) & 0xff);

        for (int a = 0; a < suffixLen; a++)
            buffer[strLen + a] = (byte) (suffix.charAt(a) & 0xff);

        return buffer;
    }
    
    /**
     * Raw buffer to string.
     */
    private String bufferToString(byte[] buffer, int offset, int len) {
        char[] cBuffer = new char[len];
        for (int a=0; a<len; a++) {
            cBuffer[a] = (char) (buffer[offset + a] & 0xff);
        }
        
        return new String(cBuffer);
    }
    
    
    /**
     * Drops this socket; may trigger a reconnect.
     */
    public void drop() {
        synchronized (_lock) {
            if (_shutdown)
                return;

            safeClose(_process);
        }
    }
    
    /**
     * Permanently shuts down this managed process
     */
    @Override
    public void close() {
        synchronized(_lock) {
            if (_shutdown)
                return;
            
            _shutdown = true;
            
            _outputStream = null;
            
            if (_startTimer != null)
                _startTimer.cancel();

            safeClose(_process);

            _process = null;
            
            // notify the connection and receive thread if it happens to be waiting
            _lock.notify();
        }
    }
    
    /**
     * (convenience method)
     */
    private static void safeClose(Process process) {
        if (process == null)
            return;

        try {
            process.destroy();
        } catch (Exception exc) {
            // ignore
        }
    }
    
}
