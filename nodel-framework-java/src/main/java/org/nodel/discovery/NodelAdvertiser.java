package org.nodel.discovery;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.nodel.Exceptions;
import org.nodel.Handler;
import org.nodel.Threads;
import org.nodel.core.Nodel;
import org.nodel.discovery.NodelAutoDNS.ServiceItem;
import org.nodel.io.Stream;
import org.nodel.threading.TimerTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NodelAdvertiser {

    /**
     * (logging)
     */
    private Logger _logger;
    
    /**
     * General purpose lock for sendSocket, receiveSocket, enabled, recycle*flag
     */
    private Object _lock = new Object();    
    
    /**
     * (permanently latches false)
     */
    private volatile boolean _enabled = true;
    
    /**
     * The incoming queue.
     * (self locked)
     */
    private Queue<DatagramPacket> _queue = new LinkedList<DatagramPacket>();

    /**
     * Used to avoid unnecessary thread overlapping.
     */
    private boolean _isProcessingQueue = false;

    /**
     * Incoming queue processor runnable.
     */
    private Runnable _queueProcessor = new Runnable() {
        
        @Override
        public void run() {
            processIncomingPacketQueue();
        }
        
    };    

    /**
     * For multicast receives and unicast responses.
     * (locked around 'lock')
     */
    private MulticastSocket _socket;
    
    /**
     * (will never be null after being set)
     */
    private String _nodelAddress;

    /**
     * Will be a valid address.
     * (init in constructor)
     */
    private String _httpAddress;

    /**
     * The thread for receiving UDP
     */
    private Thread _thread;

    /**
     * The interface being managed by this discoverer.
     */
    private InetAddress _intf;
    
    /**
     * (see setter)
     */
    private Handler.F0<Collection<ServiceItem>> _servicesSnapshotProvider;
    
    /**
     * Provides a snapshot of services to advertise.
     * (returned collection must be fully thread safe)
     */
    public void setServicesSnapshotProvider(Handler.F0<Collection<ServiceItem>> value) {
        _servicesSnapshotProvider = value;
    }
    
    /**
     * Constructor
     */
    public NodelAdvertiser(InetAddress intf) {
        String friendlyName = intf.getHostAddress().replace('.', '_');
        _logger = LoggerFactory.getLogger(this.getClass().getName() + "." + friendlyName);

        _logger.info("Started");

        _intf = intf;
        
        // compose HTTP address once
        _httpAddress = "http://" + _intf.getHostAddress() + ":" + Nodel.getHTTPPort() + Nodel.getHTTPSuffix();

        // create the receiver thread and start it
        _thread = new Thread(new Runnable() {

            @Override
            public void run() {
                threadMain();
            }

        }, friendlyName + "_probe_resp_receiver");
        _thread.setDaemon(true);
    }

    /**
     * To start after handlers have been set.
     */
    public void start() {
        _thread.start();
    }

    /**
     * (thread entry-point)
     */
    private void threadMain() {
        final int TTL = 12; // this is fairly arbitrary 
        
        while (_enabled) {
            MulticastSocket socket = null;
            boolean timedOut = false;

            try {
             // Environment needed because MacOS handles multicast construction quite differently
                // socket = Environment.instance().createMulticastSocket(new InetSocketAddress(_intf, Discovery.MDNS_PORT));
                
                socket = new MulticastSocket(Discovery.MDNS_PORT);
                socket.setInterface(_intf);
                
                socket.setSoTimeout(5 * 60000);
                socket.setReuseAddress(true);
                socket.setTimeToLive(TTL);
                socket.joinGroup(Discovery.MDNS_GROUP);
                
                _logger.info("Multicast socket bound to this interface. port:{}, group:{}, TTL:{}", socket.getLocalPort(), Discovery.MDNS_GROUP, TTL);
                
                synchronized(_lock) {
                    if (!_enabled) {
                        Stream.safeClose(socket);
                        continue;
                    }
                    
                    _socket = socket;
                }

                while (_enabled) {
                    DatagramPacket dp = UDPPacketRecycleQueue.instance().getReadyToUsePacket();

                    try {
                        socket.receive(dp);
                        
                    } catch (SocketTimeoutException exc) {
                        timedOut = true;
                        
                    } catch (Exception exc) {
                        UDPPacketRecycleQueue.instance().returnPacket(dp);

                        throw exc;
                    }
                    
                    Discovery.countIncomingPacket(dp, true);
                    
                    enqueueForProcessing(dp);
                    
                } // (inner while)
                
            } catch (Exception exc) {
                // clean up regardless
                Stream.safeClose(socket);

                synchronized (_lock) {
                    if (!_enabled)
                        break;

                    if (timedOut)
                        _logger.info("Socket timed out after a long period of silence; this could be normal but will reinitialise socket regardless... msg:{}", exc.getMessage());
                    else
                        _logger.warn("Socket operation failed; this may occur during network topology transitions. Will retry / reinit regardless... msg:{}", exc.getMessage());

                    // stagger retry
                    Threads.waitOnSync(_lock, 10000);
                }
            }
        } // (outer while)
        
        _logger.info("This advertiser has shutdown; thread has run to completion.");
    } // (method)
    
    /**
     * Updates the Nodel address once the port becomes available and only once.
     * 
     */
    public String nodelAddress() {
        if (_nodelAddress == null) {
            int tcpPort = Nodel.getTCPPort();
            if (tcpPort > 0)
                _nodelAddress = String.format("tcp://%s:%s", _intf.getHostAddress(), tcpPort);
        }
        
        // (could still be null)
        return _nodelAddress;
    }

    /**
     * Place it in the queue and make it process if necessary
     */
    private void enqueueForProcessing(DatagramPacket dp) {
        synchronized (_queue) {
            _queue.add(dp);

            // avoid thrashing the thread-pool by checking if
            // the queue is being processed
            if (!_isProcessingQueue) {
                _isProcessingQueue = true;
                Discovery.threadPool().execute(_queueProcessor);
            }
        }
    }
    
    /**
     * Processes whatever's in the queue.
     */
    private void processIncomingPacketQueue() {
        while (_enabled) {
            DatagramPacket packet;
            
            synchronized(_queue) {
                if (_queue.size() <= 0) {
                    // nothing left, so clear flag and return
                    _isProcessingQueue = false;

                    return;
                }

                packet = _queue.remove();
            }
            
            DatagramPacket dp = packet;

            try {
                // parse packet
                NameServicesChannelMessage message = NameServicesChannelMessage.fromPacket(dp);

                // handle message
                this.handleIncomingMessage((InetSocketAddress) dp.getSocketAddress(), message);
                
            } catch (Exception exc) {
                if (!_enabled)
                    break;

                // log nested exception summary instead of stack-trace dump
                _logger.warn("Error while handling received packet from {}: {}", dp.getSocketAddress(), Exceptions.formatExceptionGraph(exc));
                
            } finally {
                // make sure the packet is returned
                UDPPacketRecycleQueue.instance().returnPacket(packet);
            }
        } // (while) 
    } // (method)

    /**
     * One responder per recipient at any stage.
     */
    private class Responder {
        
        public InetSocketAddress _recipient;
        
        private Iterator<ServiceItem> _serviceIterator;
        
        public Responder(InetSocketAddress recipient) {
            _recipient = recipient;

            // the service iterator
            _serviceIterator = _servicesSnapshotProvider.handle().iterator();
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
            
            int timeToWait = Discovery.random().nextInt(delay);
            
            Discovery.timerThread().schedule(new TimerTask() {

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
            message.addresses.add(nodelAddress());
            message.addresses.add(_httpAddress);
            
            // IO is involved so use thread-pool
            // (and only send if the 'present' list is not empty)
            
            if (message.present.size() > 0) {
                Discovery.threadPool().execute(new Runnable() {

                    @Override
                    public void run() {
                        sendResponse(_socket, _recipient, message);
                    }

                });
            }
            
            // do we need this completed in the very near future?
            // if so, space out by at least 333ms.
            if (_serviceIterator.hasNext()) {
                
                Discovery.timerThread().schedule(new TimerTask() {

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
    private void sendResponse(DatagramSocket socket, InetSocketAddress to, NameServicesChannelMessage message) {
        if (socket == null) {
            _logger.info("(cannot send any response; no socket is available)");
            return;
        }

        try {
            _logger.info("Responding to probe. message:{}", message);
            
            DatagramPacket dp = message.intoPacket(to); 
            socket.send(dp);
            
            Discovery.countOutgoingPacket(dp);

        } catch (IOException exc) {
            if (!_enabled)
                return;

            _logger.warn("Socket send unexpectedly failed.", exc);
        }
    }

    /**
     * The map of responders by recipient address.
     */
    private ConcurrentMap<SocketAddress, Responder> _responders = new ConcurrentHashMap<SocketAddress, Responder>();

    /**
     * Handles a complete packet from the socket.
     */
    private void handleIncomingMessage(InetSocketAddress from, NameServicesChannelMessage message) {
        // discovery request?
        if (message.discovery != null) {
            _logger.info("Received probe from {}. message:{}", from, message);
            // create a responder if one isn't already active

            if (nodelAddress() == null) {
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
    } // (method)

    /**
     * Permanently shuts down all related resources.
     */
    public void shutdown() {
        synchronized (_lock) {
            // clear flag
            _enabled = false;
            
            _lock.notifyAll();
        }

        Stream.safeClose(_socket);
    }

}
