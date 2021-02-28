package org.nodel.toolkit;

import org.nodel.Handler;
import org.nodel.Strings;
import org.nodel.diagnostics.Diagnostics;
import org.nodel.diagnostics.SharableMeasurementProvider;
import org.nodel.host.BaseNode;
import org.nodel.io.BufferBuilder;
import org.nodel.threading.CallbackQueue;
import org.nodel.threading.ThreadPool;
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
     * Permanently shut down?
     */
    private boolean _shutdown;

    /**
     * Shared timer framework to use.
     */
    private final Timers _timerThread;

    /**
     * (Response for handling thread-state)
     */
    private final Handler.H0 _threadStateHandler;

    /**
     * Holds the full socket address (addr:port)
     * (may be null)
     */
    private final String _dest;

    /**
     * known hosts which will be used with ssh connection
     */
    private final String _knownHosts;

    /**
     * Inet Address
     */
    private final InetSocketAddress _inetSocketAddress;

    /**
     * Holds username
     */
    private final String _username;

    /**
     * Holds password
     */
    private final String _password;

    /**
     * (see setter)
     */
    private Handler.H0 _connectedCallback;

    /**
     * (see setter)
     */
    private Handler.H0 _disconnectedCallback;

    /**
     * (see setter)
     */
    private Handler.H1<String> _executedCallback;

    /**
     * (see setter)
     */
    private Handler.H1<String> _shellConsoleOutputCallback;

    /**
     * (see setter)
     */
    private Handler.H1<String> _receivedCallback;

    /**
     * (see setter)
     */
    private Handler.H0 _timeoutCallback;

    /**
     * When errors occur during callbacks.
     */
    private final Handler.H1<Exception> _callbackErrorHandler;

    /**
     * The safe queue as provided by a host
     */
    private final CallbackQueue _callbackHandler;

    /**
     * (diagnostics)
     */
    private final SharableMeasurementProvider _counterExecutions;

    /**
     * The command queue
     */
    private final ConcurrentLinkedQueue<ManagedSSH.QueuedCommand> _commandQueue = new ConcurrentLinkedQueue<ManagedSSH.QueuedCommand>();

    /**
     * Holds the queue length ('ConcurrentLinkedQueue' not designed to handle 'size()' efficiently)
     */
    private int _queueLength = 0;

    /**
     * The active command
     */
    private ManagedSSH.QueuedCommand _activeCommand;

    /**
     * The default request timeout value (timed from respective 'send')
     */
    private int _requestTimeout = 10000;

    /**
     * The command timeout value (timed from respective 'queue')
     */
    private final int _longTermRequestTimeout = 60000;

    /**
     * The maximum data to be received (default 1 MB)
     */
    private final static int MAX_DATA_ALLOWED = 1 * 1024 * 1024;

    /**
     * Exec or Shell
     */
    private final Mode _mode;

    /**
     * Session
     */
    private Session _session;

    /**
     * Shell
     */
    private Channel _channel;

    /**
     * Parameters for reverse forwarding
     * ex>
     * {
     * "bind_address": "abcd",
     * "rport": 80,
     * "host": "localhost",
     * "lport": 8086,
     * }
     */
    private Map<String, Object> _reverseForwardingParameters;

    /**
     * Write shell's input stream to out by callback
     */
    private class ShellConsoleOutputStream extends OutputStream {

        private final BufferBuilder bufferBuilder = new BufferBuilder(256);

        @Override
        public void write(int b) throws IOException {
            if (_inputStreamHandleMode.equals(InputStreamHandleMode.CharacterDelimitedText)) {
                if (charMatches((char) b, _receiveDelimiters)) {
                    String str = bufferBuilder.getTrimmedString();
                    if (str != null) {
                        handleReceivedData(str);
                        _callbackHandler.handle(_shellConsoleOutputCallback, str, _callbackErrorHandler);
                    }
                    bufferBuilder.reset();
                } else {
                    if (bufferBuilder.getSize() >= MAX_DATA_ALLOWED) {
                        // drop the connection
                        throw new IOException("Too much data arrived (at least " + bufferBuilder.getSize() / 1024 + " KB) before any delimeter was present; dropping connection.");
                    }
                    bufferBuilder.append((byte) b);
                }
            }
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            baos.write(b, off, len);
            baos.flush();
            handleReceivedData(baos.toString());
            _callbackHandler.handle(_shellConsoleOutputCallback, baos.toString(), _callbackErrorHandler);
        }
    }

    /**
     * worker thread to monitor session connectivity (only for SHELL mode)
     */
    private Thread _worker;

    /**
     *
     */
    private final Object _workerSignal = new Object();

    /**
     *
     */
    private boolean _workerEnabled = false;

    /**
     * The delimiters to split the receive data on.
     */
    private String _receiveDelimiters = "\r\n";

    public enum InputStreamHandleMode {
        UnboundedRaw,
        CharacterDelimitedText
    }

    private InputStreamHandleMode _inputStreamHandleMode = InputStreamHandleMode.CharacterDelimitedText;

    /**
     * (constructor)
     */
    public ManagedSSH(
            Mode mode,
            BaseNode node,
            String dest,
            String knownHosts,
            String username,
            String password,
            Handler.H0 threadStateHandler,
            Handler.H1<Exception> callbackExceptionHandler,
            CallbackQueue callbackQueue,
            ThreadPool threadPool,
            Timers timers) {
        _mode = mode;
        _dest = dest;
        _knownHosts = knownHosts;
        _username = username;
        _password = password;

        _inetSocketAddress = parseAndResolveDestination(_dest);

        _threadStateHandler = threadStateHandler;
        _callbackErrorHandler = callbackExceptionHandler;
        _callbackHandler = callbackQueue;
        _threadPool = threadPool;
        _timerThread = timers;

        // register the counters
        String counterName = "'" + node.getName().getReducedName() + "'";
        _counterExecutions = Diagnostics.shared().registerSharableCounter(counterName + ".SSH executes", true);

        // create JSch
        _jsch = new JSch();
    }

    private void doInitialize() {
        try {
            // set knownHosts if existing
            if (!Strings.isEmpty(_knownHosts)) {
                InputStream isKnowsHosts = new ByteArrayInputStream(_knownHosts.getBytes());
                _jsch.setKnownHosts(isKnowsHosts);
            }

            // Exec mode
            if (_mode.equals(Mode.EXEC)) {
                // A new session per command should be created.
                // do nothing here
            }
            // Shell mode
            else if (_mode.equals(Mode.SHELL)) {

                // assuming both server and client should keep connected

                _session = _jsch.getSession(_username, _inetSocketAddress.getAddress().getHostAddress(), _inetSocketAddress.getPort());

                if (!Strings.isEmpty(_password)) {
                    _session.setPassword(_password);
                }

                java.util.Properties config = new java.util.Properties();
                config.put("StrictHostKeyChecking", "no");
                _session.setConfig(config);
                _session.setServerAliveInterval(5000);
                _session.connect();

                _channel = _session.openChannel("shell");

                new StreamCopier(_channel.getInputStream(), new ShellConsoleOutputStream())
                        .bufSize(1024 * 10 * 2)
                        .inputStreamHandleMode(_inputStreamHandleMode)
                        .spawn("ShellConsoleOutputStream - " + _instance);

                _channel.connect();

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

            } else {
                throw new RuntimeException("Unknown SSH mode");
            }

        } catch (Exception ex) {
            _logger.debug("[doInitialize] Exception", ex);

            // reset all resource
            if (_channel != null) {
                if (_channel.isConnected()) {
                    _channel.disconnect();
                }
                _channel = null;
            }
            if (_session != null) {
                if (_session.isConnected()) {
                    _session.disconnect();
                }
                _session = null;
            }
        }
    }

    /**
     * Note: Should be called after all callbacks registered
     */
    public void connect() {
        if (_mode.equals(Mode.EXEC)) {
            doInitialize();
        } else {
            // start the worker thread to monitor session connectivity
            startWorker();
        }
    }

    public void start() {
    }

    /**
     * Parameters which are used for reverse port forwarding.
     *
     * @param params, Map<String, Object>
     */
    public void setReverseForwardingParameters(Map<String, Object> params) {
        _reverseForwardingParameters = params;
    }

    /**
     * When a connection moves into a connected state.
     */
    public void setConnectedHandler(Handler.H0 handler) {
        _connectedCallback = handler;
    }

    /**
     * When the connected moves into a disconnected state.
     */
    public void setDisconnectedHandler(Handler.H0 handler) {
        _disconnectedCallback = handler;
    }

    /**
     * FOR EXEC MODE
     * When a command is executed
     * 1st para : cmdString, 2nd para : response
     */
    public void setExecutedHandler(Handler.H1<String> handler) {
        _executedCallback = handler;
    }

    /**
     * FOR SHELL MODE
     * When a shell input stream is available, console output will be sent with this callback.
     */
    public void setShellConsoleOutputHandler(Handler.H1<String> handler) {
        _shellConsoleOutputCallback = handler;
    }

    /**
     * When a data segment arrives.
     */
    public void setReceivedHandler(Handler.H1<String> handler) {
        _receivedCallback = handler;
    }

    /**
     * When a command timeout occurs.
     */
    public void setTimeoutHandler(Handler.H0 handler) {
        _timeoutCallback = handler;
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

    public enum Mode {
        EXEC("exec"),
        SHELL("shell");

        private final String name;

        Mode(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    /**
     * When an actual data segment was received; deals with command callbacks if necessary
     */
    private void handleReceivedData(String data) {

        // deal with any queued callbacks first

        ManagedSSH.QueuedCommand command = null;

        // then check for any requests
        // (should release lock as soon as possible)
        synchronized (_lock) {
            if (_activeCommand != null) {

                // compare given data to cmdString of the active command
                // if identical, ignore.
                // Because it is required to send response only.
                if (_inputStreamHandleMode.equals(InputStreamHandleMode.CharacterDelimitedText)) {
                    if (data.equals(_activeCommand.cmdString)) {
                        return;
                    }
                }
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
        ManagedSSH.QueuedCommand nextCommand = null;

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
            executeCommandNow(nextCommand.cmdString, false); // do not use thread pool
        }
    }

    /**
     * Safely executes command without overlapping any existing commands
     */
    public void execute(String cmdString) throws Exception {
        execute(cmdString, null);
    }

    public void execute(String cmdString, Handler.H1<String> responseHandler) throws Exception {
        if (Strings.isEmpty(cmdString)) {
            return;
        }

        ManagedSSH.QueuedCommand command = new ManagedSSH.QueuedCommand(cmdString, _requestTimeout, responseHandler);
        doQueueCommand(command);
    }

    public void doQueueCommand(ManagedSSH.QueuedCommand command) {
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
            executeCommandNow(command.cmdString, false); // do not use thread pool
        }

        // without a timer, the queue needs to serviced on both send and receive
        processQueue();
    }

    /**
     * Executes command. Returns immediately. Will not throw any exceptions.
     */
    public void executeNow(final String cmdString) {
        if (Strings.isEmpty(cmdString)) {
            return;
        }

        executeCommandNow(cmdString, false); // do not use thread pool
    }

    /**
     * Executes a command immediately, optionally using a thread-pool
     * In some cases, sequence of commands execution is broken. Please do not use thread pool.
     */
    private void executeCommandNow(final String cmdString, boolean onThreadPool) {
        try {
            if (onThreadPool) {
                _threadPool.execute(new Runnable() {

                    @Override
                    public void run() {
                        _threadStateHandler.handle();
                        if (_mode.equals(Mode.SHELL)) {
                            shellCommandNow0(cmdString);
                        } else {
                            executeCommandNow0(cmdString);
                        }
                    }
                });
            } else {
                if (_mode.equals(Mode.SHELL)) {
                    shellCommandNow0(cmdString);
                } else {
                    executeCommandNow0(cmdString);
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
            BufferBuilder bb = new BufferBuilder(256);
            while (true) {
                int c = in.read();
                if (c < 0)
                    break;

                if (bb.getSize() >= MAX_DATA_ALLOWED) {
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

    private void executeCommandNow0(String cmdString) {
        Session session = null;
        Channel channel = null;
        try {
            // Exec Session should be created per a command and disconnected.
            session = _jsch.getSession(_username, _inetSocketAddress.getAddress().getHostAddress(), _inetSocketAddress.getPort());
            session.setPassword(_password);
            java.util.Properties config = new java.util.Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.connect();

            channel = session.openChannel("exec");
            ((ChannelExec) channel).setCommand(cmdString);
            channel.setInputStream(null);
            channel.setOutputStream(System.out);

            channel.connect();

            Handler.tryHandle(_connectedCallback, _callbackErrorHandler);

            handleExecModeInputStream(channel);

            _counterExecutions.incr();

        } catch (Exception ex) {
            _logger.debug("[executeCommandNow0] Exception", ex);
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
                _logger.debug("[executeCommandNow0::finally] Exception", ex);
            }
        }
    }

    /**********************************************************************************************************************************
     * SHELL MODE
     *********************************************************************************************************************************/

    /**
     * Note : User needs to handle connectivity by himself if the session is disconnected.
     *
     * @return, Channel
     */
    private Channel getChannelShell() {
        if (_session == null || !_session.isConnected()) {
            _logger.debug("[getChannelShell] Session disconnected");
            synchronized (_workerSignal) {
                doClose();
            }
            throw new RuntimeException("Session disconnected");
        }
        return _channel;
    }

    private void shellCommandNow0(String cmdString) {
        try {
            final String cmd = cmdString + "\r\n";
            Channel channel = this.getChannelShell();
            OutputStream os = channel.getOutputStream();
            os.write(cmd.getBytes());
            os.flush();

            // handleReceivedData() will handle data from InputStream of the channel

            // fire the 'executed' callback next
            _callbackHandler.handle(_executedCallback, cmdString, _callbackErrorHandler);

        } catch (IOException | RuntimeException ex) {
            _logger.debug("[shellCommandNow0] Exception", ex);
            _logger.debug("[shellCommandNow0] will retry to connect");
        }
    }

    /**
     * start the worker to monitor session connectivity
     */
    private void startWorker() {
        synchronized (_workerSignal) {
            if (_workerEnabled) {
                return;
            }
            _workerEnabled = true;
            _worker = new Thread(new Runnable() {
                @Override
                public void run() {
                    workerEntry();
                }
            });
            _worker.setName("Session Watcher - Shell mode");
            _worker.setDaemon(true);
            _worker.start();
        }
    }

    /**
     * entry for monitor worker thread
     */
    private void workerEntry() {
        try {
            _logger.info("[workerEntry] called");
            synchronized (_workerSignal) {

                int kickoffTime = 1000 + s_random.nextInt(KICKOFF_DELAY);
                _workerSignal.wait(kickoffTime);

                this.doInitialize();

                _workerSignal.wait(2 * 1000);
            }

            boolean firedDisconnected = false;

            while (_workerEnabled) {
                if (_session != null) {
                    if (!_session.isConnected()) {

                        _logger.debug("[workerEntry] session disconnected");

                        if (!firedDisconnected) {
                            Handler.tryHandle(_disconnectedCallback, _callbackErrorHandler);
                            firedDisconnected = true;
                        }

                        synchronized (_workerSignal) {

                            doClose();

                            _workerSignal.wait(5 * 1000);

                            doInitialize();

                            _workerSignal.wait(5 * 1000);
                        }
                    } else {
                        synchronized (_workerSignal) {
                            // keep monitoring
                            _logger.debug("[workerEntry] Keeping monitoring");

                            _workerSignal.wait(2500);

                            firedDisconnected = false;
                        }
                    }
                } else {
                    synchronized (_workerSignal) {

                        _logger.debug("[workerEntry] Session not created");

                        this.doInitialize();

                        _workerSignal.wait(5 * 1000);
                    }
                }
            }
        } catch (InterruptedException | RuntimeException ex) {
            // ignore
        }
    }

    /**
     * stop monitor worker thread
     */
    private void stopWorker() {
        synchronized (_workerSignal) {
            if (!_workerEnabled) {
                return;
            }
            _workerEnabled = false;
            _worker.interrupt();
        }
        try {
            _worker.join();
        } catch (InterruptedException ex) {
            // ignore
        }
    }

    /**
     * Permanently shuts down this managed SSH connection.
     */
    @Override
    public void close() {
        synchronized (_lock) {
            if (_shutdown)
                return;

            _shutdown = true;

            if (_mode.equals(Mode.SHELL)) {
                stopWorker();
            }

            doClose();
            _jsch = null;

            // notify the connection and receive thread if it happens to be waiting
            _lock.notify();
        }
    }

    private void doClose() {
        if (_mode.equals(Mode.SHELL)) {
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
}