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
import java.io.Writer;
import java.net.Socket;

import javax.net.SocketFactory;

import org.nodel.DateTimes;
import org.nodel.diagnostics.CountableInputStream;
import org.nodel.diagnostics.CountableOutputStream;
import org.nodel.diagnostics.Diagnostics;
import org.nodel.diagnostics.LongSharableMeasurementProvider;
import org.nodel.diagnostics.SharableMeasurementProvider;
import org.nodel.reflection.Serialisation;

/**
 * Manages a channel client, including connection, etc.
 */
public class TCPChannelClient extends ChannelClient {
    
    /**
     * (diagnostics)
     */
    private static SharableMeasurementProvider s_dataInCounter = new LongSharableMeasurementProvider();
    
    /**
     * (diagnostics)
     */    
    private static SharableMeasurementProvider s_dataOutCounter = new LongSharableMeasurementProvider();
    
    /**
     * (diagnostics)
     */
    static {
        Diagnostics.shared().registerCounter("Nodel TCP client channels.Receive rate", s_dataInCounter, true);
        Diagnostics.shared().registerCounter("Nodel TCP client channels.Send rate", s_dataOutCounter, true);
        
        // note: 'op' counts are selectively excluded for brevity
    }

    /**
     * Started or not. 
     */
    private boolean _started = false;
    
    /**
     * The main thread for performing the connection and streaming.
     * (initialised in constructor, will be null if shutdown.) 
     */
    private Thread _thread;
    
    /**
     * (locked around 'signal')
     */
    private Socket _socket;

    /**
     * (locked around 'signal')
     */
    private JSONStreamReader _reader;
    
    /**
     * (locked around 'signal')
     */
    private Writer _writer;
    
    /**
     * Creates a new channel client which is responsible for connection and reconnection.
     * (does not block)
     */
    public TCPChannelClient(NodeAddress address) {
        super(address);
        
        // initialise a long running thread to read from the socket
        _thread = new Thread(new Runnable() {
            @Override
            public void run() {
                TCPChannelClient.this.run();
            }
        });
        _thread.setName(String.format("ChannelClient%03d", this._instance));
        _thread.setDaemon(true);
    } // (constructor)
    
    /**
     * Starts the channel client. Should only be called after all event handlers are attached.
     */
    @Override
    protected void start() {
        synchronized(this._signal) {
            if (_started)
                throw new IllegalStateException("Already started.");
            
            _started = true;
            
            _thread.start();
        }
    } // (method)
    
    /**
     * (thread entry-point)
     */
    private void run() {
        Socket socket = null;
        try {
            long start = System.nanoTime();
            socket = SocketFactory.getDefault().createSocket(this._address.getHost(), this._address.getPort());
            
            _logger.info("Connected to {} (took {})", this._address, DateTimes.formatPeriod(start)); 
            
            processSocket(socket);
            
        } catch (Exception exc) {
            synchronized (this._signal) {
                _logger.trace("run exception");
                
                if (_enabled) {
                    _enabled = false;
                    
                    safeCleanup();
                }
            }
            
            // the fault event is only handled here once
            onConnectionFault(exc);
        }

        _logger.info("Thread run to completion.");
    } // (method)
    
    private void processSocket(Socket socket) throws IOException {
        synchronized (this._signal) {
            _socket = socket;

            // both 'input' and 'output' will be cleaned up via the
            // 'this.socket.close()'.
            CountableInputStream input = new CountableInputStream(_socket.getInputStream(), SharableMeasurementProvider.Null.INSTANCE, s_dataInCounter);
            CountableOutputStream output = new CountableOutputStream(_socket.getOutputStream(), SharableMeasurementProvider.Null.INSTANCE, s_dataOutCounter);

            // need to buffer in the input since arriving via socket.
            _reader = new JSONStreamReader(new BufferedReader(new InputStreamReader(input)));

            // no need to buffer the output since higher layers work
            // at 'message' level, not byte.
            _writer = new OutputStreamWriter(output);
            
            // good time to update the event interests table.
            syncActionAndEventHandlerTable();            
        }
        
        // fire connected event
        onConnected();        
        
        for (;;) {
            // receive the JSON stream
            String jsonString = _reader.readJSONMessage();
            
            if (jsonString == null)
                throw new EOFException("Stream ended abruptly.");
            
            // retrieve the message delivered to this channel server
            ChannelMessage message = (ChannelMessage) Serialisation.coerceFromJSON(ChannelMessage.class, jsonString);
            
            handleMessage(message);
        } // (while)
        
    } // (method)
    
    
    /**
     * Instantaneous check whether the channel is connected or not.
     */
    @Override
    public boolean isConnected() {
        synchronized (this._signal) {
            return _socket != null;
        }
    } // (method)
    

    /**
     * Asynchronously sends the message down the channel.
     */
    @Override
    public void sendMessage(final ChannelMessage message) {
        s_threadPool.execute(new Runnable() {

            @Override
            public void run() {
                doSendMessage(message);
            }

        });
    } // (method)
    
    /**
     * Performs the IO to send the message.
     */
    private void doSendMessage(ChannelMessage message) {
        Writer writer = _writer;
        if (writer == null) {
            _logger.info("A message was dropped because the channel connect was not complete yet; safely ignoring. message='{}'" + message);
            return;
        }

        try {
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

            // writer will never be null
            writer.write(sb.toString());
            writer.flush();
            
        } catch (Exception exc) {
            synchronized (this._signal) {
                _logger.trace("sendMessage exception");
                
                if (_enabled) {
                    _logger.info("Socket write failed; socket will be cleaned up.", exc);

                    _enabled = false;

                    // bring down socket, which will cause the reading side to
                    // throw an exception
                    safeCleanup();
                }
            }
        } 
    } // (method)  

    /**
     * Cleans up.
     * (assumed locked, exception free)
     */
    private void safeCleanup() {
        if (_socket != null) {
            long ts = System.nanoTime();
            try {
                _socket.close();
            } catch (Exception exc) {
                // ignore
            } finally {
                _logger.trace("(socket.close() took {})", DateTimes.formatPeriod(ts));
            }
        }

        _socket = null;
    } // (method)

    /**
     * Permanently closes this channel.
     */
    @Override
    public void close() {
        synchronized (this._signal) {
            _enabled = false;
            
            // clean up and back-off if still enabled
            safeCleanup();
        }        
    }
    
} // (class)
