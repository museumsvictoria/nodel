package org.nodel.toolkit;

import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import org.nodel.Handler;
import org.nodel.Handler.H0;
import org.nodel.Handler.H1;
import org.nodel.Handler.H2;
import org.nodel.Strings;
import org.nodel.Threads;
import org.nodel.diagnostics.Diagnostics;
import org.nodel.diagnostics.SharableMeasurementProvider;
import org.nodel.host.BaseNode;
import org.nodel.io.Stream;
import org.nodel.io.UTF8Charset;
import org.nodel.threading.ThreadPool;
import org.nodel.threading.TimerTask;
import org.nodel.threading.Timers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A managed UDP solution
 * 
 *  Features include:
 *  - staggered start up (prevent startup storms)
 *  - event-based
 *  - automatic UTF8 / binary detection
 */
public class ManagedUDP implements Closeable {
    
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
     * The maximum UDP buffer size
     */
    private final static int MAX_BUFFER_SIZE = 65536;

    /**
     * The maximum back-off time allowed (default 32 secs or 2^5 millis)
     */
    private final static int BACKOFF = 16000;

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
     * (see setter)
     */
    private H0 _readyCallback;

    /**
     * (see setter)
     */
    private H2<String, String> _receivedCallback;

    /**
     * (see setter)
     */
    private H1<String> _sentCallback;

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
     */
    private DatagramSocket _socket;
    
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
     * A resolved address
     */
    private InetSocketAddress _resolvedDest;
    
    /**
     * e.g. "0.0.0.0:0"
     * (see setter)
     */
    private String _source;
    
    /**
     * An specific interface to bind to.
     * (will be null if explicit interface selection is not required) 
     */
    private String _intf;
    
    /**
     * The send-queue
     * (self-locked)
     */
    private Queue<QueueItem> _sendQueue = new LinkedList<>();
    
    /**
     * Whether or not the send queue is being processed.
     * (locked around 'sendQueue')
     */
    private boolean _processing = false;
    
    /**
     * This is needed to create the send packet
     */
    private byte[] DUMMY = new byte[1];
    
    /**
     * The send packet (reused)
     * The buffer is replaced each time unfortunately.
     */
    private DatagramPacket _sendPacket = new DatagramPacket(DUMMY, DUMMY.length);
    
    /**
     * The send queue item
     */
    private class QueueItem {
        
        public String dest;
        
        public byte[] buffer;
        
        public String origData;
        
        public QueueItem(String dest, byte[] buffer, String origData) {
            this.dest = dest;
            this.buffer = buffer;
            this.origData = origData;
        }
    }
    
    /**
     * (Response for handling thread-state)
     */
    private H0 _threadStateHandler;

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
     * (constructor)
     */
    public ManagedUDP(BaseNode node, String source, String dest, H0 threadStateHandler, H1<Exception> callbackExceptionHandler, CallbackHandler callbackQueue, ThreadPool threadPool, Timers timers) {
        _source = source;
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
        _thread.setName(node.getName().getReducedName() + "_udpBindAndListen_" + _instance);
        _thread.setDaemon(true);
        
        // register the counters
        String counterName = "'" + node.getName().getReducedName() + "'";
        _counterRecvOps = Diagnostics.shared().registerSharableCounter(counterName + ".UDP receives", true);
        _counterRecvRate = Diagnostics.shared().registerSharableCounter(counterName + ".UDP receive rate", true);
        _counterSendOps = Diagnostics.shared().registerSharableCounter(counterName + ".UDP sends", true);
        _counterSendRate = Diagnostics.shared().registerSharableCounter(counterName + ".UDP send rate", true);
    }
    
    /**
     * When the socket has been bound.
     */
    public void setReadyHandler(H0 handler) {
        _readyCallback = handler;
    }
    
    /**
     * When a data segment arrives.
     */
    public void setReceivedHandler(H2<String, String> handler) {
        _receivedCallback = handler;
    }
    
    /**
     * When a data segment is sent
     */
    public void setSentHandler(H1<String> handler) {
        _sentCallback = handler;
    }    
    
    /**
     * Sets the destination.
     */
    public void setDest(String dest) {
        synchronized(_lock) {
            _dest = dest;
            
            // clear resolved address
            _resolvedDest = null;
        }
    }
    
    /**
     * (see setter)
     */
    public String getDest() {
        return _dest;
    }
    
    /**
     * The source address in form 'intf:port' (where port <= 0 for arbitrary port, normally send-only)
     * 'intf' can be '0.0.0.0' for all
     */
    public void setSource(String source) {
        synchronized(_lock) {
            _source = source;
        }
    }
    
    /**
     * (see setter)
     */
    public String getSource() {
        return _source;
    }
    
    /**
     * Sets the interface.
     */
    public void setIntf(String value) {
    	synchronized(_lock) {
    		_intf = value;
    	}
    }
    
    /**
     * Gets the interface.
     */
    public String getIntf() {
    	return _intf;
    }
    
    /**
     * Gets the active listening port (may be different from port part of 'source')
     */
    public int getListeningPort() {
        DatagramSocket socket = _socket;
        if (socket == null)
            return 0;
        else
            return _socket.getLocalPort();
    }

    
    /**
     * Starts UDP port binding after event handlers have been set.
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
                bindAndListen();

            } catch (Exception exc) {
                // an exception here can only happen if a binding failure
                // has occurred or things are being shutdown

                if (_shutdown) {
                    // thread can gracefully exit
                    return;
                }
                
                // fire the general error-handler that'll hopefully log the issue
                Handler.tryHandle(_callbackErrorHandler, exc);
            }

            // back off for a period of time, no need for exponential back-off, normally
            // just because someone else has bound to the port
            Threads.wait(_lock, BACKOFF);
        } // (while)
    }
    
    /**
     * Establishes a socket and continually reads.
     */
    private void bindAndListen() throws Exception {
        DatagramSocket socket = null;
        MulticastSocket multicastSocket = null;
        
        try {
        	String sourceAddress = _source;
        	String destAddress = _dest;
        	String intfAddress = _intf;
        	
        	boolean sourceMulticast = false;
        	boolean destMulticast = false;
        	
            // lazily resolve source, dest and intf addresses looking for multicast requirements
        	
        	InetSocketAddress sourceSocketAddress = null;
        	if (!Strings.isNullOrEmpty(sourceAddress)) {
        		sourceSocketAddress = parseAndResolveAddress(sourceAddress);
        		InetAddress addressPart = sourceSocketAddress.getAddress(); // (can be null if unresolved)
        		if (addressPart != null && addressPart.isMulticastAddress())
        			sourceMulticast = true;
        	}
            
            InetSocketAddress destSocketAddress = null;
            if (!Strings.isNullOrEmpty(destAddress)) {
            	destSocketAddress = parseAndResolveAddress(destAddress);
            	InetAddress addressPart = destSocketAddress.getAddress();
            	if (addressPart != null && addressPart.isMulticastAddress())
            		destMulticast = true;
            }
            
            InetAddress intfHostAddress = null;
            if (!Strings.isNullOrEmpty(intfAddress))
            	intfHostAddress = InetAddress.getByName(intfAddress);
            
            if (sourceMulticast || destMulticast) {
            	// multicast usage
            	
            	// (requires more complex binding decisions than unicast)
            	boolean bound = false;
            	
            	multicastSocket = new MulticastSocket(null);
            	
            	// 'socket' reference set immediately for clean up purposes
            	socket = multicastSocket;
            	
            	// always set the optional 'interface' if it's specified
            	if (intfHostAddress != null)
            		multicastSocket.setInterface(intfHostAddress);
            	
            	// it's important the source is used as the bind address if it
            	// not a multicast address itself
            		
            	if (sourceMulticast) {
            		if (intfHostAddress == null) {
            			// bind to wildcard address, and specific port
						multicastSocket.bind(new InetSocketAddress((InetAddress) null, sourceSocketAddress.getPort()));
            		} else {
            			// bind to intf address and specific port
            			multicastSocket.bind(new InetSocketAddress(intfHostAddress, sourceSocketAddress.getPort()));
            		}
            			
            		bound = true;
            	}
            	
            	if (!bound) {
            		// specifying multicast with the destination, port can be altered on the fly
            		// during sends
            		multicastSocket.bind(null);
            		bound = true;
            	}
            	
            	// join the multicast group(s) (wouldn't make much sense having one set on 'source' and 'dest' but
            	// they can try)
            	if (sourceMulticast) 
            		multicastSocket.joinGroup(sourceSocketAddress.getAddress());
            	
            	if (destMulticast)
            		multicastSocket.joinGroup(destSocketAddress.getAddress());
            		
            } else {
            	// unicast usage
            	
            	// prepare a reusuable UDP socket
            	socket = new DatagramSocket(null);
            	socket.setReuseAddress(true);
            	
            	// bind to the source address
            	// (null is valid)
            	socket.bind(sourceSocketAddress);
            }
            
            // at this point, the socket will be bound
            
            synchronized (_lock) {
                if (_shutdown)
                    return;
                
                _socket = socket;
            }
            
            // socket it bound here, so fire 'ready' flag
            _callbackHandler.handle(_readyCallback, _callbackErrorHandler);
            
            _logger.info("A UDP socket is bound.");
            
            // start listening
            readLoop(socket);
            
        } finally {
            // always gracefully close the socket and invalidate the socket fields
            
            synchronized(_lock) {
                _socket = null;
            }
            
            Stream.safeClose(socket);
        }
    }

    /**
     * The reading loop will continually read or until shutdown.
     */
    private void readLoop(DatagramSocket socket) throws Exception {
        // set up the receive buffer and packet once
        byte[] recvBuffer = new byte[MAX_BUFFER_SIZE];
        DatagramPacket recvPacket = new DatagramPacket(recvBuffer, recvBuffer.length);
        
        while (!_shutdown) {
            // always reset the length before receiving
            recvPacket.setLength(recvBuffer.length);
            
            try {
                // receive packets...
                datagramRecvAndCount(socket, recvPacket);
                
                handleReceivedData(recvPacket);
                
            } catch (Exception exc) {
                // unless we're shutting down, ignore these exceptions
                // which could be 'no route to host' etc.
            }
        } // (while)
        
        // asked to be shutdown, will do so gracefully... 
    }
    
    /**
     * When a packet arrives; deals with request callbacks if necessary
     */
    private void handleReceivedData(DatagramPacket recvPacket) {
        InetAddress fromAddr = recvPacket.getAddress();
        String fromHost = fromAddr.getHostAddress();
        
        int fromPort = recvPacket.getPort();
        
        String from = fromHost + ":" + fromPort;
        
        String data = bufferToString(recvPacket.getData(), recvPacket.getLength());

        // ...then fire the 'received' callback next
        _callbackHandler.handle(_receivedCallback, from, data, _callbackErrorHandler);
    }
    
    /**
     * Sends data asynchronously (using 'dest' as destination)
     */
    public void send(String data) {
        queueSend(null, data);
    }
    
    /**
     * Sends data asynchronously (using 'dest' as destination)
     */
    public void sendTo(String dest, String data) {
        queueSend(dest, data);
    }
    
    /**
     * Safely queues a send-request to be processed by a thread-pool.
     * (returns immediately)
     */
    private void queueSend(String dest, String data) {
        byte[] buffer = stringToBuffer(data);

        synchronized (_sendQueue) {
            // immediately queue the send request
            _sendQueue.add(new QueueItem(dest, buffer, data));

            // kicking off thread-pool to process if necessary
            if (!_processing) {
                _processing = true;

                _threadPool.execute(new Runnable() {

                    @Override
                    public void run() {
                        _threadStateHandler.handle();
                        processQueue();
                    }

                });
            }
        }
    }

    /**
     * Continually processes the queue.
     */
    private void processQueue() {
        for(;;) {
            QueueItem qi;
            synchronized(_sendQueue) {
                qi = _sendQueue.poll();
                
                if (qi == null || _shutdown) {
                    _processing = false;
                    return;
                }
            }
            
            // 'qi' has a value
            doSend(qi.dest, qi.buffer, qi.origData);
        }
    }
    
    /**
     * Sends data
     * (assumes locked)
     * (no exceptions can be thrown)
     */
    private void doSend(String dest, byte[] buffer, String origData) {
        try {
            DatagramSocket socket = _socket;

            if (socket == null)
                return;
            
            InetSocketAddress addr;

            if (dest == null) {
                // use the 'dest' field, resolving if necessary
                if (_resolvedDest == null) {
                    addr = parseAndResolveAddress(_dest);
                } else {
                    addr = _resolvedDest;
                }
            } else {
                // attempt to resolve the once-off address
                addr = parseAndResolveAddress(dest);
            }

            _sendPacket.setSocketAddress(addr);
            _sendPacket.setData(buffer);
            _sendPacket.setLength(buffer.length);

            datagramSendAndCount(socket, _sendPacket);
            
            // call the 'sent' handler
            Handler.tryHandle(_sentCallback, origData, _callbackErrorHandler);
            
        } catch (Exception exc) {
            Handler.tryHandle(_callbackErrorHandler, exc);
        }
    }

    /**
     * Convenience method to send and count
     */
    private void datagramSendAndCount(DatagramSocket socket, DatagramPacket packet) throws IOException {
        socket.send(packet);
        
        _counterSendOps.incr();
        _counterSendRate.add(packet.getLength() * 8);
    }
    
    /**
     * Convenience method to receive and count.
     */
    private void datagramRecvAndCount(DatagramSocket socket, DatagramPacket packet) throws IOException {
        socket.receive(packet);
        
        _counterRecvOps.incr();
        _counterRecvRate.add(packet.getLength() * 8);
    }

    /**
     * Converts a direct char-to-byte conversion, automatically detecting whether UTF=8 conversion is required. 
     */
    private static byte[] stringToBuffer(String str) {
        int strLen = (str != null ? str.length() : 0);
        
        byte[] buffer = new byte[strLen];
        for (int a = 0; a < strLen; a++) {
            char c = str.charAt(a);
            
            // detect if UTF-8 is needed
            if (c > 0xff)
                return utf8StringToBuffer(str);
            
            buffer[a] = (byte) (c & 0xff);
        }

        return buffer;
    }
    
    /**
     * Uses UTF-8 encoding to convert a string to buffer.
     * (only to be used by 'stringToBuffer')
     */
    private static byte[] utf8StringToBuffer(String str) {
        return str.getBytes(UTF8Charset.instance());
    }
    
    /**
     * Assumes a UTF-8 encoded buffer, otherwise uses
     * raw conversion if anything less than \x08 is found which would be very unusual in a
     * UTF-8 string (0x09 is TAB)
     */
    private static String bufferToString(byte[] buffer, int len) {
        // scan the buffer for raw or binary bytes
        // this looks for a zero-byte
        
        for (int a = 0; a < len; a++) {
            byte b = buffer[a];
            
            if (b < 9)
                return binaryBufferToString(buffer, len);
        }
        
        // if we're here, there are no 'NUL' bytes, so put it through the UTF8 decoder.
        return new String(buffer, 0, len, UTF8Charset.instance());
    }
    
    /**
     * (only to be used by 'bufferToString')
     */
    private static String binaryBufferToString(byte[] buffer, int len) {
        char[] cBuffer = new char[len];

        for (int a = 0; a < len; a++) {
            cBuffer[a] = (char) (buffer[a] & 0xff);
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
            
            if (_startTimer != null)
                _startTimer.cancel();

            Stream.safeClose(_socket);

            _socket = null;
            
            // notify the connection and receive thread if it happens to be waiting
            _lock.notify();
        }
    }
    
    /**
     * Creates a resolved (if necessary) socket address.
     */
    private static InetSocketAddress parseAndResolveAddress(String address) {
        if (Strings.isNullOrEmpty(address))
            throw new IllegalArgumentException("No address was given.");

        int lastIndexOfPort = address.lastIndexOf(':');
        if (lastIndexOfPort < 1)
            throw new IllegalArgumentException("'address' is missing a port (no ':' found)");

        String hostPart = address.substring(0, lastIndexOfPort);
        String portPart = address.substring(lastIndexOfPort + 1);

        if (Strings.isNullOrEmpty(hostPart))
            throw new IllegalArgumentException("'host' is missing or empty.");
        
        if (Strings.isNullOrEmpty(portPart))
            throw new IllegalArgumentException("port is missing or empty.");

        int port;
        try {
            port = Integer.parseInt(portPart);
            
        } catch (Exception exc) {
            throw new IllegalArgumentException("port was invalid - '" + portPart + "'");
        }
        
        return new InetSocketAddress(hostPart, port);
    }

}
