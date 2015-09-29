package org.nodel.jyhost;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import org.nodel.Handler;
import org.nodel.SimpleName;
import org.nodel.host.BaseNode;
import org.nodel.host.LogEntry;
import org.nodel.io.UnexpectedIOException;
import org.nodel.reflection.Serialisation;
import org.nodel.threading.TimerTask;
import org.nodel.threading.Timers;
import org.nodel.websockets.NanoWebSocketServer;
import org.nodel.websockets.NanoWebSocketServer.WebSocketFrame.CloseCode;
import org.nodel.websockets.WebSocketMessage;

/**
 * A WebSocket request might come in like this:
 * 
 * ws://192.168.178.12:8085/nodes/Office%20Amplifier
 * -------------------------------------------
 * SEND> { "activity": { "seq": 12345, "timestamp": "...", ... } }
 * or
 * SEND> { "error": "Node not found" }
 */
public class NodelHostWebSocketServer extends NanoWebSocketServer {
    
    /**
     * Timers, used for 'ping/pong'
     */
    protected static Timers s_timers = new Timers("NodelHostWebSocketServer");
    
    private class QueueEntry {
        
        /**
         * The websocket data.
         */
        public WebSocketMessage msg;
        
        /**
         * 'Ping' to be sent
         */
        public boolean ping;
        
        /**
         * Socket to close because of silence.
         */
        public boolean silentTooLong;
    }
    
    /**
     * (locking around instances, global not needed)
     */
    private class SessionEntry {
        
        public boolean busy = false;
        
        public WebSocket webSocket;
        
        public BaseNode node;
        
        public Handler.H1<LogEntry> activityHandler;
        
        public Queue<QueueEntry> sendQueue = new LinkedList<QueueEntry>();

        public TimerTask pingTimer;
        
        /**
         * Last time this session received data.
         * (System.nanoTime base)
         */
        public long lastReceive = System.nanoTime();
        
        public SessionEntry(WebSocket webSocket, BaseNode node) {
            this.webSocket = webSocket;
            this.node = node;
        }
        
    }
    
    /**
     * Holds the one-to-one WebSocket/Node sessions.
     * (locked around self)
     */
    private Map<WebSocket, SessionEntry> _sessions = new HashMap<WebSocket, SessionEntry>();
    
    public NodelHostWebSocketServer(int port) {
        super(port);
    }
    
    @Override
    protected void onOpen(WebSocket webSocket) {
        // determine the node it's related to
        String uri = webSocket.getHandshakeRequest().getUri();
        
        // get the parts (avoiding blank first part if necessary)
        String[] parts = (uri.startsWith("/") ? uri.substring(1) : uri).split("/");

        // check if we're serving up from a node
        // e.g. '/nodes/Office amplifier'
        if (parts.length >= 2 && parts[0].equalsIgnoreCase("nodes")) {
            String origName = parts[1];
            
            // the second part will be the node name
            SimpleName nodeName = new SimpleName(parts[1]);

            BaseNode node = BaseNode.getNode(nodeName);
            if (node == null) {
                indicateNotFoundAndClose(webSocket, origName);
                return;
            } 
            
            // the node is present, establish a new session
            newSession(webSocket, node);
        } else {
            // is the root
            // RESERVED for possible future use, close for now
            indicateMethodNotFound(webSocket);
        }
    }    
   
    /**
     * Sets up a new session that can be pulled down when the socket dies.
     */
    private void newSession(WebSocket webSocket, BaseNode node) {       
        SessionEntry session = new SessionEntry(webSocket, node);
        session.activityHandler = new Handler.H1<LogEntry>() {
            
            @Override
            public void handle(LogEntry activity) {
                handleActivityArrived(session, activity);
            }
            
        };
        
        // ping every 45s (read timeout is 60s)
        session.pingTimer = s_timers.schedule(new TimerTask() {

            @Override
            public void run() {
                handlePingTimer(session);
            }
            
        }, 45000, 45000);
        
        synchronized(_sessions) {
            _sessions.put(webSocket, session);
        }
        
        // get the latest history so states can be synced up
        List<LogEntry> activityHistory = node.registerActivityHandler(session.activityHandler, 0);
        
        // prepare the message
        WebSocketMessage msg = new WebSocketMessage();
        msg.activityHistory = activityHistory;
        
        QueueEntry qe = new QueueEntry();
        qe.msg = msg;
        
        queueSend(session, qe);
    }
    
    /**
     * (timer entry-point)
     */
    private void handlePingTimer(SessionEntry session) {
        // check whether too much time has passed
        long diff = (session.lastReceive - System.currentTimeMillis()) / 1000000;
        if (diff > 120000) {
            // too much time has passed with no response (pong or otherwise), so forcefully
            // close connection

        }
        
        QueueEntry qe = new QueueEntry();
        qe.ping = true;
        
        queueSend(session, qe);
    }

    /**
     * (must not-block)
     * (onActivity callback)
     */
    private void handleActivityArrived(SessionEntry session, LogEntry activity) {
        WebSocketMessage msg = new WebSocketMessage();
        msg.activity = activity;
        
        QueueEntry qe = new QueueEntry();
        qe.msg = msg;
        
        queueSend(session, qe);
    }
    
    
    /**
     * Queues the message to be set immediately (without blocking)
     */
    private void queueSend(SessionEntry session, QueueEntry qe) {
        synchronized (session) {
            // add to the queue
            session.sendQueue.add(qe);
            
            // kick off processor if necessary
            if (!session.busy) {
                session.busy = true;

                // run on the thread-pool
                s_threadPool.execute(new Runnable() {

                    @Override
                    public void run() {
                        processQueue(session);
                    }

                });
            }
        }
    }
    
    /**
     * Process the message queue (within a thread-pool)
     */
    private void processQueue(SessionEntry session) {
        for (;;) {
            QueueEntry qe;
            
            synchronized (session) {
                qe = session.sendQueue.poll();
                if (qe == null) {
                    session.busy = false;
                    return;
                }
            }

            // 'busy' flag will stay high while data is being sent
            try {
                if (qe.ping) {
                    WebSocket webSocket = session.webSocket;
                    webSocket.ping("ping".getBytes());
                    
                } else if (qe.silentTooLong) {
                    WebSocket webSocket = session.webSocket;
                    webSocket.close(CloseCode.NormalClosure, "Silent too long");
                    
                } else if (qe.msg != null) {
                    WebSocket webSocket = session.webSocket;
                    String data = Serialisation.serialise(qe.msg);
                    webSocket.send(data);
                }

            } catch (Exception exc) {
                _logger.debug("Failure while processing send queue.", exc);
                continue;
            }
            
            // if a socket is closed, this will rapidly consume all items in the queue
            // and exit
        }
    }

    @Override
    protected void onMessage(WebSocket webSocket, WebSocketFrame messageFrame) {
        synchronized(_sessions) {
            SessionEntry session = _sessions.get(webSocket);
            if (session == null)
                return;

            session.lastReceive = System.nanoTime();
        }
    }

    @Override
    protected void onClose(WebSocket webSocket, CloseCode code, String reason, boolean initiatedByRemote) {
        synchronized(_sessions) {
            SessionEntry session = _sessions.get(webSocket);
            if (session == null)
                return;
            
            session.node.unregisterActivityHandler(session.activityHandler);
            
            session.pingTimer.cancel();           
        }
    }

    @Override
    protected void onException(WebSocket webSocket, IOException e) {
    }

    @Override
    protected void onPong(WebSocket webSocket, WebSocketFrame pongFrame) {
        synchronized(_sessions) {
            SessionEntry session = _sessions.get(webSocket);
            if (session == null)
                return;
            
            // update timestamp
            session.lastReceive = System.nanoTime();
        }        
    }
    
    /**
     * Sends a 'Method not found' message.
     */
    private void indicateMethodNotFound(WebSocket webSocket) {
        try {
            WebSocketMessage msg = new WebSocketMessage();
            msg.error = "Method not found";
            
            sendMessage(webSocket, msg);
            
            webSocket.close(CloseCode.ProtocolError, "Method not found");
        } catch (Exception exc) {
            throw new RuntimeException(exc);
        }
    }
    
    /**
     * Sends a 'Node not found' response.
     */
    private void indicateNotFoundAndClose(WebSocket webSocket, String nodeName) {
        try {
            WebSocketMessage msg = new WebSocketMessage();
            msg.node = nodeName;
            msg.error = "Node not found";

            sendMessage(webSocket, msg);
            
            webSocket.close(CloseCode.AbnormalClosure, "Node not found.");
        } catch (IOException exc) {
            throw new UnexpectedIOException(exc);
        }
    }
    
    /**
     * Sends a structured WebSocket message.
     */
    private void sendMessage(WebSocket webSocket, WebSocketMessage msg) throws IOException {
        webSocket.send(Serialisation.serialise(msg));
    }    

}
