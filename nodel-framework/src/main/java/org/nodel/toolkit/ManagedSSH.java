package org.nodel.toolkit;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
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

/**
 * Managed SSH toolkit based heavily on the existing Managed TCP class.
 *
 *  This Nodel feature contribution is thanks to:
 *   - [ACMI](https://acmi.net.au), implemented by [Automatic](https://automatic.com.au)
 *
 *  Features include:
 *  - staggered start up (prevent startup storms)
 *  - event-driven with asynchronous request support
 *  - efficient stream filtering
 *    - minimisation of String object fragmentation
 *    - automatic delimiting
 *    - UTF8 decoded
 *    - trimmed
 *  - exponential back-off
 */
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
     * The maximum segment allowed between delimiters (default 2 MB)
     */
    private final static int MAX_SEGMENT_ALLOWED = 2 * 1024 * 1024;

    /**
     * The minimum gap between connections (default 500ms)
     * A minimum gap is used to achieve greater reliability when connecting to hosts
     * that may be network challenged.
     */
    private final static int MIN_CONNECTION_GAP = 500;

    /**
     * The maximum back-off time allowed (default 32 secs or 2^5 millis)
     */
    private final static int MAX_BACKOFF = 32000;

    /**
     * The connection or SSH read timeout (default 5 mins)
     */
    private static final int RECV_TIMEOUT = 5 * 60000;

    /**
     * The amount of time given to connect to a socket.
     */
    private static final int CONNECT_TIMEOUT = 30000;

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
    private H1<String> _receivedCallback;

    /**
     * (see setter)
     */
    private H1<String> _sentCallback;

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
     * The receive thread.
     */
    private final Thread _thread;

    /**
     * Shared timer framework to use.
     */
    private final Timers _timerThread;

    /**
     * The output stream
     * (if a field because its accessed by other threads)
     * (must be set and cleared with 'outputStream')
     */
    private OutputStream _outputStream;

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

    private enum ReadMode {
        UnboundedRaw,
        CharacterDelimitedText
    }

    /**
     * If length delimited mode is being used with start / stop flags
     */
    private ReadMode _mode = ReadMode.CharacterDelimitedText;

    /**
     * The default request timeout value (timed from respective 'send')
     */
    private int _requestTimeout = 10000;

    /**
     * The request timeout value (timed from respective 'queue')
     */
    private final static int LONG_REQ_TIMEOUT = 60000;

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
     * The request queue
     */
    private final ConcurrentLinkedQueue<QueuedRequest> _requestQueue = new ConcurrentLinkedQueue<>();

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
     * SSH: Session (must be set and cleared with 'outputStream')
     */
    private final JSch _jsch;

    /**
     * SSH: Session (must be set and cleared with 'outputStream')
     */
    private Session _sshSession;

    /**
     * SSH: Channel (must be set and cleared with 'outputStream')
     */
    private Channel _sshChannel;

    /**
     * (must be set and cleared with 'outputStream')
     * (see setter)
     */
    private boolean _disableEcho;

    /**
     * SSH: Disable TELNET console echo
     */
    public void setDisableEcho(boolean value) {
        _disableEcho = value;
    }

    /**
     * (see setter)
     */
    public boolean getDisableEcho() {
        return _disableEcho;
    }

    private String _username;

    public void setUsername(String value) {
        _username = value;
    }

    public String getUsername() {
        return _username;
    }

    private String _password;

    public void setPassword(String value) {
        _password = value;
    }

    public String getPassword() {
        return _password;
    }

    /**
     * (constructor)
     */
    public ManagedSSH(BaseNode node,
                      String dest,
                      H0 threadStateHandler,
                      H1<Exception> callbackExceptionHandler,
                      CallbackQueue callbackQueue,
                      ThreadPool threadPool,
                      Timers timers) {
        _dest = dest;

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
        _thread.setName(node.getName().getReducedName() + "_sshConnectAndReceive_" + _instance);
        _thread.setDaemon(true);

        // register the counters
        String counterName = "'" + node.getName().getReducedName() + "'";
        _counterConnections = Diagnostics.shared().registerSharableCounter(counterName + ".SSH connects", true);
        _counterRecvOps = Diagnostics.shared().registerSharableCounter(counterName + ".SSH receives", true);
        _counterRecvRate = Diagnostics.shared().registerSharableCounter(counterName + ".SSH receive rate", true);
        _counterSendOps = Diagnostics.shared().registerSharableCounter(counterName + ".SSH sends", true);
        _counterSendRate = Diagnostics.shared().registerSharableCounter(counterName + ".SSH send rate", true);

        // prepare for JSch library use straight up
        _jsch = new JSch();
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
     * When a data segment arrives.
     */
    public void setReceivedHandler(H1<String> handler) {
        _receivedCallback = handler;
    }

    /**
     * When a data segment is sent
     */
    public void setSentHandler(H1<String> handler) {
        _sentCallback = handler;
    }

    /**
     * When a a connector a request timeout occurs.
     */
    public void setTimeoutHandler(H0 handler) {
        _timeoutCallback = handler;
    }

    /**
     * Sets the destination.
     */
    public void setDest(String dest) {
        synchronized(_lock) {
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
        synchronized(_lock) {
            if (delims == null)
                _receiveDelimiters = "";
            else
                _receiveDelimiters = delims;

            if (Strings.isEmpty(_receiveDelimiters))
                _mode = ReadMode.UnboundedRaw;
        }
    }

    /**
     * Sets the connection and receive timeout.
     */
    public void setTimeout(int value) {
        synchronized(_lock) {
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
     * (see https://tools.ietf.org/html/rfc4254)
     */
    private final static byte[] TERMINAL_MODE_ECHO_OFF = new byte[]{
            53, // ECHO
            0, 0, 0, 0, // 4th byte: 0: ECHO OFF, 1: ECHO ON
            0 // TTY_OP_END
    };

    /**
     * Safely starts this SSH connection after event handlers have been set.
     */
    public void start() {
        synchronized(_lock) {
            if (_shutdown || _started)
                return;

            _started = true;

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
     * Establishes a socket and continually reads.
     */
    private void connectAndRead() throws Exception {
        // keeping on method stack until can be safely 'synchronized'
        Session session = null;
        Channel channel = null;
        OutputStream os = null;

        try {
            InetSocketAddress socketAddress = parseAndResolveDestination(_dest);
            session = _jsch.getSession(_username, socketAddress.getAddress().getHostAddress(), socketAddress.getPort());
            if (!Strings.isEmpty(_password))
                session.setPassword(_password);

            // set the incoming read timeout
            session.setTimeout(_timeout);

            java.util.Properties config = new java.util.Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);

            int interval = session.getServerAliveInterval();

            session.connect(CONNECT_TIMEOUT);

            channel = session.openChannel("shell");
            ChannelShell channelShell = (ChannelShell) channel;

            if (_disableEcho)
                channelShell.setTerminalMode(TERMINAL_MODE_ECHO_OFF);

            channel.connect(); // NOTE: channel.connect(CONNECT_TIMEOUT) stopped incoming data for some reason

            _counterConnections.incr();

            // 'inject' countable stream
            os = new CountableOutputStream(channel.getOutputStream(), _counterSendOps, _counterSendRate);

            // update flag
            _lastSuccessfulConnection = System.nanoTime();

            synchronized (_lock) {
                if (_shutdown)
                    return;

                _outputStream = os;
                _sshSession = session;
                _sshChannel = channel;

                // connection has been successful so reset variables
                // related to exponential back-off

                _recentlyConnected = true;

                _backoffTime = MIN_CONNECTION_GAP;
            }

            // fire the connected event
            _callbackHandler.handle(_connectedCallback, _callbackErrorHandler);

            InputStream is = channel.getInputStream();

            // start reading
            if (_mode == ReadMode.CharacterDelimitedText)
                readTextLoop(is);
            else if (_mode == ReadMode.UnboundedRaw)
                readUnboundedRawLoop(is);
            else
                throw new Exception("Internal failure - unknown Input Stream Mode"); // should never get here

            // (any non-timeout exceptions will be propagated to caller...)

        } catch (Exception exc) {
            // fire the disconnected handler if was previously connected
            if (os != null)
                Handler.tryHandle(_disconnectedCallback, _callbackErrorHandler);

            throw exc;

        } finally {
            // always gracefully close the socket and invalidate the socket fields

            synchronized(_lock) {
                _sshSession = null;
                _sshChannel = null;
                _outputStream = null;
            }

            safeCleanup(session, channel);
        }
    }

    private static void safeCleanup(Session session, Channel channel) {
        try {
            if (session != null)
                session.disconnect(); // regardless of connection state
        } catch (Exception exc) {
            // consume
        }

        try {
            if (channel != null)
                channel.disconnect(); // regardless of connection state
        } catch (Exception exc) {
            // consume
        }
    }

    /**
     * The reading loop will continually read until an error occurs
     * or the stream is gracefully ended by the peer.
     *
     * ("resource" warning suppression applies to 'bis'. It's not valid because socket itself gets closed)
     */
    @SuppressWarnings("resource")
    private void readTextLoop(InputStream is) throws Exception {
        BufferedInputStream bis = new BufferedInputStream(new CountableInputStream(is, _counterRecvOps, _counterRecvRate), 1024);

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
            if (str != null)
                handleReceivedData(str);

            // then fire the disconnected callback
            Handler.tryHandle(_disconnectedCallback, _callbackErrorHandler);
        }
    }

    /**
     * No read-delimiters specified, so fire events as data segments arrive.
     */
    @SuppressWarnings("resource")
    private void readUnboundedRawLoop(InputStream is) throws IOException {
        CountableInputStream cis = new CountableInputStream(is, _counterRecvOps, _counterRecvRate);

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
            // then fire the disconnected callback
            Handler.tryHandle(_disconnectedCallback, _callbackErrorHandler);
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
        _callbackHandler.handle(_receivedCallback, data, _callbackErrorHandler);

        processQueue();
    }

    private static class QueuedRequest {

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
            return timeDiff > LONG_REQ_TIMEOUT;
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
            if(_activeRequest != null){
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
        QueuedRequest nextRequest;

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

        // character(s) delimited mode:

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

        sendBufferNow(buffer, data, false);
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

        Handler.tryHandle(_sentCallback, origData, _callbackErrorHandler);
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

            safeCleanup(_sshSession, _sshChannel);
        }
    }

    /**
     * Permanently shuts down this managed SSH connection.
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

            safeCleanup(_sshSession, _sshChannel);

            _sshSession = null;
            _sshChannel = null;

            // notify the connection and receive thread if it happens to be waiting
            _lock.notify();
        }
    }

    /**
     * Creates an unresolved socket address.
     */
    private static InetSocketAddress parseAndResolveDestination(String dest) {
        if (Strings.isBlank(dest))
            throw new IllegalArgumentException("No destination has been set.");

        int lastIndexOfPort = dest.lastIndexOf(':');
        if (lastIndexOfPort < 1)
            throw new IllegalArgumentException("'dest' is missing a port (no ':' found)");

        String addrPart = dest.substring(0, lastIndexOfPort);
        String portPart = dest.substring(lastIndexOfPort + 1);

        if (Strings.isBlank(addrPart))
            throw new IllegalArgumentException("'dest' is missing or empty.");

        if (Strings.isBlank(portPart))
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