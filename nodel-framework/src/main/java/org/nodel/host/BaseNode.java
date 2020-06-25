package org.nodel.host;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicLong;

import org.joda.time.DateTime;
import org.nodel.Handler;
import org.nodel.Handlers;
import org.nodel.SimpleName;
import org.nodel.Threads;
import org.nodel.core.ActionRequestHandler;
import org.nodel.core.ArgInstance;
import org.nodel.core.Nodel;
import org.nodel.core.NodelClientAction;
import org.nodel.core.NodelClientEvent;
import org.nodel.core.NodelServerAction;
import org.nodel.core.NodelServerEvent;
import org.nodel.core.NodelClients.NodeURL;
import org.nodel.discovery.AdvertisementInfo;
import org.nodel.discovery.AutoDNS;
import org.nodel.host.LogEntry.Source;
import org.nodel.host.LogEntry.Type;
import org.nodel.host.RemoteBindingValues.ActionValue;
import org.nodel.host.RemoteBindingValues.EventValue;
import org.nodel.io.Stream;
import org.nodel.reflection.Param;
import org.nodel.reflection.Schema;
import org.nodel.reflection.Serialisation;
import org.nodel.reflection.Service;
import org.nodel.reflection.Value;
import org.nodel.threading.ThreadPool;
import org.nodel.threading.Timers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BaseNode implements Closeable {
    
    /**
     * (threading)
     */
    protected static ThreadPool s_threadPool = new ThreadPool("Dynamic nodes", 32);
    
    /**
     * (threading)
     */
    protected static Timers s_timerThread = new Timers("Dynamic nodes");    
    
    /**
     * The repository of all base nodes created
     * (self locked)
     */
    private static Map<SimpleName, BaseNode> s_repo = new HashMap<SimpleName, BaseNode>();
    
    /**
     * (logging related)
     */
    private static AtomicLong s_instance = new AtomicLong();
    
    /**
     * (logging related)
     */
    private long _instance = s_instance.getAndIncrement();
    
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
     * If permanently closed (using 'close()')
     */
    protected boolean _closed = false;
    
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
     * The root directory which holds configuration files and script.
     */
    protected File _root;
        
    /**
     * Returns the folder root for this node.
     * (can be null if not anchored to file resource)
     */
    public File getRoot() {
        return _root;
    }
    
    /**
     * The folder ".nodel" which stores a node's metadata such as seed values. This
     * is typically transient, disposable and generated as needed.
     */
    protected File _metaRoot;
    
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
     * Holds a full history.
     * (self-locked)
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
     * The config file.
     */
    protected File _configFile;
    
    /**
     * Stores the internal config, NOT live bindings.
     */
    protected NodeConfig _config = new NodeConfig();
    
    public BaseNode(File root) throws IOException {
        this(new SimpleName(root.getCanonicalFile().getName()), root);
    }
    
    /**
     * Base constructor for a dynamic node.
     */
    public BaseNode(SimpleName name, File root) throws IOException {
        init(name);
        
        _root = root;
        _metaRoot = new File(_root, ".nodel");
        
        // make the directory (don't care if it can or cannot)
        _metaRoot.mkdirs();

        _logger.info("Node initialised. Name=" + _name + ", Root='" + _root.getAbsolutePath() + "'");
    } // (constructor)
    
    /**
     * Base constructor for a dynamic node.
     */
    public BaseNode(SimpleName name) {
        init(name);
        
        _logger.info("Node initialised. Name=" + _name);
    }
    
    /**
     * (common construction code)
     */
    private void init(SimpleName name) {
        // determine the name before logging is initialised
        _name = name;
        
        _logger = LoggerFactory.getLogger(String.format("%s_%d_%s", this.getClass().getName(), _instance, _name.getReducedName()));
        
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
        
        s_repo.put(_name, this);
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
     * (see 'getDesc')
     */
    @Value(name = "nodelVersion", title = "Nodel version", order = 21, desc = "The Nodel environment version.")
    public final String nodelVersion = Nodel.getVersion();
    
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
    protected void addLog(DateTime now, LogEntry.Source source, LogEntry.Type type, SimpleName alias, Object arg) {
        synchronized (_logs) {
            // stamp with current seq number
            LogEntry entry = new LogEntry(_logsSeqCounter++, now, source, type, alias, arg);

            _logs.add(entry);
            
            _logStreamerHandlers.updateAllUnsynchronized(entry);

            if (_logs.size() > 1000)
                _logs.removeFirst();

            _logs.notifyAll();
        }
    }
    
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
    protected void addConsoleLog(DateTime timestamp, ConsoleLogEntry.Console console, String line) {
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
     * Logs general log 
     */
    protected void log(String line) {
        addConsoleLog(DateTime.now(), ConsoleLogEntry.Console.out, line);
    }

    /**
     * Logs an error. 
     */
    protected void logError(String line) {
        addConsoleLog(DateTime.now(), ConsoleLogEntry.Console.err, line);
    }
    
    /**
     * Logs a warning. 
     */
    protected void logWarning(String line) {
        addConsoleLog(DateTime.now(), ConsoleLogEntry.Console.warn, line);
    }
    
    /**
     * Logs an info event. 
     */
    protected void logInfo(String line) {
        addConsoleLog(DateTime.now(), ConsoleLogEntry.Console.info, line);
    }
    
    /**
     * Holds log streamers.
     */
    private Handlers.H1<LogEntry> _logStreamerHandlers = new Handlers.H1<>();
    
    /**
     * Streams the general event/action log.
     */
    @Service(name = "activity", title = "Activity", genericClassA = LogEntry.class, desc = "Retrieves a syncable state of node's general event/action activity.")
    public List<LogEntry> getActivity(@Param(name = "from", title = "From", desc = "The minimum sequence number.")
                                       long from) {
        LinkedList<LogEntry> batch = new LinkedList<LogEntry>();

        synchronized (_logs) {
            if (Nodel.getSeq() < from)
                from = 0;
            
            // go through server events
            for (Entry<SimpleName, NodelServerEvent> entry : _localEvents.entrySet()) {
                NodelServerEvent localEvent = entry.getValue();

                long seq = localEvent.getSeqNum();
                if (seq >= from)
                    batch.add(new LogEntry(seq, localEvent.getTimestamp(), Source.local, Type.event, entry.getKey(), localEvent.getArg()));
            }

            // go through server actions
            for (Entry<SimpleName, NodelServerAction> entry : _localActions.entrySet()) {
                NodelServerAction localAction = entry.getValue();

                long seq = localAction.getSeqNum();
                if (seq >= from)
                    batch.add(new LogEntry(seq, localAction.getTimestamp(), Source.local, Type.action, entry.getKey(), localAction.getArg()));
            }
            
            // go through remote events
            for (NodelClientEvent remoteEvent : _remoteEvents.values()) {
                long seq = remoteEvent.getSeqNum();
                if (seq >= from)
                    batch.add(new LogEntry(seq, remoteEvent.getTimestamp(), Source.remote, Type.event, remoteEvent.getName(), remoteEvent.getArg()));

                seq = remoteEvent.getStatusSeqNum();
                if (seq >= from)
                    batch.add(new LogEntry(seq, remoteEvent.getStatusTimestamp(), Source.remote, Type.eventBinding, remoteEvent.getName(), remoteEvent.getBindingState()));

            }
            
            // go through remote actions
            for (NodelClientAction remoteAction : _remoteActions.values()) {
                long seq = remoteAction.getArgSeqNum();
                if (seq >= from)
                    batch.add(new LogEntry(seq, remoteAction.getArgTimestamp(), Source.remote, Type.event, remoteAction.getName(), remoteAction.getArg()));

                seq = remoteAction.getBindingStateSeqNum();
                if (seq >= from)
                    batch.add(new LogEntry(seq, remoteAction.getBindingStateTimestamp(), Source.remote, Type.actionBinding, remoteAction.getName(), remoteAction.getBindingState()));
            }            

            return batch;
        }
    }
    
    
    /**
     * Add an activity stream handler.
     */
    public List<LogEntry> registerActivityHandler(Handler.H1<LogEntry> handler, long from) {
        synchronized(_logs) {
            _logStreamerHandlers.addHandler(handler);
            
            List<LogEntry> current = getActivity(from);
            
            return current;
        }
    }
    
    /**
     * Unregisters an activity stream handler.
     */
    public void unregisterActivityHandler(Handler.H1<LogEntry> handler) {
        synchronized(_logs) {
            _logStreamerHandlers.removeHandler(handler);
        }        
    }
            
    protected Map<SimpleName, NodelServerAction> _localActions = new LinkedHashMap<SimpleName, NodelServerAction>();
    
    @Service(name = "actions", title = "Actions", desc = "The local actions.", genericClassA = SimpleName.class, genericClassB = NodelServerAction.class)
    public Map<SimpleName, NodelServerAction> getLocalActions() {
        return _localActions;
    }
    
    protected NodelServerAction addLocalAction(NodelServerAction action) {
        _localActions.put(action.getAction(), action);
        return action;
    }
    
    /**
     * (convenience method)
     */
    protected NodelServerAction addLocalAction(String name, ActionRequestHandler handler,
            String desc, String group, String caution, double order, String argTitle, Class<?> argClass) {
        SimpleName action = new SimpleName(name);
        
        Binding metadata = new Binding(action.getOriginalName(), desc, group, caution, order, Schema.getSchemaObject(argTitle, argClass));
        NodelServerAction nodelAction = new NodelServerAction(this.getName(), new SimpleName(action.getReducedName()), metadata);
        nodelAction.registerAction(handler);

        return addLocalAction(nodelAction);
    }
    
    /**
     * (can only be called by subclasses)
     */
    protected void removeLocalAction(NodelServerAction action) {
        _localActions.remove(action.getAction());
    }
    
    private Map<SimpleName, NodelServerEvent> _localEvents = new LinkedHashMap<SimpleName, NodelServerEvent>();
    
    @Service(name = "events", title = "Events", desc = "The local events.", genericClassA = SimpleName.class, genericClassB = NodelServerEvent.class)
    public Map<SimpleName, NodelServerEvent> getLocalEvents() {
        return _localEvents;
    }
    
    /**
     * Adds a local event, seeding the argument if persistent data is available.
     */
    protected NodelServerEvent addLocalEvent(final NodelServerEvent event) {
        // seed the event with some data if it exists
        String key = event.getNodelPoint().getPoint().getReducedForMatchingName();
        File seedFile = new File(_metaRoot, key + ".event.json");
        
        ArgInstance seed = null;
        
        if (seedFile.exists()) {
            try {
                seed = (ArgInstance) Serialisation.deserialise(ArgInstance.class, Stream.readFully(seedFile));
            } catch (Exception e) {
                // ignore
            }
        }
        
        event.seedAndPersist(seed, new Handler.H1<ArgInstance>() {

            @Override
            public void handle(ArgInstance instance) {
                persistEventArg(event, instance);
            }

        });

        // register with the framework
        event.registerEvent();
        
        _localEvents.put(event.getEvent(), event);
        return event;
    }
    
    /**
     * (convenience method)
     */
    protected NodelServerEvent addLocalEvent(String name, String desc, String group, String caution, double order, String argTitle, Class<?> argClass) {
        return addLocalEvent(name, desc, group, caution, order, Schema.getSchemaObject(argTitle, argClass));
    }
    
    /**
     * (convenience method)
     */
    protected NodelServerEvent addLocalEvent(String name, String desc, String group, String caution, double order, Map<String, Object> schema) {
        SimpleName event = new SimpleName(name);
        
        Binding metadata = new Binding(event.getOriginalName(), desc, group, caution, order, schema);
        NodelServerEvent nodelEvent = new NodelServerEvent(this.getName(), new SimpleName(event.getReducedName()), metadata);
        
        // TODO: check whether threading environment can be set here

        return addLocalEvent(nodelEvent);
    }
    
    protected void removeLocalEvent(NodelServerEvent event) {
        _localEvents.remove(event.getEvent());
    }
  
    /**
     * Persists an event's timestamp and argument.
     */
    private void persistEventArg(NodelServerEvent event, ArgInstance instance) {
        try {
            File seedFile = new File(_metaRoot, event.getNodelPoint().getPoint().getReducedForMatchingName() + ".event.json");

            Stream.writeFully(seedFile, Serialisation.serialise(instance));

        } catch (Exception exc) {
            // ignore
        }
    }
    
    /**
     * Persists an event's timestamp and argument.
     */
    private void persistEventArg(NodelClientEvent remoteevent, ArgInstance instance) {
        try {
            String key = remoteevent.getName().getReducedForMatchingName();
            File seedFile = new File(_metaRoot, key + ".remoteevent.json");

            Stream.writeFully(seedFile, Serialisation.serialise(instance));

        } catch (Exception exc) {
            // ignore
        }
    }    
    
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
        for (NodelServerAction entry : _localActions.values()) {
            _logger.info("Releasing server action " + entry.getNodelPoint());
            entry.close();
        }
        _localActions.clear();
        
        // release previous local events
        for (NodelServerEvent entry : _localEvents.values()) {
            _logger.info("Releasing server event " + entry.getNodelPoint());
            entry.close();
        }
        _localEvents.clear();

        // release the dummy local binding (if it was used)
        if (_dummyBinding != null) {
            _logger.info("Releasing dummy local binding.");
            _dummyBinding.close();

            _dummyBinding = null;
        }

        // release previous events
        for (NodelClientEvent event : _remoteEvents.values()) {
            _logger.info("Releasing client event " + event.getNodelPoint());
            event.close();
        }
        _remoteEvents.clear();        
        
        // release previous events
        for (NodelClientAction action : _remoteActions.values()) {
            _logger.info("Releasing client action " + action.getNodelPoint());
            action.close();
        }
        _remoteActions.clear();
        
        // release previous params
        for (ParameterEntry entry : _parameters.values()) {
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
    
    /**
     * The remote actions
     */
    protected Map<SimpleName, NodelClientAction> _remoteActions = new LinkedHashMap<SimpleName, NodelClientAction>();
    
    @Service(name = "remoteActions", title = "Remote actions", genericClassA = SimpleName.class, genericClassB = NodelClientAction.class)
    public Map<SimpleName, NodelClientAction> getRemoteActions() {
        return _remoteActions;
    }

    /**
     * The remote events
     */
    protected Map<SimpleName, NodelClientEvent> _remoteEvents = new LinkedHashMap<SimpleName, NodelClientEvent>();
    
    @Service(name = "remoteEvents", title = "Remote events", genericClassA = SimpleName.class, genericClassB = NodelClientEvent.class)
    public Map<SimpleName, NodelClientEvent> getRemoteEvents() {
        return _remoteEvents;
    }

    /**
     * Add a remote action (by subclass)
     */
    protected void addRemoteAction(NodelClientAction remoteAction, SimpleName suggestedNode, SimpleName suggestedAction) {
        SimpleName remoteActionName = remoteAction.getName();
        
        // look up the value first
        Map<SimpleName, ActionValue> actionValues = _config.remoteBindingValues.actions;
        ActionValue actionValue = actionValues.get(remoteAction.getName());
        
        SimpleName node = null;
        SimpleName action = null;
        
        if (actionValue != null) {
            node = actionValue.node;
            action = actionValue.action;
        } else {
            // optionally use suggestions
            if (suggestedNode != null || suggestedAction != null) {
                actionValue = new ActionValue();
                actionValue.node = suggestedNode;
                actionValue.action = suggestedAction;
                
                // 'fake' the values
                actionValues.put(remoteAction.getName(), actionValue);
            }
        }

        // ensure a section is already reserved for this remote action
        Map<SimpleName, NodelActionInfo> remoteActionBindings = _bindings.remote.actions;
        
        // (check if same name already being used)
        if (remoteActionBindings.containsKey(remoteActionName))
            throw new IllegalStateException("Remote action '" + remoteActionName + "' already exists.");
        
        // create the action info required for the remote binding info structure
        NodelActionInfo actionInfo = (NodelActionInfo) Serialisation.coerce(NodelActionInfo.class, remoteAction.getMetadata());
        remoteActionBindings.put(remoteActionName, actionInfo);

        // set the node and action values
        remoteAction.setNodeAndAction(node, action);
        
        // make sure it gets cleaned up later
        _remoteActions.put(remoteAction.getName(), remoteAction);

        remoteAction.registerActionInterest();
    }
    

    /**
     * Prepares a remote event before its added using any suggestion parameters.
     */
    protected void prepareRemoteEvent(final NodelClientEvent remoteEvent, SimpleName suggestedNode, SimpleName suggestedEvent) {
        SimpleName remoteEventName = remoteEvent.getName();
        
        // look up the value first
        Map<SimpleName, EventValue> eventValues = _config.remoteBindingValues.events;
        EventValue eventValue = eventValues.get(remoteEvent.getName());
        
        SimpleName node = null;
        SimpleName event = null;
        
        if (eventValue != null) {
            node = eventValue.node;
            event = eventValue.event;
        } else {
            // optionally use suggestions
            if (suggestedNode != null || suggestedEvent != null) {
                eventValue = new EventValue();
                eventValue.node = suggestedNode;
                eventValue.event = suggestedEvent;
                
                // 'fake' the values
                eventValues.put(remoteEvent.getName(), eventValue);                
            }
        }

        // ensure a section is already reserved for this remote action
        Map<SimpleName, NodelEventInfo> remoteEventBindings = _bindings.remote.events;
        
        // (check if same name already being used)
        if (remoteEventBindings.containsKey(remoteEventName))
            throw new IllegalStateException("Remote action '" + remoteEventName + "' already exists.");
        
        // create the event info required for the remote binding info structure
        NodelEventInfo eventInfo = (NodelEventInfo) Serialisation.coerce(NodelEventInfo.class, remoteEvent.getMetadata());
        remoteEventBindings.put(remoteEventName, eventInfo);

        // set the node and event values
        remoteEvent.setNodeAndEvent(node, event);        
    }
    
    
    /**
     * Add a remote event (by subclass)
     */
    protected void addRemoteEvent(final NodelClientEvent remoteEvent) {
        // seed the event with some data if it exists
        String key = remoteEvent.getName().getReducedForMatchingName();
        
        File seedFile = new File(_metaRoot, key + ".remoteevent.json");

        ArgInstance seed = null;

        if (seedFile.exists()) {
            try {
                seed = (ArgInstance) Serialisation.deserialise(ArgInstance.class, Stream.readFully(seedFile));
            } catch (Exception e) {
                // ignore
            }
        }

        remoteEvent.seedAndPersist(seed, new Handler.H1<ArgInstance>() {

            @Override
            public void handle(ArgInstance instance) {
                persistEventArg(remoteEvent, instance);
            }

        });

        // make sure it gets cleaned up later
        _remoteEvents.put(remoteEvent.getName(), remoteEvent);

        remoteEvent.registerInterest();
    }
            
    /**
     * The parameters
     */
    protected Map<SimpleName, ParameterEntry> _parameters = new LinkedHashMap<>();
    
    public class ParameterEntry {

        public SimpleName name;
        
        public Object value;

        public ParameterEntry(SimpleName name, Object value) {
            this.name = name;
            this.value = value;
        }
    }
    
    /**
     * Public access to parameters. 
     */
    public Map<SimpleName, ParameterEntry> getParameters() {
        return _parameters;
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
    
    /**
     * Must be called by subclasses
     */
    public void close() {
        _closed = true;
        
        Stream.safeCloseCloseables(_localActions.values());
        Stream.safeCloseCloseables(_localEvents.values());

        synchronized (s_repo) {
            s_repo.remove(_name);
        }
    }
    
    public static BaseNode getNode(SimpleName node) {
        synchronized (s_repo) {
            return s_repo.get(node);
        }
    }

    public static Map<SimpleName, BaseNode> getNodes() {
        synchronized(s_repo) {
            return Collections.unmodifiableMap(s_repo);
        }
    }

}
