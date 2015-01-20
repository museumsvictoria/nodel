package org.nodel.host;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;
import org.nodel.Handler;
import org.nodel.SimpleName;
import org.nodel.Threads;
import org.nodel.core.ActionRequestHandler;
import org.nodel.core.Nodel;
import org.nodel.core.NodelClientAction;
import org.nodel.core.NodelClientEvent;
import org.nodel.core.NodelServerAction;
import org.nodel.core.NodelServerEvent;
import org.nodel.core.NodelClients.NodeURL;
import org.nodel.discovery.AdvertisementInfo;
import org.nodel.discovery.AutoDNS;
import org.nodel.host.Binding;
import org.nodel.host.Bindings;
import org.nodel.host.LineReader;
import org.nodel.host.NodeConfig;
import org.nodel.host.NodelActionInfo;
import org.nodel.host.NodelEventInfo;
import org.nodel.host.ParamValues;
import org.nodel.host.ParameterBinding;
import org.nodel.host.ParameterBindings;
import org.nodel.host.RemoteBindingValues;
import org.nodel.host.RemoteBindings;
import org.nodel.host.RemoteBindingValues.ActionValue;
import org.nodel.host.RemoteBindingValues.EventValue;
import org.nodel.reflection.Param;
import org.nodel.reflection.Service;
import org.nodel.reflection.Value;
import org.nodel.threading.ThreadPool;
import org.nodel.threading.Timers;

/**
 * A base for a dynamic node, i.e. one which has dynamic actions and events.
 */
public class BaseDynamicNode {

    /**
     * (logging related)
     */
    private static AtomicLong s_instance = new AtomicLong();

    /**
     * (logging related)
     */
    private long _instance = s_instance.getAndIncrement();
    
    /**
     * (threading)
     */
    protected static ThreadPool s_threadPool = new ThreadPool("dynamic_node", 32);
    
    /**
     * (threading)
     */
    protected static Timers s_timerThread = new Timers("dynamic_node");
    
    /**
     * (initialised in constructor)
     * (logging related)
     */
    protected Logger _logger;
    
    /**
     * General purpose lock / signal.
     */
    protected Object _signal = new Object();

    /**
     * The root directory which holds configuration files and script.
     */
    protected File _root;
    
    /**
     * The config file.
     */
    protected File _configFile;
            
    /**
     * Holds the name (derived from the directory name).
     */
    @Value(name = "name", title = "Name", order = 10, desc = "The name of the node.")
    protected SimpleName _name;
    
    /**
     * Returns the name
     */
    public SimpleName getName() {
        return _name;
    }
    
    /**
     * (see 'getDesc')
     */
    protected String _desc;
    
    /**
     * Holds the description of the node.
     */
    @Value(name = "desc", title = "Description", order = 20, desc = "The description of the Node using docstring on the 'main' function.")
    public String getDesc() {
        return _desc;
    }
    
    /**
     * (see 'getDesc')
     */
    @Value(name = "nodelVersion", title = "Nodel version", order = 21, desc = "The Nodel environment version.")
    public final String nodelVersion = Nodel.getVersion();
            
    /**
     * The current bindings.
     */
    protected Bindings _bindings = Bindings.Empty;
    
    /**
     * Holds all lines going to standard error.
     */
    protected LineReader _errReader;
    
    /**
     * Holds all the lines going to standard out.
     */
    protected LineReader _outReader;
    
    /**
     * For sequence counting, starting at current time
     * to get a unique, progressing sequence number every 
     * time (regardless of restart).
     * (locked around 'logs')
     */
    private long _logsSeqCounter = System.currentTimeMillis();
    
    /**
     * Holds the event logs.
     * (locked around self)
     */
    private LinkedList<LogEntry> _logs = new LinkedList<LogEntry>();
    
    /**
     * For sequence counting.
     * (locked around 'console')
     */
    private long _consoleSeqCounter = System.currentTimeMillis();    
    
    /**
     * Holds the console logs
     */
    private LinkedList<ConsoleLogEntry> _console = new LinkedList<ConsoleLogEntry>();
    
    /**
     * The time this node instance was started.
     */
    protected DateTime _started = DateTime.now();
    
    @Value(name = "started", title = "Started", order = 20, desc = "The time this node instance was started.")
    public DateTime getStarted() {
        return _started;
    }
    
    /**
     * Compares the start time given, waits if necessary, returning a new time or the previous one.
     * (allows caller to throttle efficiently).
     */
    @Service(name = "hasRestarted", title = "Has restarted", desc = "Compares the start time given, waits if necessary, returning a new time or the previous one. Allows caller to throttle efficiently.", order = 20, embeddedFieldName = "timestamp")
    public DateTime hasRestarted(
            @Param(name = "timestamp", title = "Timestamp", desc = "Timestamp to compare against.") DateTime timestamp, 
            @Param(name = "timeout", title = "Timeout", desc = "Timeout before returning.") int timeout) {
        synchronized(_signal) {
            if (_started == null) {
                // what to be notified when first one is available
                Threads.waitOnSync(_signal, timeout);
                
                // fall through...
            }
            
            if (timeout > 0 && timestamp != null && _started.compareTo(timestamp) == 0) {
                Threads.waitOnSync(_signal, timeout);
            }
            
            return _started;
        }
    } // (method)
    
    /**
     * Stores the internal config, NOT live bindings.
     */
    protected NodeConfig _config = new NodeConfig();
    
    /**
     * Base constructor for a dynamic node.
     */
    public BaseDynamicNode(File root) throws IOException {
        if (root == null)
            throw new IllegalArgumentException("Root cannot be null");
                
        _root = root;
        
        // determine the name before logging is initialised
        _name = new SimpleName(root.getCanonicalFile().getName());
        
        _logger = LogManager.getLogger(String.format("%s_%d_%s", this.getClass().getName(), _instance, _name.getReducedName()));
        
        _outReader = new LineReader();
        _outReader.setHandler(new Handler.H1<String>() {
            
            @Override
            public void handle(String line) {
                addConsoleLog(DateTime.now(), ConsoleLogEntry.Console.out, line);
            }
            
        });        
        
        _errReader = new LineReader();
        _errReader.setHandler(new Handler.H1<String>() {
            
            @Override
            public void handle(String line) {
                addConsoleLog(DateTime.now(), ConsoleLogEntry.Console.err, line);
            }
            
        });

        _logger.info("Node initialised. Name=" + _name + ", Root='" + _root.getAbsolutePath() + "'");
    } // (constructor)
    
    /**
     * Returns the folder root for this node.
     */
    public File getRoot() {
        return _root;
    }
            
    /**
     * Streams the general event/action log.
     */
    @Service(name = "logs", title = "Logs", genericClassA = LogEntry.class, desc = "Retrieves this node's general event/action log.")
    public List<LogEntry> getLogs(
            @Param(name = "from", title = "From", desc = "The minimum sequence number.")
            long from,
            @Param(name = "max", title = "Max", desc = "The maximum number of rows to return.")
            int max,
            @Param(name = "timeout", title = "Timeout", desc = "How long to wait for new items in ms (default 0)")
            int timeout) {
        LinkedList<LogEntry> batch = new LinkedList<LogEntry>();

        synchronized (_logs) {
            if (_logsSeqCounter < from)
                from = 0;

            Iterator<LogEntry> inReverse = _logs.descendingIterator();
            while (inReverse.hasNext()) {
                LogEntry entry = inReverse.next();
                if (entry.seq >= from)
                    batch.add(entry);

                if (batch.size() >= max)
                    break;
            } // (while)

            if (batch.size() == 0 && timeout > 0) {
                Threads.waitOnSync(_logs, timeout);

                // will only recurse once more
                return getLogs(from, max, 0);
            }

            return batch;
        }
    } // (method)
    
    /**
     * Adds to the event logs, dropping if necessary.
     */
    protected void addLog(DateTime now, LogEntry.Source source, LogEntry.Type type, String alias, Object arg) {
        synchronized (_logs) {
            // stamp with current seq number
            LogEntry entry = new LogEntry(_logsSeqCounter++, now, source, type, alias, arg);

            _logs.add(entry);

            if (_logs.size() > 1000)
                _logs.removeFirst();

            _logs.notifyAll();
        }
    } // (method)
    
    /**
     * Streams the console log.
     */
    @Service(name = "console", title = "Console", desc = "The script console.", genericClassA = ConsoleLogEntry.class)
    public List<ConsoleLogEntry> getConsoleLogs(@Param(name = "from", title = "From", desc = "The minimum sequence number.") 
                                                long from, 
                                                @Param(name = "max", title = "Max", desc = "The maximum number of rows to return.") 
                                                int max,
                                                @Param(name = "timeout", title = "Timeout", desc = "How long to wait for new items in ms (default 0)") 
                                                int timeout) {
        LinkedList<ConsoleLogEntry> batch = new LinkedList<ConsoleLogEntry>();
        
        synchronized (_console) {
            if (_consoleSeqCounter < from)
                from = 0;            
            
            Iterator<ConsoleLogEntry> inReverse = _console.descendingIterator();
            while (inReverse.hasNext()) {
                ConsoleLogEntry entry = inReverse.next();
                if (entry.seq >= from)
                    batch.add(entry);
                else
                    break;

                if (batch.size() >= max)
                    break;
            } // (while)
            
            if (batch.size() == 0 && timeout > 0) {
                Threads.waitOnSync(_console, timeout);

                // will only recurse once more
                return getConsoleLogs(from, max, 0);
            }            

            return batch;
        }
    } // (method)
    
    /**
     * Adds to the console logs, dropping if necessary.
     */
    private void addConsoleLog(DateTime timestamp, ConsoleLogEntry.Console console, String line) {
        synchronized(_console) {
            // stamp with current sequence number
            ConsoleLogEntry entry = new ConsoleLogEntry(_consoleSeqCounter++, timestamp, console, line); 
            
            _console.add(entry);
            
            if (_console.size() > 1000)
                _console.removeFirst();
            
            _console.notifyAll();
        }
    } // (method)
        
    /**
     * Injects the remote binding values into the Remote Bindings
     */
    protected void injectRemoteBindingValues(NodeConfig config, RemoteBindings remoteBindings) {
        // fill out any 'gaps' in config
        RemoteBindingValues remoteBindingValues = config.remoteBindingValues;
        if (remoteBindingValues == null) {
            remoteBindingValues = new RemoteBindingValues();
            config.remoteBindingValues = remoteBindingValues;
        }
        
        if (remoteBindingValues.actions == null)
            remoteBindingValues.actions = new LinkedHashMap<SimpleName, RemoteBindingValues.ActionValue>();
        if (remoteBindingValues.events == null)
            remoteBindingValues.events = new LinkedHashMap<SimpleName, RemoteBindingValues.EventValue>();
        
        // actions
        Map<SimpleName, ActionValue> actionValues = remoteBindingValues.actions;
        if (actionValues != null) {
            for(Entry<SimpleName, ActionValue> entry : actionValues.entrySet()) {
                SimpleName name = entry.getKey();
                ActionValue actionValue = entry.getValue();
                
                NodelActionInfo actionInfo = remoteBindings.actions.get(name);
                if (actionInfo == null)
                    continue;
                
                if (actionValue.node != null)
                    actionInfo.node = actionValue.node.getOriginalName();
                if (actionValue.action != null)
                    actionInfo.action = actionValue.action.getOriginalName();
            } // (for)
        }
        
        // events
        Map<SimpleName, RemoteBindingValues.EventValue> eventValues = remoteBindingValues.events;
        if (eventValues != null) {
            for(Entry<SimpleName, EventValue> entry : eventValues.entrySet()) {
                SimpleName name = entry.getKey();
                EventValue eventValue = entry.getValue();
                
                NodelEventInfo eventInfo = remoteBindings.events.get(name);
                if (eventInfo == null)
                    continue;
                
                if (eventValue.node != null)
                    eventInfo.node = eventValue.node.getOriginalName();
                if (eventValue.event != null)
                    eventInfo.event = eventValue.event.getOriginalName();
            } // (for)
        }
        
        // flesh out 'gaps' in config object
        
        for (Entry<SimpleName, NodelActionInfo> action : remoteBindings.actions.entrySet()) {
            SimpleName name = action.getKey();
            if (!remoteBindingValues.actions.containsKey(name)) {
                remoteBindingValues.actions.put(name, new ActionValue());
            }
        } // (for)
        
        for (Entry<SimpleName, NodelEventInfo> event : remoteBindings.events.entrySet()) {
            SimpleName name = event.getKey();
            if (!remoteBindingValues.events.containsKey(name)) {
                remoteBindingValues.events.put(name, new EventValue());
            }
        } // (for)
        
        return;
    } // (method)
    
    /**
     * Injects the param values into the remote binding values into the Remote Bindings
     */
    protected void injectParamValues(NodeConfig config, ParameterBindings paramBindings) {
        // fill out any 'gaps' in config
        ParamValues paramValues = config.paramValues;
        if (paramValues == null) {
            paramValues = new ParamValues();
            config.paramValues = paramValues;
        }
        
        // params
        if (paramValues != null) {
            for(Entry<SimpleName, Object> entry : paramValues.entrySet()) {
                SimpleName name = entry.getKey();
                Object state = entry.getValue();
                
                ParameterBinding paramBinding = paramBindings.get(name);
                if (paramBinding == null)
                    continue;
                
                paramBinding.value = state;
            } // (for)
        }        
        
        // flesh out 'gaps' in config object
        for (Entry<SimpleName, ParameterBinding> entry : paramBindings.entrySet()) {
            SimpleName name = entry.getKey();
            if (!paramValues.containsKey(name)) {
                // using JSONObject to pad out the Map which don't
                // work with 'null' values (serialisation libraries understand it)
                //paramValues.put(name, JSONObject.NULL);
            }
        } // (for)
        
        return;
    } // (method)
    
    /**
     * Used to ensure advertisement even without actions / events, etc.
     */
    protected NodelServerAction _dummyBinding;
   
    /**
     * Cleans up previous established bindings.
     */
    protected void cleanupBindings() {
        // release previous local actions
        for (ServerActionEntry entry : _localActions.values()) {
            _logger.info("Releasing server action " + entry.action.getNodelPoint());
            entry.action.close();
        }
        _localActions.clear();
        
        // release previous local events
        for (ServerEventEntry entry : _localEvents.values()) {
            _logger.info("Releasing server event " + entry.__event.getNodelPoint());
            entry.__event.close();
        }
        _localEvents.clear();

        // release the dummy local binding (if it was used)
        if (_dummyBinding != null) {
            _logger.info("Releasing dummy local binding.");
            _dummyBinding.close();

            _dummyBinding = null;
        }

        // release previous events
        for (RemoteEventEntry entry : _remoteEvents) {
            _logger.info("Releasing client event " + entry.event.getNodelPoint());
            entry.event.close();
        }
        _remoteEvents.clear();        
        
        // release previous events
        for (RemoteActionEntry entry : _clientActions) {
            _logger.info("Releasing client action " + entry.action.getNodelPoint());
            entry.action.close();
        }
        _clientActions.clear();
        
        // release previous params
        for (ParameterEntry entry : _parameters) {
            _logger.info("Releasing parameter " + entry.name);
        }
        
        _parameters.clear();        
    } // (method)
    
    public class Local {

        /**
         * The parameters bindings.
         */
        @Service(name = "schema", title = "Schema", desc = "Returns the processed schema that produced data that can be used by 'save'.")
        public Map<String, Object> getSchema() {
            return _bindings.local.asSchema();
        }
        
        @Value(name = "value", title = "Value", desc = "The parameters value object.", treatAsDefaultValue = true)
        public Map<String, Object> getValue() {
            return _bindings.local.asValue();            
        }

    } // (class)
    
    /**
     * Holds the live params.
     */
    private Local _local = new Local();

    @Service(name = "local", title = "Local", desc = "Holds the local live parameters.")
    public Local getLocal() {
        return _local;
    }    
    
    public class ServerActionEntry {
        
        public Binding binding;
        
        public NodelServerAction action;
        
        public ServerActionEntry(Binding binding, NodelServerAction action) {
            this.binding = binding;
            this.action = action;
        }
        
        @Value(name = "name", title = "Name", desc = "A simple name, alias or ID.")
        public String getName() {
            return this.action.getAction().getReducedName();
        }
        
        @Value(name = "title", title = "Title", desc = "A short title.")
        public String getTitle() {
            return this.binding.title;
        }
        
        @Value(name = "desc", title = "Description", desc = "A short description.")
        public String getDesc() {
            return this.binding.desc;
        }
        
        @Value(name = "group", title = "Group", desc = "A group name.")
        public String getGroup() {
            return this.binding.group;
        }
        
        @Value(name = "caution", title = "Caution", desc = "A caution message associated with this action.")
        public String getCaution() {
            return this.binding.caution;
        }
        
        @Service(name = "call", title = "Call", desc = "Invokes this action.")
        public void callExternal(@Param(name="arg", title="Argument") Object arg) {
            ActionRequestHandler handler = this.action.getHandler();
            assert handler != null : "Action request handler was null but should never be.";
            
            handler.handleActionRequest(arg);
        }
        
        /**
         * Used from within the script.
         */
        public void call(Object arg) {
            ActionRequestHandler handler = this.action.getHandler();
            assert handler != null : "Action request handler was null but should never be.";
            
            handler.handleActionRequest(arg);
        }        
        
        @Value(name = "schema", title = "Schema", genericClassA = String.class, genericClassB = Object.class, desc = "The raw stand-alone schema.")
        public Map<String, Object> getRawArgSchema() {
            return binding.schema;
        }

        @Service(name = "schema", title = "Schema", genericClassA = String.class, genericClassB = Object.class, desc = "Prepares a filtered schema for this action.")
        public Map<String, Object> argSchema() {
            Map<String, Object> schema = new LinkedHashMap<String, Object>();
            schema.put("type", "object");

            Map<String, Object> properties = new LinkedHashMap<String, Object>();

            schema.put("properties", properties);
            
            Map<String, Object> argSchema;
            
            if (binding.schema == null) {
                argSchema = new LinkedHashMap<String, Object>();
                argSchema.put("type", "null");
            } else {
                argSchema = binding.schema;
            }
            
            properties.put("arg", argSchema);

            return schema;
        }
        
    } // (class)
    
    
    protected Map<SimpleName, ServerActionEntry> _localActions = new LinkedHashMap<SimpleName, ServerActionEntry>();
    
    @Service(name = "actions", title = "Actions", desc = "The local actions.", genericClassA = SimpleName.class, genericClassB = ServerActionEntry.class)
    public Map<SimpleName, ServerActionEntry> getLocalActions() {
        return _localActions;
    }
        
    public class ServerEventEntry {
        
        private Binding __binding;
        
        public NodelServerEvent __event;
        
        public ServerEventEntry(Binding binding, NodelServerEvent event) {
            __binding = binding;
            __event = event;
        }
        
        @Value(name = "name", title = "Name", desc = "The name.", order = 1)
        public String getName() {
            return __event.getNodelPoint().getPoint().getReducedName();
        }
        
        @Value(name = "title", title = "Title", desc = "A title.", order = 2)
        public String getTitle() {
            return __binding.title;
        }
        
        @Value(name = "desc", title = "Description", desc = "A short description.", order = 3)
        public String getDesc() {
            return __binding.desc;
        }
        
        @Value(name = "group", title = "Group", desc = "A group.", order = 4)
        public String getGroup() {
            return __binding.group;
        }
        
        @Value(name = "caution", title = "Caution", desc = "A caution message associated with the generation of this event.", order = 5)
        public String getCaution() {
            return __binding.caution;
        }
        
        /**
         * Used from externally (web, etc)
         */
        @Service(name = "emit", title = "Emit", desc = "Emits this event.")
        public void externalEmit(@Param(name = "arg", title = "Argument", desc = "The event's (optional) argument.") Object arg) {
            __event.emit(arg);
        }
        
        /**
         * Used from within the script. 
         */
        public void emit(Object arg) {
            __event.emit(arg);
        }
        
        @Value(name = "schema", title = "Schema", genericClassA = String.class, genericClassB = Object.class, desc = "The raw stand-alone schema.")
        public Map<String, Object> getRawArgSchema() {
            return __binding.schema;
        }

        @Service(name = "schema", title = "Schema", genericClassA = String.class, genericClassB = Object.class, desc = "Prepares a filtered schema for this action.")
        public Map<String, Object> argSchema() {
            Map<String, Object> schema = new LinkedHashMap<String, Object>();
            schema.put("type", "object");

            Map<String, Object> properties = new LinkedHashMap<String, Object>();

            schema.put("properties", properties);
            
            Map<String, Object> argSchema;
            
            if (__binding.schema == null) {
                argSchema = new LinkedHashMap<String, Object>();
                argSchema.put("type", "null");
            } else {
                argSchema = __binding.schema;
            }
            
            properties.put("arg", argSchema);

            return schema;
        }        
        
    } // (class)
    
    protected Map<SimpleName, ServerEventEntry> _localEvents = new LinkedHashMap<SimpleName, ServerEventEntry>();
    
    @Service(name = "events", title = "Events", desc = "The local events.", genericClassA = SimpleName.class, genericClassB = ServerEventEntry.class)
    public Map<SimpleName, ServerEventEntry> getLocalEvents() {
        return _localEvents;
    }
                
    protected class RemoteActionEntry {
        
        public NodelClientAction action;
        
        public RemoteActionEntry(NodelClientAction action) {
            this.action = action;
        }
    }
    
    protected List<RemoteActionEntry> _clientActions = new ArrayList<RemoteActionEntry>();
        
    protected class RemoteEventEntry {
        
        public NodelClientEvent event;
        
        public RemoteEventEntry(NodelClientEvent event) {
            this.event = event;
        }
        
    } // (class)
    
    protected List<RemoteEventEntry> _remoteEvents = new ArrayList<RemoteEventEntry>();
            
    protected List<ParameterEntry> _parameters = new ArrayList<ParameterEntry>();
    
    protected class ParameterEntry {
        
        public SimpleName name;
        
        public ParameterEntry(SimpleName name) {
            this.name = name;
        }
    }
    
    /**
     * Outside callers can inject error messages related to this node.
     */
    protected void notifyOfError(Exception exc) {
        _errReader.inject(exc.toString());
    }

    @Service(name = "allNodes", order = 5, title = "All nodes", desc = "Returns all the advertised nodes.")
    public Collection<AdvertisementInfo> getAllNodes() {
        return Nodel.getAllNodes();
    }

    @Service(name = "autoDNS", order = 6, title = "Auto DNS", desc = "The service providing multicast DNS services.")
    public AutoDNS mdms() {
        return AutoDNS.instance();
    }

    @Service(name = "nodeURLs", order = 6, title = "Node URLs", desc = "Returns the addresses of the advertised nodes.")
    public List<NodeURL> nodeURLs(
            @Param(name = "filter", title = "Filter", desc = "Optional string filter.") String filter) throws IOException {
        return Nodel.getNodeURLs(filter);
    }

} // (class)
