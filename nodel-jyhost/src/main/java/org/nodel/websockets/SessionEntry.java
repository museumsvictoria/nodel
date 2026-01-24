package org.nodel.websockets;

import org.nanohttpd.protocols.websockets.WebSocket;
import org.nodel.Handler;
import org.nodel.host.BaseNode;
import org.nodel.host.LogEntry;
import org.nodel.threading.TimerTask;

import java.util.LinkedList;
import java.util.Queue;

public class SessionEntry {
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
