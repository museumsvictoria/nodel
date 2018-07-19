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
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import org.nodel.Exceptions;
import org.nodel.Handler;
import org.nodel.Threads;
import org.nodel.core.Nodel;
import org.nodel.io.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Is responsible for the discovery of remote nodes through the use of multicast probing.
 */
public class NodelDiscoverer {
    
    /**
     * (init. in constructor)
     */
    private Logger _logger;
    
    /**
     * The thread to receive the unicast responses data.
     */
    private Thread _thread;
    
    /**
     * (permanently latches false)
     */
    private volatile boolean _enabled = true;
    
    /**
     * The incoming queue.
     * (self locked)
     */
    private Queue<DatagramPacket>_queue = new LinkedList<DatagramPacket>();
    
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
     * General purpose lock for sendSocket, receiveSocket, enabled, recycle*flag
     */
    private Object _lock = new Object();

    /**
     * For multicast sends and unicast receives on arbitrary port.
     * (locked around 'lock')
     */
    private MulticastSocket _socket;

    /**
     * The interface being managed by this discoverer.
     */
    private InetAddress _intf;
    
    /**
     * (see public setter)
     */
    private Handler.H1<NameServicesChannelMessage> _probeResponseHandler;
    
    /**
     * (as per title) 
     */
    public void setProbeResponseHandler(Handler.H1<NameServicesChannelMessage> value) {
        _probeResponseHandler = value;
    }
    
    /**
     * Constructor
     */
    public NodelDiscoverer(InetAddress intf) {
        String friendlyName = intf.getHostAddress().replace('.', '_');
        _logger = LoggerFactory.getLogger(this.getClass().getName() + "." + friendlyName);
        
        _logger.info("Started");
        
        _intf = intf;

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
                // socket = Environment.instance().createMulticastSocket(new InetSocketAddress(_intf, 0));
                
                socket = new MulticastSocket(0);
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
        
        _logger.info("This discoverer has shutdown; thread has run to completion.");
    } // (method)

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
                // Deep within dp.getSocketAddress() (used below and previously while logging), Java allowed 
                // an exception to be thrown so it's best we gracefully handle this condition up-front instead of 
                // crippling this thread
                int port = dp.getPort();
                if (port < 0 || port > 0xFFFF)
                    throw new RuntimeException("Packet may be invalid (port was " + port + ")");
                
                // parse packet
                NameServicesChannelMessage message = NameServicesChannelMessage.fromPacket(dp);

                // handle message
                this.handleIncomingMessage((InetSocketAddress) dp.getSocketAddress(), message);
                
            } catch (Exception exc) {
                if (!_enabled)
                    break;

                // log nested exception summary instead of stack-trace dump (skipping unimportant)
                if (dp.getAddress() != null && dp.getPort() >= 0)
                    _logger.warn("Error while handling received packet from host {}, port {}; details: {}", dp.getAddress(), dp.getPort(), Exceptions.formatExceptionGraph(exc));
                
            } finally {
                // make sure the packet is returned
                UDPPacketRecycleQueue.instance().returnPacket(packet);
            }
        } // (while) 
    } // (method)
    
    /**
     * Performs the probe asynchronously.
     */
    void sendProbe() {
        final NameServicesChannelMessage message = new NameServicesChannelMessage();
        
        message.agent = Nodel.getAgent();

        List<String> discoveryList = new ArrayList<String>(1);
        discoveryList.add("*");

        List<String> typesList = new ArrayList<String>(2);
        typesList.add("tcp");
        typesList.add("http");

        message.discovery = discoveryList;
        message.types = typesList;

        // I/O is involved so use a thread-pool
        Discovery.threadPool().execute(new Runnable() {

            @Override
            public void run() {
                doSendProbe(_socket, message);
            }

        });
    }
    
    /**
     * Sends the message to a recipient
     */
    private void doSendProbe(DatagramSocket socket, NameServicesChannelMessage message) {
        if (socket == null) {
            _logger.info("(cannot probe right now; no socket is available yet)");
            return;
        }

        try {
            _logger.info("Probing! message:{}", message);
            
            DatagramPacket dp = message.intoPacket(Discovery.GROUP_SOCKET_ADDRESS); 
            socket.send(dp);
            
            Discovery.countOutgoingPacket(dp);

        } catch (IOException exc) {
            if (!_enabled)
                return;

            _logger.warn("Socket send unexpectedly failed.", exc);
        }
    }
    
    /**
     * Handles a complete packet from the socket.
     */
    private void handleIncomingMessage(InetSocketAddress from, NameServicesChannelMessage message) {
        if (message.present != null && message.addresses != null) {
            _logger.info("Received probe response. message:{}", message);
            
            Handler.tryHandle(_probeResponseHandler, message);
        }
    }    

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
