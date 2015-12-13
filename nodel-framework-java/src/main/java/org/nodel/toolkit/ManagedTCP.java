package org.nodel.toolkit;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

import org.nodel.Formatting;
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
import org.nodel.io.Stream;
import org.nodel.threading.ThreadPool;
import org.nodel.threading.TimerTask;
import org.nodel.threading.Timers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A managed TCP solution for high density connection use.
 * 
 *  Features include:
 *  - staggered start up (prevent startup storms)
 *  - event-based
 *  - efficient stream filtering
 *    - minimisation of String object fragmentation 
 *    - automatic delimiting
 *    - UTF8 decoded
 *    - trimmed
 *  - exponential back-off
 */
public class ManagedTCP implements Closeable {
    
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
     * The minimum gap between connections (default 500ms)
     * A minimum gap is used to achieve greater reliability when connecting to  hosts
     * that may be TCPIP stack challenged (think older devices, projectors, etc.)
     */
    private final static int MIN_CONNETION_GAP = 500;
    
    /**
     * The maximum back-off time allowed (default 32 secs or 2^5 millis)
     */
    private final static int MAX_BACKOFF = 32000;

    /**
     * The connection or TCP read timeout (default 5 mins) 
     */
    private static final int RECV_TIMEOUT =  5 * 60000;
    
    /**
     * The amount of time given to connect to a socket.
     */
    private static final int CONNECT_TIMEOUT = 30000;
    
    /**
     * (synchronisation / locking)
     */
    private Object _lock = new Object();
    
    /**
     * The shared thread-pool
     */
    private ThreadPool _threadPool;
    
    /**
     * The safe queue as provided by a host
     */
    private CallbackHandler _callbackHandler;    
    
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
    private H1<Exception> _callbackErrorHandler;

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
    private Thread _thread;
    
    /**
     * Shared timer framework to use.
     */
    private Timers _timerThread;
    
    /**
     * The current socket.
     * (must be set and clear with 'outputStream')
     */
    private Socket _socket;
    
    /**
     * The output stream
     * (if a field because its accessed by other threads)
     * (must be set and clear with 'outputStream')
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
    
    /**
     * If using binary transmission.
     */
    private Character _binaryStartFlag = null;
    
    private enum Modes {
        UnboundedRaw,
        LengthDelimitedRaw,
        CharacterDelimitedText
    }
    
    /**
     * If length delimited mode is being used with start / stop flags
     */
    private Modes _mode = Modes.CharacterDelimitedText;
    
    /**
     * If using binary transmission.
     */
    private Character _binaryStopFlag = null;
    
    /**
     * The default request timeout value (timed from respective 'send')
     */
    private int _requestTimeout = 10000;
    
    /**
     * The request timeout value (timed from respective 'queue')
     */
    private int _longTermRequestTimeout = 60000;
    
    /**
     * The last time a successful connection occurred (connection or data receive)
     * (nano time)
     */
    private long _lastSuccessfulConnection = System.nanoTime();

    /**
     * (Response for handling thread-state)
     */
    private H0 _threadStateHandler;

    /**
     * The connection and receive timeout.
     */
    private int _timeout = RECV_TIMEOUT;
    
    /**
     * (diagnostics)
     */
    private SharableMeasurementProvider _counterConnections;
    
    /**
     * (diagnostics)
     */    
    private SharableMeasurementProvider _counterRecvOps;
    
    /**
     * (diagnostics)
     */    
    private SharableMeasurementProvider _counterRecvRate;
    
    /**
     * (diagnostics)
     */    
    private SharableMeasurementProvider _counterSendOps;
    
    /**
     * (diagnostics)
     */    
    private SharableMeasurementProvider _counterSendRate;
    
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
    public ManagedTCP(BaseNode node, String dest, H0 threadStateHandler, H1<Exception> callbackExceptionHandler, CallbackHandler callbackQueue, ThreadPool threadPool, Timers timers) {
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
        _thread.setName(node.getName().getReducedName() + "_tcpConnectAndReceive_" + _instance);
        _thread.setDaemon(true);
        
        // register the counters
        String counterName = "'" + node.getName().getReducedName() + "'";
        _counterConnections = Diagnostics.shared().registerSharableCounter(counterName + ".TCP connects", true);
        _counterRecvOps = Diagnostics.shared().registerSharableCounter(counterName + ".TCP receives", true);
        _counterRecvRate = Diagnostics.shared().registerSharableCounter(counterName + ".TCP receive rate", true);
        _counterSendOps = Diagnostics.shared().registerSharableCounter(counterName + ".TCP sends", true);
        _counterSendRate = Diagnostics.shared().registerSharableCounter(counterName + ".TCP send rate", true);
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
    public void setReceiveDelimeters(String delims) {
        synchronized(_lock) {
            if (delims == null)
                _receiveDelimiters = "";
            else
                _receiveDelimiters = delims;
            
            if (Strings.isNullOrEmpty(_receiveDelimiters))
                _mode = Modes.UnboundedRaw;
        }
    }
    
    /**
     * Sets the binary start and (optional) stop flags.
     */
    public void setBinaryStartStopFlags(String startStopFlags) {
        _binaryStartFlag = null;
        _binaryStopFlag = null;
        
        int len = startStopFlags != null ? startStopFlags.length() : 0;

        if (len > 0) {
            _binaryStartFlag = startStopFlags.charAt(0);
            _mode = Modes.LengthDelimitedRaw;
        }

        if (len > 1)
            _binaryStopFlag = startStopFlags.charAt(1);
    }
    
    public Character getBinaryStartFlag() {
        return _binaryStartFlag;
    }

    public Character getBinaryStopFlag() {
        return _binaryStopFlag;
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
     * Safely starts this TCP connection after event handlers have been set.
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
                    _backoffTime = MIN_CONNETION_GAP;

                else {
                    _backoffTime = Math.min(_backoffTime * 2, MAX_BACKOFF);
                    _backoffTime = Math.max(MIN_CONNETION_GAP, _backoffTime);
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
        Socket socket = null;
        OutputStream os = null;
        
        try {
            socket = new Socket();
            InetSocketAddress socketAddress = parseAndResolveDestination(_dest);
            socket.connect(socketAddress, CONNECT_TIMEOUT);
            
            _counterConnections.incr();
            
            socket.setSoTimeout(_timeout);
            
            // 'inject' countable stream
            os = new CountableOutputStream(socket.getOutputStream(), _counterSendOps, _counterSendRate);
            
            // update flag
            _lastSuccessfulConnection = System.nanoTime();
            
            synchronized (_lock) {
                if (_shutdown)
                    return;
                
                _socket = socket;
                _outputStream = os;
                
                // connection has been successful so reset variables
                // related to exponential back-off

                _recentlyConnected = true;
                
                _backoffTime = MIN_CONNETION_GAP;
            }
            
            // fire the connected event
            _callbackHandler.handle(_connectedCallback, _callbackErrorHandler);
            
            // start reading
            if (_mode == Modes.LengthDelimitedRaw)
                readLengthDelimitedLoop(socket, _binaryStartFlag, _binaryStopFlag);
            
            else if (_mode == Modes.CharacterDelimitedText)
                readTextLoop(socket);
            
            else { // mode is 'UnboundedRaw' 
                readUnboundedRawLoop(socket);
            }

            // (any non-timeout exceptions will be propagated to caller...)
            
        } catch (Exception exc) {
            // fire the disconnected handler if was previously connected
            if (os != null)
                Handler.tryHandle(_disconnectedCallback, _callbackErrorHandler);
            
            throw exc;
            
        } finally {
            // always gracefully close the socket and invalidate the socket fields
            
            synchronized(_lock) {
                _socket = null;
                _outputStream = null;
            }
            
            Stream.safeClose(socket);
        }
    }

    /**
     * The reading loop will continually read until an error occurs 
     * or the stream is gracefully ended by the peer.
     * 
     * ("resource" warning suppression applies to 'bis'. It's not valid because socket itself gets closed) 
     */
    @SuppressWarnings("resource")
    private void readTextLoop(Socket socket) throws Exception {
        InputStream in = socket.getInputStream();
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
     * The reading loop will continually read until an error occurs 
     * or the stream is gracefully ended by the peer.

     * (Suppress 'resource' ignored because 'in' stream is closed in calling function.)
     */
    @SuppressWarnings("resource")
    private void readLengthDelimitedLoop(Socket socket, char startFlag, Character optStopFlag) throws Exception {
        InputStream in = socket.getInputStream();
        BufferedInputStream bis = new BufferedInputStream(new CountableInputStream(in, _counterRecvOps, _counterRecvRate), 1024);
        
        // create a buffer that'll be reused
        // start off small, will grow as needed
        BufferBuilder bb = new BufferBuilder(256);
        
        // create an additional temporary buffer which can be used with 'length' delimited parsing
        byte[] buffer = new byte[1024];        
        
        // got 'start flag'?
        boolean synced = false;
        
        // how many bytes to read
        int bytesToRead = 0;
        
        while (!_shutdown) {
            int c = bis.read();
            
            if (c < 0)
                break;
            
            // discard data until we sync up with the 'start' flag
            if (!synced) {
                if (c == startFlag) {
                    synced = true;
                    // (put back)
                    bb.append(c);
                    
                    // read 'length' (value excludes 'start', 'stop' flags)
                    int len1 = Stream.readUnsignedByte(bis);
                    int len2 = Stream.readUnsignedByte(bis);
                    
                    int length = Stream.readUnsignedShort(len1, len2);
                    
                    if (length >= MAX_SEGMENT_ALLOWED)
                        // drop the connection
                        throw new IOException("Packet length indicates it is too large to accept (" + Formatting.formatByteLength(length) + ").");
                    
                    // (put back)
                    bb.append(len1);
                    bb.append(len2);
                    
                    // already read 2 ('length') length
                    bytesToRead = length - 2;
                } else {
                    // not synced, so keep discarding
                    continue;
                }
            } 
                
            while (!_shutdown) {
                // we're synced, so can read as much as we need to

                if (bytesToRead > 0) {
                    int bytesRead = bis.read(buffer, 0, Math.min(bytesToRead, buffer.length));
                    if (bytesRead < 0)
                        throw new EOFException();
                    
                    bytesToRead -= bytesRead;

                    // 'copy' the buffer
                    bb.append(buffer, 0, bytesRead);
                } else {
                    // we've read enough
                    break;
                }
            }
            
            if (optStopFlag != null) {
                c = Stream.readUnsignedByte(bis);
                
                if (c != optStopFlag.charValue())
                    throw new IOException("Stop flag was not matched after reading the packet bytes.");
                
                // (put back)
                bb.append(c);
                
                // fire the handled event
                String str = bb.getRawString();
                handleReceivedData(str);
                
                bb.reset();
                
                synced = false;
            }
        } // (while)
        
        // the peer has gracefully closed down the connection or we're shutting down
        
        if (!_shutdown) {
            // drop left over data and fire the disconnected callback
            Handler.tryHandle(_disconnectedCallback, _callbackErrorHandler);
        }
    }
    
    /**
     * No read-delimiters specified, so fire events as data segments arrive. 
     */
    @SuppressWarnings("resource")
    private void readUnboundedRawLoop(Socket socket) throws IOException {
        CountableInputStream cis = new CountableInputStream(socket.getInputStream(), _counterRecvOps, _counterRecvRate);
        
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
        if (Strings.isNullOrEmpty(requestData))
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
        if (Strings.isNullOrEmpty(data))
            return null;
        
        // (data will be at least 1 character in length)
        
        byte[] buffer = null;
        
        if (_mode == Modes.LengthDelimitedRaw) {
            // length delimited mode:
            buffer = stringToBuffer(data, null);
            
            return buffer;
        } else {
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

            Stream.safeClose(_socket);
        }
    }
    
    /**
     * Permanently shuts down this managed TCP connection.
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

            Stream.safeClose(_socket);

            _socket = null;
            
            // notify the connection and receive thread if it happens to be waiting
            _lock.notify();
        }
    }
    
    /**
     * Creates an unresolved socket address.
     */
    private static InetSocketAddress parseAndResolveDestination(String dest) {
        if (Strings.isNullOrEmpty(dest))
            throw new IllegalArgumentException("No destination has been set.");

        int lastIndexOfPort = dest.lastIndexOf(':');
        if (lastIndexOfPort < 1)
            throw new IllegalArgumentException("'dest' is missing a port (no ':' found)");

        String addrPart = dest.substring(0, lastIndexOfPort);
        String portPart = dest.substring(lastIndexOfPort + 1);

        if (Strings.isNullOrEmpty(addrPart))
            throw new IllegalArgumentException("'dest' is missing or empty.");
        
        if (Strings.isNullOrEmpty(portPart))
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
