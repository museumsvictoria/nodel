package org.nodel.toolkit;

import org.nodel.Handler;
import org.nodel.Handler.H0;
import org.nodel.Handler.H1;
import org.nodel.Strings;
import org.nodel.Threads;
import org.nodel.diagnostics.CountableInputStream;
import org.nodel.diagnostics.CountableOutputStream;
import org.nodel.diagnostics.Diagnostics;
import org.nodel.diagnostics.SharableMeasurementProvider;
import org.nodel.host.BaseNode;
import org.nodel.io.BufferBuilder;
import org.nodel.threading.CallbackQueue;
import org.nodel.threading.ThreadPool;
import org.nodel.threading.TimerTask;
import org.nodel.threading.Timers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

import com.jcraft.jsch.*;


public class ManagedSSH implements Closeable {

    private static final AtomicLong s_instanceCounter = new AtomicLong();

    /**
     * (used by 'logger' and thread name)
     */
    private final long _instance = s_instanceCounter.getAndIncrement();

    /**
     * (logging related)
     */
    private final Logger _logger = LoggerFactory.getLogger(String.format("%s.instance%d", this.getClass().getName(), _instance));

    /**
     * The kick-off delay (randomized)
     */
    private final static int KICKOFF_DELAY = 5000;

    /**
     * To randomise the kick-off delay
     */
    private final static Random s_random = new Random();

    /**
     * The maximum data to be received (default 1 MB)
     */
    private final static int MAX_SEGMENT_ALLOWED = 2 * 1024 * 1024;

    /**
     * The minimum gap between connections (default 500ms)
     * A minimum gap is used to achieve greater reliability when connecting to  hosts
     * that may be TCPIP stack challenged (think older devices, projectors, etc.)
     */
    private final static int MIN_CONNECTION_GAP = 500;

    /**
     * The maximum back-off time allowed (default 32 secs or 2^5 millis)
     */
    private final static int MAX_BACKOFF = 32000;

    /**
     * The connection or TCP read timeout (default 5 mins)
     */
    private static final int RECV_TIMEOUT = 5 * 60000;

    /**
     * The amount of time given to connect to a socket.
     */
    private static final int CONNECT_TIMEOUT = 30000;

    /**
     * SSH client
     */
    private JSch _jsch;

    /**
     * (synchronisation / locking)
     */
    private final Object _lock = new Object();

    /**
     * The shared thread-pool
     */
    private final ThreadPool _threadPool;

    /**
     * The safe queue as provided by a host
     */
    private final CallbackQueue _callbackHandler;

    /**
     * The current back-off period (exponential back-off)
     */
    private int _backoffTime = 0;

    /**
     * If there was a recent successful connection.
     */
    private boolean _recentlyConnected = false;

    /**
     * (see setter)
     */
    private H0 _connectedCallback;

    /**
     * (see setter)
     */
    private H0 _disconnectedCallback;

    /**
     * (see setter)
     */
    private H1<String> _executedCallback;

    /**
     * (see setter)
     */
    private H1<String> _shellConsoleOutputCallback;

    /**
     * (see setter)
     */
    private H1<String> _receivedCallback;

    /**
     * (see setter)
     */
    private H0 _timeoutCallback;

    /**
     * When errors occur during callbacks.
     */
    private final H1<Exception> _callbackErrorHandler;

    /**
     * Permanently shut down?
     */
    private boolean _shutdown;

    /**
     * Has started?
     */
    private boolean _started;

    /**
     * worker thread to monitor session connectivity (only for SHELL mode)
     */
    private Thread _thread;

    /**
     * Shared timer framework to use.
     */
    private final Timers _timerThread;

    /**
     * The start timer.
     */
    private TimerTask _startTimer;

    /**
     * Holds the full socket address (addr:port)
     * (may be null)
     */
    private String _dest;

    /**
     * The delimiters to split the receive data on.
     */
    private String _receiveDelimiters = "\r\n";

    /**
     * The delimiters to split the send data on.
     */
    private String _sendDelimiters = "\n";

    /**
     * known hosts which will be used with ssh connection
     */
    private final String _knownHosts;

    /**
     * Holds username
     */
    private final String _username;

    /**
     * Holds password
     */
    private final String _password;

    /**
     * (diagnostics)
     */
    private final SharableMeasurementProvider _counterConnections;

    /**
     * (diagnostics)
     */
    private final SharableMeasurementProvider _counterRecvOps;

    /**
     * (diagnostics)
     */
    private final SharableMeasurementProvider _counterRecvRate;

    /**
     * (diagnostics)
     */
    private final SharableMeasurementProvider _counterSendOps;

    /**
     * (diagnostics)
     */
    private final SharableMeasurementProvider _counterSendRate;

    /**
     * The command queue
     */
    private final ConcurrentLinkedQueue<QueuedCommand> _commandQueue = new ConcurrentLinkedQueue<>();

    /**
     * Holds the queue length ('ConcurrentLinkedQueue' not designed to handle 'size()' efficiently)
     */
    private int _queueLength = 0;

    /**
     * The active command
     */
    private QueuedCommand _activeCommand;

    /**
     * Gets initialised once, during connection loop and then never again.
     */
    private byte[] _buffer;

    /**
     * SSH mode : Exec or Shell
     */
    private final SSHMode _sshMode;

    /**
     * The default request timeout value (timed from respective 'send')
     */
    private int _requestTimeout = 10000;

    /**
     * The command timeout value (timed from respective 'queue')
     */
    private final int _longTermRequestTimeout = 60000;

    /**
     * The last time a successful connection occurred (connection or data receive)
     * (nano time)
     */
    private long _lastSuccessfulConnection = System.nanoTime();

    /**
     * (Response for handling thread-state)
     */
    private final H0 _threadStateHandler;

    /**
     * The connection and receive timeout.
     */
    private int _timeout = RECV_TIMEOUT;

    /**
     * SSH : Session
     */
    private Session _session;

    /**
     * SSH : Channel
     */
    private Channel _channel;

    /**
     * Parameters for reverse forwarding
     * e.g.>
     * {
     * "bind_address": "abcd",
     * "rport": 80,
     * "host": "localhost",
     * "lport": 8086,
     * }
     */
    private Map<String, Object> _reverseForwardingParameters;

    /**
     * enable/disable Echo
     */
    private boolean _enableEcho;

    /**
     * array to set SSH terminal mode
     * e.g.> OPCODE(1byte) + value(4bytes) + END(1byte) ...
     */
    private byte[] _terminalMode;

    /**
     *
     */
    private InputStreamHandleMode _inputStreamHandleMode = InputStreamHandleMode.CharacterDelimitedText;

    public enum SSHMode {
        EXEC("exec"),
        SHELL("shell");

        private final String name;

        SSHMode(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    /**
     * (constructor)
     */
    public ManagedSSH(
            SSHMode sshMode,
            BaseNode node,
            String dest,
            String knownHosts,
            String username,
            String password,
            H0 threadStateHandler,
            H1<Exception> callbackExceptionHandler,
            CallbackQueue callbackQueue,
            ThreadPool threadPool,
            Timers timers) {
        _sshMode = sshMode;
        _dest = dest;
        _knownHosts = knownHosts;
        _username = username;
        _password = password;

        _threadStateHandler = threadStateHandler;
        _callbackErrorHandler = callbackExceptionHandler;
        _callbackHandler = callbackQueue;
        _threadPool = threadPool;
        _timerThread = timers;

        _jsch = new JSch();

        if (_sshMode.equals(SSHMode.SHELL)) {
            _thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    begin();
                }
            });
            _thread.setName(node.getName().getReducedName() + "_sshConnectAndReceive_" + _instance);
            _thread.setDaemon(true);
        }

        // register the counters
        String counterName = "'" + node.getName().getReducedName() + "'";
        _counterConnections = Diagnostics.shared().registerSharableCounter(counterName + ".SSH connects", true);
        _counterRecvOps = Diagnostics.shared().registerSharableCounter(counterName + ".SSH receives", true);
        _counterRecvRate = Diagnostics.shared().registerSharableCounter(counterName + ".SSH receive rate", true);
        _counterSendOps = Diagnostics.shared().registerSharableCounter(counterName + ".SSH sends", true);
        _counterSendRate = Diagnostics.shared().registerSharableCounter(counterName + ".SSH send rate", true);
    }

    /**
     * Parameters for reverse port forwarding.
     *
     * @param params, Map<String, Object>
     */
    public void setReverseForwardingParameters(Map<String, Object> params) {
        _reverseForwardingParameters = params;
    }

    /**
     * When a connection moves into a connected state.
     */
    public void setConnectedHandler(H0 handler) {
        _connectedCallback = handler;
    }

    /**
     * When the connected moves into a disconnected state.
     */
    public void setDisconnectedHandler(H0 handler) {
        _disconnectedCallback = handler;
    }

    /**
     * When a command is executed
     */
    public void setExecutedHandler(H1<String> handler) {
        _executedCallback = handler;
    }

    /**
     * When a shell input stream is available, console output will be sent with this callback.
     */
    public void setShellConsoleOutputHandler(H1<String> handler) {
        _shellConsoleOutputCallback = handler;
    }

    /**
     * When a data segment arrives.
     */
    public void setReceivedHandler(H1<String> handler) {
        _receivedCallback = handler;
    }

    /**
     * When a command timeout occurs.
     */
    public void setTimeoutHandler(H0 handler) {
        _timeoutCallback = handler;
    }

    /**
     * Sets the destination.
     */
    public void setDest(String dest) {
        synchronized (_lock) {
            _dest = dest;
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
    public void setReceiveDelimiters(String delims) {
        synchronized (_lock) {
            if (delims == null)
                _receiveDelimiters = "";
            else
                _receiveDelimiters = delims;

            if (Strings.isEmpty(_receiveDelimiters))
                _inputStreamHandleMode = InputStreamHandleMode.UnboundedRaw;
        }
    }

    public void setEchoEnable(boolean flag) {
        _enableEcho = flag;
        doUpdateTerminalMode();
    }

    private void doUpdateTerminalMode() {
        // https://tools.ietf.org/html/rfc4254
        _terminalMode = new byte[]{
                53, // ECHO
                0, 0, 0, (byte) (_enableEcho ? 1 : 0), // 0: OFF, 1: ON
                0 // TTY_OP_END
        };
    }

    /**
     * Sets the connection and receive timeout.
     */
    public void setTimeout(int value) {
        synchronized (_lock) {
            _timeout = value;
        }
    }

    /**
     * Gets the connection and receive timeout.
     */
    public int getTimeout() {
        return _timeout;
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
     * initialize SHELL mode
     */
    private void doInitializeShellMode() throws Exception {
        try {
            if (!Strings.isEmpty(_knownHosts)) {
                InputStream isKnowsHosts = new ByteArrayInputStream(_knownHosts.getBytes());
                _jsch.setKnownHosts(isKnowsHosts);
            }

            InetSocketAddress serverAddress = parseAndResolveDestination(_dest);
            _session = _jsch.getSession(_username, serverAddress.getAddress().getHostAddress(), serverAddress.getPort());

            if (!Strings.isEmpty(_password)) {
                _session.setPassword(_password);
            }

            java.util.Properties config = new java.util.Properties();
            config.put("StrictHostKeyChecking", "no");
            _session.setConfig(config);
            _session.setServerAliveInterval(5000);
            _session.connect(); // do not use timeout. Because the first message can be lost.

            _channel = _session.openChannel("shell");
            ((ChannelShell) _channel).setTerminalMode(_terminalMode);
            _channel.connect(); // do not use timeout. Because the first message can be lost.

            if (_reverseForwardingParameters != null) {
                // Supports 2 methods below
                // 1) public void setPortForwardingR(int rport, String host, int lport)
                // 2) public void setPortForwardingR(String bind_address, int rport, String host, int lport)

                String bind_address = (String) _reverseForwardingParameters.get("bind_address"); // optional
                int rport = (Integer) _reverseForwardingParameters.get("rport"); // required
                String host = (String) _reverseForwardingParameters.get("host"); // required
                int lport = (Integer) _reverseForwardingParameters.get("lport"); // required

                if (!Strings.isEmpty(bind_address)) {
                    _session.setPortForwardingR(bind_address, rport, host, lport);
                } else {
                    _session.setPortForwardingR(rport, host, lport);
                }
            }

            // fire the connected event
            _callbackHandler.handle(_connectedCallback, _callbackErrorHandler);

        } catch (Exception ex) {
            _logger.debug("[doInitializeShellMode] Exception", ex);
            throw ex;
        }
    }

    public void start() {
        synchronized (_lock) {
            if (_shutdown || _started)
                return;

            _started = true;

            if (_sshMode.equals(SSHMode.EXEC)) {
                try {
                    if (!Strings.isEmpty(_knownHosts)) {
                        InputStream isKnowsHosts = new ByteArrayInputStream(_knownHosts.getBytes());
                        _jsch.setKnownHosts(isKnowsHosts);
                    }
                } catch (Exception ex) {
                    _logger.debug("[start] Exception", ex);
                }
            } else {
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
     * The main thread.
     */
    private void begin() {
        while (!_shutdown) {
            // only need to set the thread state once here
            _threadStateHandler.handle();

            try {
                connectAndRead();

            } catch (Exception exc) {
                if (_shutdown) {
                    // thread can gracefully exit
                    return;
                }

                if (_recentlyConnected)
                    _backoffTime = MIN_CONNECTION_GAP;

                else {
                    _backoffTime = Math.min(_backoffTime * 2, MAX_BACKOFF);
                    _backoffTime = Math.max(MIN_CONNECTION_GAP, _backoffTime);
                }

                long timeDiff = (System.nanoTime() - _lastSuccessfulConnection) / 1000000;
                if (timeDiff > _timeout) {
                    // reset the timestamp
                    _lastSuccessfulConnection = System.nanoTime();

                    _callbackHandler.handle(_timeoutCallback, _callbackErrorHandler);
                }

                _recentlyConnected = false;
            }

            Threads.wait(_lock, _backoffTime);
        } // (while)
    }

    /**
     * Establishes session/channel and continually reads.
     */
    private void connectAndRead() throws Exception {
        try {

            // Create session/channel and connect.
            doInitializeShellMode();

            _counterConnections.incr();

            // update flag
            _lastSuccessfulConnection = System.nanoTime();

            synchronized (_lock) {
                if (_shutdown)
                    return;

                // connection has been successful so reset variables
                // related to exponential back-off

                _recentlyConnected = true;

                _backoffTime = MIN_CONNECTION_GAP;
            }

            // start reading
            if (_inputStreamHandleMode == InputStreamHandleMode.CharacterDelimitedText) {
                readTextLoop(_channel);
            } else { // mode is 'UnboundedRaw'
                readUnboundedRawLoop(_channel);
            }

            // (any non-timeout exceptions will be propagated to caller...)

        } finally {
            // always gracefully close the session/channel and invalidate related fields

            synchronized (_lock) {
                doCloseChannel();
            }
        }
    }

    /**
     * The reading loop will continually read until an error occurs
     * or the stream is gracefully ended by the peer.
     * <p>
     * ("resource" warning suppression applies to 'bis'. It's not valid because socket itself gets closed)
     */
    @SuppressWarnings("resource")
    private void readTextLoop(Channel channel) throws Exception {
        InputStream in = channel.getInputStream();
        BufferedInputStream bis = new BufferedInputStream(new CountableInputStream(in, _counterRecvOps, _counterRecvRate), 1024);

        // create a buffer that'll be reused
        // start off small, will grow as needed
        BufferBuilder bb = new BufferBuilder(256);

        while (!_shutdown) {
            int c = bis.read();

            if (c < 0)
                break;

            if (charMatches((char) c, _receiveDelimiters)) {
                String str = bb.getTrimmedString();
                if (str != null) {
                    handleReceivedData(str);
                    _callbackHandler.handle(_shellConsoleOutputCallback, str, _callbackErrorHandler);
                }

                bb.reset();

            } else {
                if (bb.getSize() >= MAX_SEGMENT_ALLOWED) {
                    // drop the connection
                    throw new IOException("Too much data arrived (at least " + bb.getSize() / 1024 + " KB) before any delimeter was present; dropping connection.");
                }

                bb.append((byte) c);
            }
        } // (while)

        // the peer has gracefully closed down the connection or we're shutting down

        if (!_shutdown) {
            // send out last data
            String str = bb.getTrimmedString();
            if (str != null) {
                handleReceivedData(str);
                _callbackHandler.handle(_shellConsoleOutputCallback, str, _callbackErrorHandler);
            }

            // then fire the disconnected callback
            Handler.tryHandle(_disconnectedCallback, _callbackErrorHandler);
        }
    }

    /**
     * No read-delimiters specified, so fire events as data segments arrive.
     */
    @SuppressWarnings("resource")
    private void readUnboundedRawLoop(Channel channel) throws IOException {
        CountableInputStream cis = new CountableInputStream(channel.getInputStream(), _counterRecvOps, _counterRecvRate);

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

            _callbackHandler.handle(_shellConsoleOutputCallback, segment, _callbackErrorHandler);
        }

        // the peer has gracefully closed down the connection or we're shutting down

        if (!_shutdown) {
            // then fire the disconnected callback
            Handler.tryHandle(_disconnectedCallback, _callbackErrorHandler);
        }
    }

    /**
     * (convenience method to check when a character appears in a list of characters (in the form of a String)
     * imported from ManagedTCP
     */
    private static boolean charMatches(char c, String chars) {
        int len = chars.length();
        for (int a = 0; a < len; a++)
            if (chars.charAt(a) == c)
                return true;

        return false;
    }

    /**
     * When an actual data segment was received; deals with command callbacks if necessary
     */
    private void handleReceivedData(String data) {

        // deal with any queued callbacks first

        QueuedCommand command = null;

        // then check for any requests
        // (should release lock as soon as possible)
        synchronized (_lock) {
            if (_activeCommand != null) {
                command = _activeCommand;
                _activeCommand = null;
            }
        }

        if (command != null && command.timeout > 0) {
            // make sure it hasn't been too long i.e. timeout
            if (command.isExpired()) {
                _logger.debug("Active command has expired");

                // fire the timeout handler
                _callbackHandler.handle(_timeoutCallback, _callbackErrorHandler);
            } else {
                // fire the response request's response handler
                command.setResponse(data);

                _callbackHandler.handle(command.responseHandler, data, _callbackErrorHandler);
            }
        }

        // ...then fire the 'received' callback next
        _callbackHandler.handle(_receivedCallback, data, _callbackErrorHandler);

        processQueue();
    }

    private class QueuedCommand {

        /**
         * The original command string
         * (can be null)
         */
        public String cmdString;

        /**
         * For long term expiry detection.
         * <p>
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
        public Handler.H1<String> responseHandler;

        /**
         * Stores the response itself (for synchronous operation)
         */
        public String response;

        public QueuedCommand(String cmdString, int timeout, Handler.H1<String> responseHandler) {
            this.cmdString = cmdString;
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
         * Checks whether command has expired.
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
     * Clears the active command and any queue commands.
     */
    public void clearQueue() {
        synchronized (_lock) {
            boolean activeCommandCleared = false;

            // clear the active command
            if (_activeCommand != null) {
                _activeCommand = null;
                activeCommandCleared = true;
            }

            int count = 0;

            // clear the queue
            while (_commandQueue.poll() != null) {
                count++;
                _queueLength--;
            }

            _logger.debug("Cleared queue. activeCommand={}, queueCount={}", activeCommandCleared, count);
        }
    }

    /**
     * Expires and initiates commands in the queue
     * (assumes not synced)
     */
    private void processQueue() {
        // if any new commands are found
        QueuedCommand nextCommand = null;

        // if a timeout callback needs to be fired
        boolean callTimeout = false;

        synchronized (_lock) {
            // check if any active commands need expiring
            if (_activeCommand != null) {
                if (_activeCommand.responseHandler == null || _activeCommand.timeout <= 0) {
                    // was a blind command or
                    _activeCommand = null;

                } else if (_activeCommand.isExpired()) {
                    _logger.debug("Active command has expired");

                    // timeout callback must be fired
                    callTimeout = true;

                    // clear active command
                    _activeCommand = null;

                } else if (_activeCommand.isLongTermExpired()) {
                    _logger.debug("Active command has long term expired");

                    callTimeout = true;

                    _activeCommand = null;
                } else {
                    // there is still an valid active command,
                    // so just get out of here
                    return;
                }
            }
        }

        // call the timeout callback, (there's an opportunity for command queue to be cleared by callback handler)
        if (callTimeout) {
            _callbackHandler.handle(_timeoutCallback, _callbackErrorHandler);
        }

        // an active command might have come in
        synchronized (_lock) {
            if (_activeCommand != null) {
                // there's an active command, so leave it to play out
                return;
            }
            // record this for logging
            int longTermDropped = 0;

            try {
                for (; ; ) {
                    // no active command, check for queued ones
                    nextCommand = _commandQueue.poll();

                    if (nextCommand == null) {
                        // no more commands either, so nothing more to do
                        _logger.debug("No new commands in queue.");

                        return;
                    }

                    _queueLength--;

                    if (!nextCommand.isLongTermExpired())
                        break;

                    // otherwise, continue to expire the long term queued
                    longTermDropped++;
                }
            } finally {
                if (longTermDropped > 0)
                    _logger.debug("(dropped {} long term queued commands.)", longTermDropped);
            }

            // set active command and start timeout *before* sending
            _activeCommand = nextCommand;
            nextCommand.startTimeout();
        }

        // if the command has 'cmdString', execute now
        if (!Strings.isEmpty(nextCommand.cmdString)) {
            sendCommandNow(nextCommand.cmdString, false); // do not use thread pool
        }
    }

    /**
     * Safely sends command without overlapping any existing commands
     */
    public void send(String cmdString) throws Exception {
        send(cmdString, null);
    }

    public void send(String cmdString, Handler.H1<String> responseHandler) throws Exception {
        if (Strings.isEmpty(cmdString)) {
            return;
        }

        String commandString = prepareCommandString(cmdString);

        QueuedCommand command = new QueuedCommand(commandString, _requestTimeout, responseHandler);
        doQueueCommand(command);
    }

    public void doQueueCommand(QueuedCommand command) {
        // whether or not this entry had to be queued
        boolean queued = false;

        synchronized (_lock) {
            if (_activeCommand == null) {
                _logger.debug("Active command made. data:[{}]", command.cmdString);

                // make it the active command and don't queue it
                _activeCommand = command;

                // will be sent next, so start timing
                _activeCommand.startTimeout();

            } else {
                _logger.debug("Queued a command. data:[{}]", command.cmdString);

                // a command is active, so queue this new one
                _commandQueue.add(command);
                _queueLength++;
                queued = true;
            }
        }

        if (!queued && !Strings.isEmpty(command.cmdString)) {
            sendCommandNow(command.cmdString, false); // do not use thread pool
        }

        // without a timer, the queue needs to serviced on both send and receive
        processQueue();
    }

    /**
     * Prepares a command string for sending, null if it's not sendable.
     */
    private String prepareCommandString(String data) {
        if (Strings.isEmpty(data)) {
            return null;
        }

        // In 'exec' mode, no need to append sendDelimiters
        if (_sshMode.equals(SSHMode.EXEC)) {
            return data;
        }

        // (data will be at least 1 character in length)

        String commandString = null;

        // append the send delimiter(s) if not already present
        int delimsCount = _sendDelimiters.length();

        // go through the send delimiters
        for (int a = 0; a < delimsCount; a++) {
            // compare last characters
            if (data.charAt(data.length() - 1) == _sendDelimiters.charAt(a)) {
                commandString = data;
                break;
            }
        }

        // has no existing delimiters, so append them
        if (commandString == null) {
            commandString = data + _sendDelimiters;
        }

        return commandString;
    }

    /**
     * Executes command. Returns immediately. Will not throw any exceptions.
     */
    public void sendNow(final String cmdString) {
        if (Strings.isEmpty(cmdString)) {
            return;
        }

        String commandString = prepareCommandString(cmdString);

        sendCommandNow(commandString, false); // do not use thread pool
    }

    /**
     * Sends a command immediately, optionally using a thread-pool
     * In some cases, sequence of commands execution is broken. Please do not use thread pool.
     */
    private void sendCommandNow(final String cmdString, boolean onThreadPool) {
        try {
            if (onThreadPool) {
                _threadPool.execute(new Runnable() {

                    @Override
                    public void run() {
                        _threadStateHandler.handle();
                        if (_sshMode.equals(SSHMode.SHELL)) {
                            shellCommandNow0(cmdString);
                        } else {
                            execCommandNow0(cmdString);
                        }
                    }
                });
            } else {
                if (_sshMode.equals(SSHMode.SHELL)) {
                    shellCommandNow0(cmdString);
                } else {
                    execCommandNow0(cmdString);
                }
            }
        } catch (Exception ex) {

        } finally {
        }
    }

    /**********************************************************************************************************************************
     * EXEC MODE
     *********************************************************************************************************************************/

    private void handleExecModeInputStream(Channel channel) {
        try {
            InputStream in = channel.getInputStream();

            // create a buffer that'll be reused
            // start off small, will grow as needed
            BufferBuilder bb = new BufferBuilder(256);
            while (!_shutdown) {
                int c = in.read();
                if (c < 0)
                    break;

                if (bb.getSize() >= MAX_SEGMENT_ALLOWED) {
                    // drop the connection
                    throw new IOException("Too much data arrived (at least " + bb.getSize() / 1024 + " KB); dropping connection.");
                }
                bb.append((byte) c);
            } // (while)

            // send out last data
            String str = bb.getTrimmedString();
            if (str != null) {
                handleReceivedData(str);
                _callbackHandler.handle(_shellConsoleOutputCallback, str, _callbackErrorHandler);
            }
        } catch (IOException ex) {
            // ignore
        }
    }

    private void execCommandNow0(String cmdString) {
        Session session = null;
        Channel channel = null;
        try {
            // Exec Session should be created per a command and disconnected.
            InetSocketAddress serverAddress = parseAndResolveDestination(_dest);
            session = _jsch.getSession(_username, serverAddress.getAddress().getHostAddress(), serverAddress.getPort());
            session.setPassword(_password);
            java.util.Properties config = new java.util.Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.connect(); // do not use timeout. Because the first message can be lost.

            channel = session.openChannel("exec");
            ((ChannelExec) channel).setCommand(cmdString);
            channel.setInputStream(null);
            channel.setOutputStream(System.out);

            ((ChannelExec) channel).setTerminalMode(_terminalMode);

            channel.connect(); // do not use timeout. Because the first message can be lost.

            Handler.tryHandle(_connectedCallback, _callbackErrorHandler);

            handleExecModeInputStream(channel);

            _callbackHandler.handle(_executedCallback, cmdString, _callbackErrorHandler);

            _counterConnections.incr();

        } catch (Exception ex) {
            _logger.debug("[execCommandNow0] Exception", ex);
            throw new RuntimeException(ex.getMessage());
        } finally {
            try {
                if (channel != null && channel.isConnected()) {
                    channel.disconnect();
                }
                if (session != null && session.isConnected()) {
                    session.disconnect();
                }
                Handler.tryHandle(_disconnectedCallback, _callbackErrorHandler);
            } catch (Exception ex) {
                _logger.debug("[execCommandNow0::finally] Exception", ex);
            }
        }
    }

    /**********************************************************************************************************************************
     * SHELL MODE
     *********************************************************************************************************************************/

    private Channel getChannelShell() {
        if (_session == null || !_session.isConnected()) {
            _logger.debug("[getChannelShell] Session disconnected");
            synchronized (_lock) {
                doCloseChannel();
            }
            throw new RuntimeException("Session disconnected");
        }
        return _channel;
    }

    private void shellCommandNow0(String cmdString) {
        try {
            Channel channel = this.getChannelShell();
            OutputStream os = new CountableOutputStream(channel.getOutputStream(), _counterSendOps, _counterSendRate);
            os.write(cmdString.getBytes());
            os.flush();

            // fire the 'executed' callback next
            _callbackHandler.handle(_executedCallback, cmdString, _callbackErrorHandler);
        } catch (Exception ex) {
            // ignore
        }
    }

    /**
     * Permanently shuts down SSH connection.
     */
    @Override
    public void close() {
        synchronized (_lock) {
            if (_shutdown)
                return;

            _shutdown = true;

            if (_sshMode.equals(SSHMode.SHELL)) {
                if (_startTimer != null) {
                    _startTimer.cancel();
                }
                doCloseChannel();
            }

            _jsch = null;

            // notify the connection and receive thread if it happens to be waiting
            _lock.notify();
        }
    }

    /**
     * Only for SHELL mode
     */
    private void doCloseChannel() {
        try {
            if (_channel != null) {
                if (_channel.isConnected()) {
                    _channel.disconnect();
                }
                _channel = null;
            }
            if (_session != null) {
                if (_session.isConnected()) {
                    _session.disconnect();
                    Handler.tryHandle(_disconnectedCallback, _callbackErrorHandler);
                }
                _session = null;
            }
        } catch (Exception ex) {
            // ignore
        }
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
        for (int a = 0; a < len; a++) {
            cBuffer[a] = (char) (buffer[offset + a] & 0xff);
        }

        return new String(cBuffer);
    }

    /**
     * Creates an unresolved socket address.
     */
    private static InetSocketAddress parseAndResolveDestination(String dest) {
        if (Strings.isEmpty(dest))
            throw new IllegalArgumentException("No destination has been set.");

        int lastIndexOfPort = dest.lastIndexOf(':');
        if (lastIndexOfPort < 1)
            throw new IllegalArgumentException("'dest' is missing a port (no ':' found)");

        String addrPart = dest.substring(0, lastIndexOfPort);
        String portPart = dest.substring(lastIndexOfPort + 1);

        if (Strings.isEmpty(addrPart))
            throw new IllegalArgumentException("'dest' is missing or empty.");

        if (Strings.isEmpty(portPart))
            throw new IllegalArgumentException("port is missing or empty.");

        int port;
        try {
            port = Integer.parseInt(portPart);

        } catch (Exception exc) {
            throw new IllegalArgumentException("port was invalid - '" + portPart + "'");
        }

        return new InetSocketAddress(addrPart, port);
    }
}
