package org.nodel.core;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.LinkedList;

import org.nodel.diagnostics.CountableInputStream;
import org.nodel.diagnostics.CountableOutputStream;
import org.nodel.diagnostics.Diagnostics;
import org.nodel.diagnostics.LongSharableMeasurementProvider;
import org.nodel.diagnostics.SharableMeasurementProvider;
import org.nodel.reflection.Serialisation;

/**
 * Manages one incoming TCP socket.
 */
public class TCPChannelServer extends ChannelServer {
    
    /**
     * The underlying socket.
     */
    private Socket _socket;
    
    /**
     * For reading from the socket.
     */
    private JSONStreamReader _reader;
    
    /**
     * For writing to the socket.
     */
    private OutputStreamWriter _writer;
    
    /**
     * The thread object. 
     * (initialised in constructor, will be null if shutdown.) 
     * (not thread safe)
     */
    private Thread _thread;
    
    /**
     * (diagnostics)
     */
    private static SharableMeasurementProvider s_dataInCounter = new LongSharableMeasurementProvider();
    
    /**
     * (diagnostics)
     */    
    private static SharableMeasurementProvider s_dataInOpsCounter = new LongSharableMeasurementProvider();
    
    /**
     * (diagnostics)
     */    
    private static SharableMeasurementProvider s_dataOutCounter = new LongSharableMeasurementProvider();
    
    /**
     * (diagnostics)
     */    
    private static SharableMeasurementProvider s_dataOutOpsCounter = new LongSharableMeasurementProvider();
    
    /**
     * (diagnostics)
     */
    static {
        Diagnostics.shared().registerCounter("Nodel TCP server channels.Receives", s_dataInOpsCounter, true);
        Diagnostics.shared().registerCounter("Nodel TCP server channels.Sends", s_dataOutOpsCounter, true);
    }
    
    /**
     * Holds the outgoing message queue that is process by a separate thread.
     */
    private LinkedList<ChannelMessage> _outgoingMessageQueue = new LinkedList<ChannelMessage>();
    
    /**
     * The Nodel channel end-point. 
     */
    public TCPChannelServer(NodelServers nodelServer, Socket socket) {
        super(nodelServer);
        
        if (socket == null)
            throw new IllegalArgumentException("Socket cannot be null.");
        
        _socket = socket;
        
        // initialise the thread
        _thread = new Thread(new Runnable() {

            @Override
            public void run() {
                TCPChannelServer.this.run();
            }

        });
        _thread.setName(String.format("channel_server_%d", this._instance));
        _thread.setDaemon(true);
    } // (constructor)

    /**
     * Starts processing.
     * 
     * (may briefly block)
     */
    public void start() {
        Exception failureHandlerException = null;
        
        synchronized (this._signal) {
            if (this._enabled)
                throw new IllegalStateException("Already started.");

            if (_thread == null)
                throw new IllegalStateException("Already shutdown.");
            
            // kick of the message queue handler
            Thread outgoingMessageQueueThread = new Thread(new Runnable() {
                
                @Override
                public void run() {
                    processOutgoingMessageQueue();
                }
                
            });
            outgoingMessageQueueThread.setName(String.format("tcp_channel_server_queue_%d", this._instance));
            outgoingMessageQueueThread.setDaemon(true);
            outgoingMessageQueueThread.start();

            try {
                CountableInputStream input = new CountableInputStream(_socket.getInputStream(), s_dataInOpsCounter, s_dataInCounter);
                CountableOutputStream output = new CountableOutputStream(_socket.getOutputStream(), s_dataOutOpsCounter, s_dataOutCounter);
                
                // need to buffer in the input since arriving via socket.
                _reader = new JSONStreamReader(new BufferedReader(new InputStreamReader(input)));

                // no need to buffer the output since higher layers work
                // at 'message' level, not byte.
                _writer = new OutputStreamWriter(output);

                _thread.start();

                this._enabled = true;

                // wait for new thread to fire up...
                try {
                    this._signal.wait();
                } catch (InterruptedException exc) {
                    // (can safely ignore)
                }
                
                this._logger.info("Started.");
                
            } catch (IOException exc) {
                if (!this._enabled)
                    return;

                this._logger.warn("A failure occurred.", exc);

                cleanup();
                
                failureHandlerException = exc;
            }
        } // (sync.)

        if (failureHandlerException != null && this._onFailure != null)
            this._onFailure.handle(failureHandlerException);
    } // (method)
    
    /**
     * Processes the message queue.
     * (thread entry-point)
     */
    private void processOutgoingMessageQueue() {
        try {
            for (;;) {
                ChannelMessage message = null;
                synchronized (this._signal) {
                    while (this._enabled && _outgoingMessageQueue.size() <= 0)
                        this._signal.wait();
                    
                    if (!this._enabled)
                        break;
                    
                    // must be at least one message in the queue
                    message = _outgoingMessageQueue.removeFirst();
                }
                
                doSendMessage(message);
            } // (for)
        } catch (InterruptedException exc) {
            // (safe to bring down)
        }

        this._logger.info("Thread run to completion.");
    } // (method)

    /**
     * Cleans up the socket
     * (must already be 'synchronized')
     */
    private void cleanup() {
        this._enabled = false;
        _thread = null;
        
        // close the socket
        try {
            if (_socket != null)
                _socket.close();
            
        } catch (Exception exc) {
            // (must consume)
        }        
    } // (method)

    /**
     * (thread entry-point)
     */
    private void run() {
        synchronized (this._signal) {
            // release the 'start' method
            this._enabled = true;
            this._signal.notify();
        }
        
        // on single thread so can create a reusable character buffer that will grow if necessarily
        StringBuilder sb = new StringBuilder(256);        
        
        // enter the main loop
        try {
            while (this._enabled) {
                // always reset the buffer
                sb.setLength(0);

                // receive the JSON stream
                if (!_reader.readJSONMessage(sb))
                    throw new EOFException("Unexpectedly reached the end of the stream.");

                // retrieve the message delivered to this channel server
                ChannelMessage message = (ChannelMessage) Serialisation.coerceFromJSON(ChannelMessage.class, sb);
                
                super.handleMessage(message);
            } // (while)

        } catch (Exception exc) {
            boolean wasEnabled = false;
            
            synchronized (this._signal) {
                if (this._enabled) {
                    wasEnabled = true;
                    
                    this._logger.info("Unexpected exception occurred; this may be natural. Pulling down channel server.", exc);

                    cleanup();
                }
            }
            
            if (wasEnabled)
                super.handleFailure(exc);
        }
        
        this._logger.info("Thread has run to completion.");
    } // (method)
    
    /**
     * Permanently shuts down this channel freeing up all resources.
     * (may briefly block)
     * (exception free)
     */
    public void shutdown() {
        synchronized (this._signal) {
            if (!this._enabled)
                return;
            
            this._enabled = false;

            cleanup();
            
            // notify the thread in case its sleeping
            this._signal.notifyAll();            
            
            // wait for the thread to fully complete
            try {
                _thread.join();
            } catch (Exception exc) {
                // (must consume)
            }            
            
            // mark the thread as 'shutdown'
            _thread = null;
        } // (sync)
        
    } // (method)
    
    /**
     * Sends a message down the channel.
     * (exception free)
     * (non-blocking)
     */
    public void sendMessage(ChannelMessage message) {
        synchronized(this._signal) {
            if (!this._enabled)
                return;
            
            _outgoingMessageQueue.addLast(message);
            
            this._signal.notifyAll();
        }
    } // (method)
    
    /**
     * Synchronously sends the message.
     * (exception free)
     */
    private void doSendMessage(ChannelMessage message) {
        String jsonMessage = Serialisation.serialise(message, 4);

        int len = jsonMessage.length();

        StringBuilder sb = new StringBuilder();
        for (int a = 0; a < len; a++) {
            char c = jsonMessage.charAt(a);

            if (c == '\n') {
                // 'TELNET' friendly formatting
                sb.append("\r\n");
            } else {
                sb.append(c);
            }
        } // (for)

        sb.append("\r\n");

        try {
            _writer.write(sb.toString());
            _writer.flush();

        } catch (Exception exc) {
            boolean wasEnabled = false;
            synchronized (this._signal) {
                if (this._enabled) {
                    wasEnabled = true;
                    this._logger.warn("Unexpected exception occurred, pulling down channel server.", exc);

                    cleanup();
                }
            }

            if (wasEnabled)
                super.handleFailure(exc);
        }
    } // (method)

} // (class)
