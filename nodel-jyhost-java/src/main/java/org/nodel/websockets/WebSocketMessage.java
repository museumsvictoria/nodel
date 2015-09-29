package org.nodel.websockets;

import java.util.List;

import org.nodel.host.LogEntry;
import org.nodel.reflection.Value;

public class WebSocketMessage {

    @Value(name = "node")
    public String node;

    @Value(name = "error")
    public String error;

    @Value(name = "activity", title = "Activity", desc = "Live activity")
    public LogEntry activity;
    
    @Value(name = "activityHistory", title = "Activity history")
    public List<LogEntry> activityHistory;

}
