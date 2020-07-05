package org.nodel.websockets;

public class QueueEntry {
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
