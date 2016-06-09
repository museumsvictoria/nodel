package org.nodel.discovery;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import org.nodel.DateTimes;
import org.nodel.Exceptions;
import org.nodel.SimpleName;
import org.nodel.Threads;
import org.nodel.core.NodeAddress;
import org.nodel.core.Nodel;
import org.nodel.diagnostics.AtomicLongMeasurementProvider;
import org.nodel.io.Stream;
import org.nodel.io.UTF8Charset;
import org.nodel.reflection.Serialisation;
import org.nodel.threading.ThreadPool;
import org.nodel.threading.TimerTask;
import org.nodel.threading.Timers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Used for registering / unregistering nodes for discovery / lookup purposes. 
 */
public class NodelAutoDNS extends AutoDNS {
    
    /**
     * IPv4 multicast group
     */
    public static final String MDNS_GROUP = "224.0.0.252";

    /**
     * IPv6 multicast group (not used here but reserved)
     */
    public static final String MDNS_GROUP_IPV6 = "FF02::FB";
    
    /**
     * Multicast port
     */
    public static final int MDNS_PORT = 5354;
    
    /**
     * The period between probes when actively probing.
     * (millis)
     */
    private static final int PROBE_PERIOD = 45000;
    
    /**
	 * Expiry time (allow for at least one missing probe response)
	 */
	private static final long STALE_TIME = 2 * PROBE_PERIOD + 10000;
	
	/**
	 * How long to allow the multicast receiver to be silent. Will
	 * reinitialise socket as a precaution.
	 */
	private static final int SILENCE_TOLERANCE = 3 * PROBE_PERIOD + 10000;
    
    /**
     * (instrumentation)
     */
    private static AtomicLong s_multicastOutOps = new AtomicLong();
    
    /**
     * (instrumentation)
     */
    private static AtomicLongMeasurementProvider s_multicastOutOpsMeasurement = new AtomicLongMeasurementProvider(s_multicastOutOps);
    
    /**
     * Multicast in operations.
     */
    public static AtomicLongMeasurementProvider MulticastOutOpsMeasurement() {
        return s_multicastOutOpsMeasurement;
    }    
    
    /**
     * (instrumentation)
     */
    private static AtomicLong s_multicastOutData = new AtomicLong();
    
    /**
     * (instrumentation)
     */
    private static AtomicLongMeasurementProvider s_multicastOutDataMeasurement = new AtomicLongMeasurementProvider(s_multicastOutData);
    
    /**
     * Multicast out data.
     */
    public static AtomicLongMeasurementProvider MulticastOutDataMeasurement() {
        return s_multicastOutDataMeasurement;
    }    
    
    /**
     * (instrumentation)
     */
    private static AtomicLong s_multicastInOps = new AtomicLong();
    
    /**
     * (instrumentation)
     */
    private static AtomicLongMeasurementProvider s_multicastInOpsMeasurement = new AtomicLongMeasurementProvider(s_multicastInOps);
    
    /**
     * Multicast in operations.
     */
    public static AtomicLongMeasurementProvider MulticastInOpsMeasurement() {
        return s_multicastInOpsMeasurement;
    }
    
    /**
     * (instrumentation)
     */
    private static AtomicLong s_multicastInData = new AtomicLong();
    
    /**
     * (instrumentation)
     */
    private static AtomicLongMeasurementProvider s_multicastInDataMeasurement = new AtomicLongMeasurementProvider(s_multicastInData);
    
    /**
     * Multicast in data.
     */
    public static AtomicLongMeasurementProvider MulticastInDataMeasurement() {
        return s_multicastInDataMeasurement;
    }
    
    /**
     * (instrumentation)
     */
    private static AtomicLong s_unicastOutOps = new AtomicLong();
    
    /**
     * (instrumentation)
     */
    private static AtomicLongMeasurementProvider s_unicastOutOpsMeasurement = new AtomicLongMeasurementProvider(s_unicastOutOps);
    
    /**
     * Unicast in operations.
     */
    public static AtomicLongMeasurementProvider UnicastOutOpsMeasurement() {
        return s_unicastOutOpsMeasurement;
    }    
    
    /**
     * (instrumentation)
     */
    private static AtomicLong s_unicastOutData = new AtomicLong();
    
    /**
     * (instrumentation)
     */
    private static AtomicLongMeasurementProvider s_unicastOutDataMeasurement = new AtomicLongMeasurementProvider(s_unicastOutData);
    
    /**
     * Unicast out data.
     */
    public static AtomicLongMeasurementProvider UnicastOutDataMeasurement() {
        return s_unicastOutDataMeasurement;
    }    
    
    /**
     * (instrumentation)
     */
    private static AtomicLong s_unicastInOps = new AtomicLong();
    
    /**
     * (instrumentation)
     */
    private static AtomicLongMeasurementProvider s_unicastInOpsMeasurement = new AtomicLongMeasurementProvider(s_unicastInOps);
    
    /**
     * Unicast in operations.
     */
    public static AtomicLongMeasurementProvider UnicastInOpsMeasurement() {
        return s_unicastInOpsMeasurement;
    }
    
    /**
     * (instrumentation)
     */
    private static AtomicLong s_unicastInData = new AtomicLong();
    
    /**
     * (instrumentation)
     */
    private static AtomicLongMeasurementProvider s_unicastInDataMeasurement = new AtomicLongMeasurementProvider(s_unicastInData);
    
    /**
     * Unicast in data.
     */
    public static AtomicLongMeasurementProvider UnicastInDataMeasurement() {
        return s_unicastInDataMeasurement;
    }    
    
    
    /**
     * Returns '127.0.0.99' as a dummy address, normally when no network services are available.
     * Using '99' to know what's going on if it happens to come up.
     */
    private static InetAddress s_dummyInetAddress = parseNumericalIPAddress("127.0.0.99"); 

    /**
     * (logging)
     */
    private Logger _logger = LoggerFactory.getLogger(NodelAutoDNS.class);
    
    /**
     * General purpose lock / signal.
     */
    private Object _serverLock = new Object();

    /**
     * General purpose lock / signal.
     */    
    private Object _clientLock = new Object();
    
    /**
     * Used to micro-stagger responses.
     */
    private Random _random = new Random();
    
    /**
     * Thread-pool for IO operations.
     */
    private ThreadPool _threadPool = new ThreadPool("Discovery", 24);
    
    /**
     * This class' timer thread.
     */
    private Timers _timerThread = new Timers("Discovery");    
    
    /**
     * The thread to receive the multicast data.
     */
    private Thread _multicastHandlerThread;
    
    /**
     * The thread to receive the multicast data.
     */
    private Thread _unicastHandlerThread;
    
    /**
     * (for graceful clean up)
     */
    private List<TimerTask> _timers = new ArrayList<TimerTask>();
    
    /**
     * (permanently latches false)
     */
    private volatile boolean _enabled = true;

    /**
     * (used in 'incomingQueue')
     */
    private class QueueEntry {
        
        /**
         * For logging purposes
         */
        public final String source;
        
        /**
         * The packet
         */
        public final DatagramPacket packet;
        
        public QueueEntry(String source, DatagramPacket packet) {
            this.source = source;
            this.packet = packet;
        }
        
    }
    
    /**
     * The incoming queue.
     * (self locked)
     */
    private Queue<QueueEntry>_incomingQueue = new LinkedList<QueueEntry>();
    
    /**
     * Used to avoid unnecessary thread overlapping.
     */
    private boolean _isProcessingIncomingQueue = false;
    
    /**
     * Incoming queue processor runnable.
     */
    private Runnable _incomingQueueProcessor = new Runnable() {
    	
        @Override
        public void run() {
            processIncomingPacketQueue();
        }
        
    };    
    
    /**
     * Used in '_services'. 
     */
    private class ServiceItem {
        
        SimpleName _name;
        
        public ServiceItem(SimpleName name) {
            _name = name;
        }
        
    } // (class)
    
    /**
     * Holds the registered service items.
     */
    private ConcurrentMap<SimpleName, ServiceItem> _services = new ConcurrentHashMap<SimpleName, ServiceItem>();
    
    /**
     * Holds the collected advertisements.
     */
    private ConcurrentMap<SimpleName, AdvertisementInfo> _advertisements = new ConcurrentHashMap<SimpleName, AdvertisementInfo>();

    /**
     * (as an InetAddress; will never be null)
     */
    private InetAddress _group = parseNumericalIPAddress(MDNS_GROUP);
    
    /**
     * (as an InetSocketAddress (with port); will never be null)
     */
    private InetSocketAddress _groupSocketAddress = new InetSocketAddress(_group, MDNS_PORT);
    
    /**
     * Whether or not we're probing for client. It will probe on start up and then deactivate.
     * e.g. 'list' is called.
     * (nanos)
     */
    private AtomicLong _lastList = new AtomicLong(System.nanoTime());
    
    /**
     * The last time a probe occurred.
     * (nanos)
     */
    private AtomicLong _lastProbe = new AtomicLong(0);
    
    /**
     * Whether or not client resolution is being used. This affects whether polling
     * should take place. 
     * (one way switch)
     */
    private volatile boolean _usingResolution = false;
    
    /**
     * The time before probing can be suspended if there haven't been
     * any recent 'list' or 'resolve' operation. 5 minutes.
     * 
     * (millis)
     */
    private static final long LIST_ACTIVITY_PERIOD = 5 * 60 * 1000;
    
    /**
     * General purpose lock for sendSocket, receiveSocket, enabled, recycle*flag
     */
    private Object _lock = new Object();

    /**
     * For multicast sends and unicast receives on arbitrary port.
     * (locked around 'lock')
     */
    private MulticastSocket _sendSocket;
    
    /**
     * (socket label)
     */
    private final static String s_sendSocketLabel = "[multicastSender_unicastSenderReceiver]";    
    
    /**
     * (flag; locked around 'lock')
     */
    private boolean _recycleSender;
    
    /**
     * For multicast receives on the MDNS port.
     */
    private MulticastSocket _receiveSocket;
    
    /**
     * (socket label)
     */
    private static String s_receiveSocketlabel = "[multicastReceiver]";
    
    /**
     * (flag; locked around 'lock')
     */
    private boolean _recycleReceiver;
    
    /**
     * Used if direct "multicast" (using unicast) is enabled.
     */
    private DatagramSocket _hardLinksSocket;
    
    /**
     * (socket label)
     */
    private static String s_hardLinksSocketlabel = "[unicastHardLinksSenderReceiver]";
    
    /**
     * (will never be null after being set)
     */
    private String _nodelAddress;

    /**
     * Will be a valid address.
     */
    private String _httpAddress = "http://" + getLocalIPv4Address().getHostAddress() + ":" + Nodel.getHTTPPort() + Nodel.getHTTPSuffix();
    
    /**
     * Holds a safe list of resolved addresses and ports that should be "directly" multicast to (i.e. using unicast).
     * Can be used if multicasting is unreliable or inconvenient.
     * (is either null or has at least one element)
     */
    private List<InetSocketAddress> _hardLinksAddresses = composeHardLinksSocketAddresses();
    
    /**
     * Returns immediately.
     * 
     * (Private constructor)
     */
    private NodelAutoDNS() {
        // (no blocking code can be here)
        
        // create the receiver thread and start it
        _multicastHandlerThread = new Thread(new Runnable() {

            @Override
            public void run() {
                multicastReceiverThreadMain();
            }

        }, "autodns_multicastreceiver");
        _multicastHandlerThread.setDaemon(true);
        _multicastHandlerThread.start();

        // create the receiver thread and start it
        _unicastHandlerThread = new Thread(new Runnable() {

            @Override
            public void run() {
                unicastReceiverThreadMain();
            }

        }, "autodns_unicastreceiver");
        _unicastHandlerThread.setDaemon(true);
        _unicastHandlerThread.start();
        
        // kick off the client prober to start
        // after 10s - 15s (randomly chosen)
        _timers.add(_timerThread.schedule(new TimerTask() {

            @Override
            public void run() {
                handleProbeTimer();
            }

        }, (long) (10000 + _random.nextDouble() * 5000), PROBE_PERIOD));

        // kick off the cleanup tasks timer
        _timers.add(_timerThread.schedule(new TimerTask() {

            @Override
            public void run() {
                handleCleanupTimer();
            }

        }, 60000, 60000));;

        // monitor interface changes after 10s delay, then every 2 mins
        _timers.add(_timerThread.schedule(new TimerTask() {

            @Override
            public void run() {
                handleInterfaceCheck();
            }

        }, 10000, 120000));

        _logger.info("Auto discovery threads and timers started. probePeriod:{}, stalePeriodAllowed:{}",
                DateTimes.formatShortDuration(PROBE_PERIOD), DateTimes.formatShortDuration(STALE_TIME));
    } // (init)

    /**
     * Creates a new socket, cleaning up if anything goes wrong in the process
     */
    private MulticastSocket createMulticastSocket(String label, InetAddress intf, int port) throws Exception {
        MulticastSocket socket = null;

        try {
            _logger.info("Preparing {} socket. interface:{}, port:{}, group:{}", 
                    label, (intf == null ? "default" : intf), (port == 0 ? "any" : port), _group);
            
            // in previous versions the interface was selected using constructor instead of 'socket.setInterface(intf)' 
            // but that uncovered side-effect in OSX which caused 'cannot assign address' Java bug
            
            socket = new MulticastSocket(port); // (port '0' means any port)
            
            if (intf != null)
                socket.setInterface(intf);

            // join the multicast group
            socket.joinGroup(_group);

            _logger.info("{} ready. localAddr:{}", label, socket.getLocalSocketAddress());

            return socket;

        } catch (Exception exc) {
            Stream.safeClose(socket);

            throw exc;
        }
    }
    
    /**
     * (thread entry-point)
     */
    private void multicastReceiverThreadMain() {
        while (_enabled) {
            MulticastSocket socket = null;

            try {
                synchronized(_lock) {
                    // clear flag regardless
                    _recycleReceiver = false;
                }
                
                socket = createMulticastSocket(s_receiveSocketlabel, s_interface, MDNS_PORT);
                
                synchronized(_lock) {
                    // make sure not flagged since reset
                    if (_recycleReceiver) {
                        Stream.safeClose(socket);
                        continue;
                    }
                    
                    _receiveSocket = socket;
                }

                while (_enabled) {
                    DatagramPacket dp = UDPPacketRecycleQueue.instance().getReadyToUsePacket();

                    // ('returnPacket' will be called in 'catch' or later after use in thread-pool)

                    try {
                        socket.receive(dp);

                    } catch (Exception exc) {
                        UDPPacketRecycleQueue.instance().returnPacket(dp);

                        throw exc;
                    }
                    
                    InetAddress recvAddr = dp.getAddress();

                    if (recvAddr.isMulticastAddress()) {
                        s_multicastInData.addAndGet(dp.getLength());
                        s_multicastInOps.incrementAndGet();
                    } else {
                        s_unicastInData.addAndGet(dp.getLength());
                        s_unicastInOps.incrementAndGet();
                    }
                    
                    // check whether it's external i.e. completely different IP address
                    // (local multicasting would almost always be reliable)
                    
                    MulticastSocket otherLocal = _sendSocket;
                    boolean isLocal = (otherLocal != null && recvAddr.equals(otherLocal.getLocalAddress()));

                    // update counter which is used to detect silence
                    if (!isLocal)
                        _lastExternalMulticastPacket = System.nanoTime();
                    
                    enqueueForProcessing(dp, s_receiveSocketlabel);
                    
                } // (inner while)

            } catch (Exception exc) {
                // (timeouts and general IO problems)
                
                // clean up regardless
                Stream.safeClose(socket);

                synchronized (_lock) {
                    if (!_enabled)
                        break;

                    if (_recycleReceiver)
                        _logger.info(s_receiveSocketlabel + " was gracefully closed. Will reinitialise...");
                    else
                        _logger.warn(s_receiveSocketlabel + " receive failed; this may be a transitional condition. Will reinitialise... message was '" + exc.toString() + "'");
                    
                    // set flag
                    _recycleSender = true;
                    // "signal" other thread
                    Stream.safeClose(_sendSocket);
                    
                    // stagger retry
                    Threads.waitOnSync(_lock, 333);
                }
            }
        } // (outer while)

        _logger.info("This thread has run to completion.");
    } // (method)

    /**
     * (thread entry-point)
     */
    private void unicastReceiverThreadMain() {
        while (_enabled) {
            MulticastSocket socket = null;

            try {
                synchronized (_lock) {
                    // clear flag regardless
                    _recycleSender = false;
                }

                socket = createMulticastSocket(s_sendSocketLabel, s_interface, 0);

                // make sure a recycle request hasn't since occurred
                synchronized (_lock) {
                    if (_recycleSender) {
                        Stream.safeClose(socket);
                        continue;
                    }

                    _sendSocket = socket;
                }

                while (_enabled) {
                    DatagramPacket dp = UDPPacketRecycleQueue.instance().getReadyToUsePacket();

                    try {
                        socket.receive(dp);

                    } catch (Exception exc) {
                        UDPPacketRecycleQueue.instance().returnPacket(dp);

                        throw exc;
                    }

                    if (dp.getAddress().isMulticastAddress()) {
                        s_multicastInData.addAndGet(dp.getLength());
                        s_multicastInOps.incrementAndGet();
                    } else {
                        s_unicastInData.addAndGet(dp.getLength());
                        s_unicastInOps.incrementAndGet();
                    }
                    
                    enqueueForProcessing(dp, s_sendSocketLabel);

                } // (inner while)
                
            } catch (Exception exc) {
                boolean wasClosed = (socket != null && socket.isClosed());
                
                // clean up regardless
                Stream.safeClose(socket);

                synchronized (_lock) {
                    if (!_enabled)
                        break;

                    if (wasClosed)
                        _logger.info(s_sendSocketLabel + " was signalled to gracefully close. Will reinitialise...");
                    else
                        _logger.warn(s_sendSocketLabel + " receive failed; will reinitialise...", exc);

                    // stagger retry
                    Threads.waitOnSync(_lock, 333);
                }
            }
        } // (outer while)
        
        _logger.info("This thread has run to completion.");
    }

    private void enqueueForProcessing(DatagramPacket dp, String label) {
        // place it in the queue and make it process if necessary
        synchronized (_incomingQueue) {
            QueueEntry qe = new QueueEntry(label, dp);
            _incomingQueue.add(qe);

            // kick off the other thread to process the queue
            // (otherwise the thread will already be processing the queue)
            if (!_isProcessingIncomingQueue) {
                _isProcessingIncomingQueue = true;
                _threadPool.execute(_incomingQueueProcessor);
            }
        }
    }    
    
    /**
     * Processes whatever's in the queue.
     */
    private void processIncomingPacketQueue() {
        while(_enabled) {
            QueueEntry entry;
            
            synchronized(_incomingQueue) {
                if (_incomingQueue.size() <= 0) {
                    // nothing left, so clear flag and return
                    _isProcessingIncomingQueue = false;

                    return;
                }

                entry = _incomingQueue.remove();
            }
            
            DatagramPacket dp = entry.packet;
            String source = entry.source;

            try {
                // parse packet
                NameServicesChannelMessage message = this.parsePacket(dp);

                // handle message
                this.handleIncomingMessage(source, (InetSocketAddress) dp.getSocketAddress(), message);
                
            } catch (Exception exc) {
                if (!_enabled)
                    break;

                // log nested exception summary instead of stack-trace dump
                _logger.warn("{} while handling received packet from {}: {}", source, dp.getSocketAddress(), Exceptions.formatExceptionGraph(exc));
                
            } finally {
                // make sure the packet is returned
            	UDPPacketRecycleQueue.instance().returnPacket(entry.packet);
            }
        } // (while) 
    }
    
    private boolean _suppressProbeLog = false;

    /**
     * The last time an external multicast packet was received.
     * (nano-based)
     */
    private volatile long _lastExternalMulticastPacket = System.nanoTime(); 
    
    /**
     * The client timer; determines whether probing is actually necessary
     * (timer entry-point)
     */
    private void handleProbeTimer() {
        if (_usingResolution) {
            // client names are being resolved, so stay probing
            _suppressProbeLog = false;
            sendProbe();
            
        } else {
            // the time difference in millis
            long listDiff = (System.nanoTime() - _lastList.get()) / 1000000L;

            if (listDiff < LIST_ACTIVITY_PERIOD) {
                _suppressProbeLog = false;
                sendProbe();
                
            } else {
                if (!_suppressProbeLog) {
                    _logger.info("Probing is paused because it has been more than {} since a 'list' or 'resolve' (total {}).", 
                            DateTimes.formatShortDuration(LIST_ACTIVITY_PERIOD), DateTimes.formatShortDuration(listDiff));
                }

                _suppressProbeLog = true;
            }
        }
    }
    
	/**
	 * Performs the probe asynchronously.
	 */
    private void sendProbe() {
		final NameServicesChannelMessage message = new NameServicesChannelMessage();
		
		message.agent = Nodel.getAgent();

		List<String> discoveryList = new ArrayList<String>(1);
		discoveryList.add("*");

		List<String> typesList = new ArrayList<String>(2);
		typesList.add("tcp");
		typesList.add("http");

		message.discovery = discoveryList;
		message.types = typesList;

		_lastProbe.set(System.nanoTime());

		// IO is involved so use a thread-pool
		_threadPool.execute(new Runnable() {

            @Override
            public void run() {
                sendMessage(_sendSocket, s_sendSocketLabel, _groupSocketAddress, message);

                // check if hard links (direct "multicasting") are enabled for some hosts
                if (_hardLinksAddresses != null && _hardLinksSocket != null) {
                    for (InetSocketAddress socketAddress : _hardLinksAddresses) {
                        sendMessage(_hardLinksSocket, s_hardLinksSocketlabel, socketAddress, message);
                    }
                }
            }

        });
	}

	/**
     * Handle clean-up tasks
     * (timer entry-point)
     */
    private void handleCleanupTimer() {
        long currentTime = System.nanoTime();

        reapStaleRecords(currentTime);
        
        recycleReceiverIfNecessary(currentTime);
    } // (method)  
    
    /**
     * Checks for stale records and removes them.
     */
    private void reapStaleRecords(long currentTime) {
        LinkedList<AdvertisementInfo> toRemove = new LinkedList<AdvertisementInfo>();

        synchronized (_clientLock) {
            for (AdvertisementInfo adInfo : _advertisements.values()) {
                long timeDiff = (currentTime / 1000000) - adInfo.timeStamp;

                if (timeDiff > STALE_TIME)
                    toRemove.add(adInfo);
            }

            // reap if necessary
            if (toRemove.size() > 0) {
                StringBuilder sb = new StringBuilder();

                for (AdvertisementInfo adInfo : toRemove) {
                    if (sb.length() > 0)
                        sb.append(",");

                    _advertisements.remove(adInfo.name);
                    sb.append(adInfo.name);
                }

                _logger.info("{} stale record{} removed. [{}]", 
                        toRemove.size(), toRemove.size() == 1 ? " was" : "s were", sb.toString());
            }
        }
    } // (method)
    
    /**
     * Check if the main receiver has been silent for some time so
     * recycle the socket for best resilience.
     */
    private void recycleReceiverIfNecessary(long currentTime) {
        long timeDiff = (currentTime - _lastExternalMulticastPacket) / 1000000;
        
        if (timeDiff > SILENCE_TOLERANCE) {
            _logger.info("There appears to be external silence on the multicast receiver (this may or may not be expected); the socket will be recycled to ensure resilience.");
            
            synchronized(_lock) {
                // recycle receiver which will in turn recycle sender
                _recycleReceiver = true;
                
                // "signal" the other thread
                Stream.safeClose(_receiveSocket);
            }
        }
    }    

    /**
     * Parses the incoming packet.
     */
    private NameServicesChannelMessage parsePacket(DatagramPacket dp) {
        String packetString = new String(dp.getData(), 0, dp.getLength(), UTF8Charset.instance());

        return (NameServicesChannelMessage) Serialisation.coerceFromJSON(NameServicesChannelMessage.class, packetString);
    }
    
    /**
     * One responder per recipient at any stage.
     */
    private class Responder {
        
        public InetSocketAddress _recipient;
        
        public LinkedList<ServiceItem> _serviceSet;

        private Iterator<ServiceItem> _serviceIterator;
        
        public Responder(InetSocketAddress recipient) {
            _recipient = recipient;
            
            synchronized (_serverLock) {
                // get a snap-shot of the service values
                _serviceSet = new LinkedList<ServiceItem>(_services.values());
            }
            
            // the service iterator
            _serviceIterator = _serviceSet.iterator();
        }
        
        /**
         * Starts the responder. Wait a random amount of time to before
         * actually sending anything. Staggered responses assists with receiver
         * buffer management by the remote receivers.
         */
        public void start(int randomResponseDelay) {
        	// use a random minimum delay of 333ms
        	int delay = 333;
        	
        	if (randomResponseDelay > 333)
        		delay = randomResponseDelay;
        	
            int timeToWait = _random.nextInt(delay);
            
            _timerThread.schedule(new TimerTask() {

                @Override
                public void run() {
                    completeResponse();
                }

            }, timeToWait);
        }
        
        /**
         * Complete the necessary response, also a timer entry-point. 
         */
        private void completeResponse() {
            // prepare a message
            final NameServicesChannelMessage message = new NameServicesChannelMessage();
            message.present = new ArrayList<String>();
            
            // try keep the packets relatively small by roughly guessing how much space 
            // its JSON advertisement packet might take up
            
            // account for "present"... 
            // and "addresses":["tcp://136.154.27.100:65017","http://136.154.27.100:8085/index.htm?node=%NODE%"]
            // so start off with approx. 110 chars
            long roughTotalSize = 110;
            
            while(_serviceIterator.hasNext()) {
                ServiceItem si = _serviceIterator.next();
                
                String name = si._name.getOriginalName();
                
                // calculate size to be the name, two inverted-commas, and a comma and a possible space in between. 
                int size = name.length() + 4;
                roughTotalSize += size;
                
                message.present.add(name);
                
                // make sure we're not going anywhere near UDP MTU (64K),
                // in fact, squeeze them into packets similar to size of Ethernet MTU (~1400 MTU)
                if (roughTotalSize > 1200)
                    break;
            } // (while)
            
            message.addresses = new ArrayList<String>();
            message.addresses.add(_nodelAddress);
            message.addresses.add(_httpAddress);
            
            // IO is involved so use thread-pool
            // (and only send if the 'present' list is not empty)
            
            if (message.present.size() > 0) {
                _threadPool.execute(new Runnable() {

                    @Override
                    public void run() {
                        sendMessage(_sendSocket, s_sendSocketLabel, _recipient, message);
                    }

                });
            }
            
            // do we need this completed in the very near future?
            // if so, space out by at least 333ms.
            if (_serviceIterator.hasNext()) {
                
                _timerThread.schedule(new TimerTask() {

                    @Override
                    public void run() {
                        completeResponse();
                    }
                    
                }, 333);
            } else {
                synchronized(_responders) {
                    _responders.remove(_recipient);
                }
            }
        } // (method)
        
    } // (class)
    
    /**
     * Sends the message to a recipient 
     */
    private void sendMessage(DatagramSocket socket, String label, InetSocketAddress to, NameServicesChannelMessage message) {
        if (isSameSocketAddress(socket, to))
            _logger.info("{} sending message. to=self, message={}", label, message);
        else
            _logger.info("{} sending message. to={}, message={}", label, to, message);
        
        if (socket == null) {
        	_logger.info("{} is not available yet; ignoring send request.", label);
        	return;
        }
        
        // convert into bytes
        String json = Serialisation.serialise(message);
        byte[] bytes = json.getBytes(UTF8Charset.instance());
        
        DatagramPacket packet = new DatagramPacket(bytes, bytes.length);
        packet.setSocketAddress(to);
        
        try {
            socket.send(packet);
            
            if (to.getAddress().isMulticastAddress()) {
                s_multicastOutData.addAndGet(bytes.length);
                s_multicastOutOps.incrementAndGet();
            } else {
                s_unicastOutData.addAndGet(bytes.length);
                s_unicastOutOps.incrementAndGet();
            }

        } catch (IOException exc) {
            if (!_enabled)
                return;
            
            if (socket.isClosed())
                _logger.info(s_sendSocketLabel + " send() ignored as socket is being recycled.");
            else
                _logger.warn(s_sendSocketLabel + " send() failed. ", exc);
        }
    } // (method)
    
    /**
     * The map of responders by recipient address.
     */
    private ConcurrentMap<SocketAddress, Responder> _responders = new ConcurrentHashMap<SocketAddress, Responder>();

    /**
     * Handles a complete packet from the socket.
     */
    private void handleIncomingMessage(String label, InetSocketAddress from, NameServicesChannelMessage message) {
        if (isSameSocketAddress(_sendSocket, from))
            _logger.info("{} message arrived. from=self, message={}", label, message);
        else
            _logger.info("{} message arrived. from={}, message={}", label, from, message);
        
        // discovery request?
        if (message.discovery != null) {
            // create a responder if one isn't already active
            
            if (_nodelAddress == null) {
                _logger.info("(will not respond; nodel server port still not available)");
                
                return;
            }

            synchronized (_responders) {
                if (!_responders.containsKey(from)) {
                    Responder responder = new Responder(from);
                    _responders.put(from, responder);

                    int delay = message.delay == null ? 0 : message.delay.intValue();
                    
                    responder.start(delay);
                }
            }
        }
        
        else if (message.present != null && message.addresses != null) {
            for (String name : message.present ) {
                synchronized (_clientLock) {
                    SimpleName node = new SimpleName(name);
                    AdvertisementInfo ad = _advertisements.get(node);
                    if (ad == null) {
                        ad = new AdvertisementInfo();
                        ad.name = node;
                        _advertisements.put(node, ad);
                    }
                    
                    // refresh the time stamp and update the address
                    ad.timeStamp = System.nanoTime() / 1000000;
                    ad.addresses = message.addresses;
                }
            }
        }
        
    } // (method)

    @Override
    public NodeAddress resolveNodeAddress(SimpleName node) {
    	// indicate client resolution is being used
    	_usingResolution = true;
    	
        AdvertisementInfo adInfo = _advertisements.get(node);
        if(adInfo != null) {
            Collection<String> addresses = adInfo.addresses;
            
            for (String address : addresses) {
                try {
                    if (address == null || !address.startsWith("tcp://"))
                        continue;

                    int indexOfPort = address.lastIndexOf(':');
                    if (indexOfPort < 0 || indexOfPort >= address.length() - 2)
                        continue;

                    String addressPart = address.substring(6, indexOfPort);

                    String portStr = address.substring(indexOfPort + 1);

                    int port = Integer.parseInt(portStr);

                    NodeAddress nodeAddress = NodeAddress.create(addressPart, port);

                    return nodeAddress;

                } catch (Exception exc) {
                    _logger.info("'{}' node resolved to a bad address - '{}'; ignoring.", node, address);

                    return null;
                }
            }
        }
        
        return null;
    }

    @Override
    public void registerService(SimpleName node) {
        synchronized (_serverLock) {
            if (_services.containsKey(node))
                throw new IllegalStateException(node + " is already being advertised.");

            ServiceItem si = new ServiceItem(node);

            _services.put(node, si);
        }
    } // (method)
    
    /**
     * Used to monitor address changes on the the interface.
     */
    private void handleInterfaceCheck() {
        try {
            int port = super.getAdvertisementPort();
            if (port < 0) {
                // can't compose a nodel address yet
                _logger.info("(nodel server port still not available; will wait.)");
                
                return;
            }
            
            InetAddress localIPv4Address = getLocalIPv4Address();
            String nodelAddress = "tcp://" + localIPv4Address.getHostAddress() + ":" + port;

            if (nodelAddress.equals(_nodelAddress))
                // nothing to do
                return;

            // the address has changed so should update advertisements
            _logger.info("An address change has been detected. previous={}, current={}", _nodelAddress, nodelAddress);

            _nodelAddress = "tcp://" + localIPv4Address.getHostAddress() + ":" + port;
            _httpAddress = "http://" + localIPv4Address.getHostAddress() + ":" + Nodel.getHTTPPort() + Nodel.getHTTPSuffix();
            
            Nodel.updateHTTPAddress(_httpAddress);

            synchronized(_lock) {
                // recycle receiver which will in turn recycle sender
                _recycleReceiver = true;
                
                // "signal" thread
                Stream.safeClose(_receiveSocket);
            }
            
        } catch (Exception exc) {
            _logger.warn("'handleInterfaceCheck' did not complete cleanly; ignoring for now.", exc);
        }
    } // (method)

    @Override
    public void unregisterService(SimpleName node) {
        synchronized(_serverLock) {
            if (!_services.containsKey(node))
                throw new IllegalStateException(node + " is not advertised anyway.");

            _services.remove(node);
        }        
    }    

    @Override
    public Collection<AdvertisementInfo> list() {
    	long now = System.nanoTime();
    	
    	_lastList.set(now);
    	
    	// check how long it has been since the last probe (millis)
        long timeSinceProbe = (now - _lastProbe.get()) / 1000000L;

        if (timeSinceProbe > LIST_ACTIVITY_PERIOD)
            sendProbe();

        // create snap-shot
        List<AdvertisementInfo> ads = new ArrayList<AdvertisementInfo>(_advertisements.size());
        
        synchronized (_clientLock) {
            ads.addAll(_advertisements.values());
        }
        
        return ads;
    }
    
    @Override
    public AdvertisementInfo resolve(SimpleName node) {
        // indicate client resolution is being used
        _usingResolution = true;
        
        return _advertisements.get(node);
    }

    /**
     * Permanently shuts down all related resources.
     */
    @Override
    public void close() throws IOException {
        // clear flag
        _enabled = false;
        
        // release timers
        for (TimerTask timer : _timers)
            timer.cancel();

        Stream.safeClose(_sendSocket, _receiveSocket, _hardLinksSocket);
    }
    
    /**
     * Creates or returns the shared instance.
     */
    public static AutoDNS create() {
        return Instance.INSTANCE;
    }
    
    /**
     * (singleton, thread-safe, non-blocking)
     */
    private static class Instance {
        
        private static final NodelAutoDNS INSTANCE = new NodelAutoDNS();
        
    }
    
    /**
     * Returns the singleton instance of this class.
     */
    public static NodelAutoDNS instance() {
        return Instance.INSTANCE;
    }
    
    /**
     * Returns the first "sensible" IPv4 address.
     */
    public static InetAddress getLocalIPv4Address() {
        InetAddress selectedInterface = s_interface;
        
        // if an interface is being used and IP addresses don't necessarily match, use this candidate anyway
        InetAddress candidate = null;

        try {
            Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
            if (en == null)
                return s_dummyInetAddress;

            while (en.hasMoreElements()) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                        
                        // is an IPv4 address that is not a loopback address
                        
                        // if interface binding isn't being used, return the found address
                        if (selectedInterface == null) {
                            return inetAddress;
                        
                        } else {
                            // make first one candidate
                            if (candidate != null)
                                candidate = inetAddress;
                            
                            if (selectedInterface.equals(inetAddress))
                                return inetAddress;
                            
                            // otherwise keep iterating, will return the candidate at the end
                        }
                    }
                }
            }
        } catch (IOException exc) {
            // ignore
        }
        
        if (candidate != null)
            return candidate;
        
        return s_dummyInetAddress;
    }
    
    /**
     * Parses a dotted numerical IP address without throwing any exceptions.
     * (convenience function)
     */
    private static InetAddress parseNumericalIPAddress(String dottedNumerical) {
        try {
            return InetAddress.getByName(dottedNumerical);
            
        } catch (Exception exc) {
            throw new Error("Failed to resolve dotted numerical address - " + dottedNumerical);
        }
    }
    
    /**
     * Safely returns true if a packet has the same address and a socket. Used to determine its own socket.
     */
    private static boolean isSameSocketAddress(DatagramSocket socket, InetSocketAddress addr) {
        if (socket == null || addr == null)
            return false;
        
        SocketAddress socketAddr = socket.getLocalSocketAddress();
        
        return socketAddr != null && socketAddr.equals(addr);
    }
    
    /**
     * Turns the list of addresses into "resolved" InetSocketAddresses.
     * (should only be called once)
     */
    private List<InetSocketAddress> composeHardLinksSocketAddresses() {
        List<InetAddress> addresses = Nodel.getHardLinksAddresses();
        
        if (addresses.size() <= 0)
            return null;
        
        List<InetSocketAddress> socketAddresses = new ArrayList<InetSocketAddress>();
        for (InetAddress address : addresses)
            socketAddresses.add(new InetSocketAddress(address, MDNS_PORT));

        // at least one address is enabled, so initialise a general purpose UDP socket and
        // receiver thread.
        
        Thread thread = new Thread(new Runnable() {

            @Override
            public void run() {
                hardLinksReceiverThreadMain();
            }

        }, s_hardLinksSocketlabel);
        thread.setDaemon(true);
        thread.start();

        return socketAddresses;
    }

    /**
     * (thread entry-point)
     */
	private void hardLinksReceiverThreadMain() {
		_logger.info("Instructed to use hardlinks. address:{}", _hardLinksAddresses);

        DatagramSocket socket = null;
        try {
            // initialise a UDP socket on an arbitrary port
            _hardLinksSocket = new DatagramSocket();

            socket = _hardLinksSocket;

            while (_enabled) {
                DatagramPacket dp = UDPPacketRecycleQueue.instance().getReadyToUsePacket();

                // ('returnPacket' will be called in 'catch' or later after use in thread-pool)

                try {
                    socket.receive(dp);

                    s_unicastInData.addAndGet(dp.getLength());
                    s_unicastInOps.incrementAndGet();

                    enqueueForProcessing(dp, s_hardLinksSocketlabel);
                    
                } catch (Exception exc) {
                    UDPPacketRecycleQueue.instance().returnPacket(dp);

                    // ignore
                }
            } // (while)

        } catch (Exception exc) {
            _logger.warn("Failed to initialise [" + s_hardLinksSocketlabel + "] socket.", exc);
        } finally {
            _logger.info("[" + s_hardLinksSocketlabel + "] thread run to completion.");
            
            // close for good measure
            Stream.safeClose(socket);
        }
    }

} // (class)
