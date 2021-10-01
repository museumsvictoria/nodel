package org.nodel.toolkit;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;

import org.joda.time.DateTime;
import org.nodel.Handler;
import org.nodel.SimpleName;
import org.nodel.Handler.H0;
import org.nodel.Handler.H1;
import org.nodel.Handler.H2;
import org.nodel.Strings;
import org.nodel.core.ActionRequestHandler;
import org.nodel.core.BindingState;
import org.nodel.core.Nodel;
import org.nodel.core.NodelClientAction;
import org.nodel.core.NodelClientEvent;
import org.nodel.core.NodelEventHandler;
import org.nodel.core.NodelServerAction;
import org.nodel.core.NodelServerEvent;
import org.nodel.host.BaseDynamicNode;
import org.nodel.host.Binding;
import org.nodel.host.LogEntry;
import org.nodel.host.BaseNode.ParameterEntry;
import org.nodel.io.Stream;
import org.nodel.net.NodelHTTPClient;
import org.nodel.net.NodelHttpClientProvider;
import org.nodel.reflection.Objects;
import org.nodel.reflection.Serialisation;
import org.nodel.threading.CallbackQueue;
import org.nodel.threading.ThreadPool;
import org.nodel.threading.TimerTask;
import org.nodel.threading.Timers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A simple toolkit aimed within a managed, shared scripting environment.
 */
public class ManagedToolkit {

    /**
     * (logging related)
     */
    private static AtomicLong s_instanceCounter = new AtomicLong();

    /**
     * The shared timers.
     */
    private static Timers s_timers = new Timers("Toolkit");

    /**
     * The shared thread-pool.
     */
    private static ThreadPool s_threadPool = new ThreadPool("Toolkit", 32);
    
    /**
     * (logging related)
     */
    private Logger _logger = LoggerFactory.getLogger(String.format("%s.instance%d", this.getClass().getName(), s_instanceCounter.getAndIncrement()));    

    /**
     * (for locking / synchronisation)
     */
    private Object _lock = new Object();
    
    /**
     * Keeps track of the number of threads in use.
     */
    private AtomicLong _threadsInUse = new AtomicLong();
    
    /**
     * A callback queue for orderly, predictable handling of callbacks.
     */
    private CallbackQueue _callbackQueue;

    /**
     * The node name for debugging purposes.
     */
    private BaseDynamicNode _node;

    /**
     * Permanently closed.
     */
    private boolean _closed;
    
    /**
     * The console interface
     */
    private Console.Interface _console = Console.NullConsole();

    /**
     * The exception handler, with context
     */
    private Handler.H2<String, Exception> _exceptionHandler;
    
    /**
     * ('exceptionHandler' with context)
     */
    private H1<Exception> _actionExceptionHandler = createExceptionHandlerWithContext("action");
    
    /**
     * ('exceptionHandler' with context)
     */    
    private H1<Exception> _remoteEventExceptionHandler = createExceptionHandlerWithContext("remoteEvent");
    
    /**
     * ('exceptionHandler' with context)
     */
    private H1<Exception> _callDelayedExceptionHandler = createExceptionHandlerWithContext("callDelayed");
    
    /**
     * ('exceptionHandler' with context)
     */
    private H1<Exception> _timerExceptionHandler = createExceptionHandlerWithContext("timer");
    
    /**
     * ('exceptionHandler' with context)
     */
    private H1<Exception> _tcpExceptionHandler = createExceptionHandlerWithContext("tcp");
    
    /**
     * ('exceptionHandler' with context)
     */
    private H1<Exception> _requestQueueExceptionHandler = createExceptionHandlerWithContext("requestQueue");    
    
    /**
     * ('exceptionHandler' with context)
     */
    private H1<Exception> _udpExceptionHandler = createExceptionHandlerWithContext("udp");
    
    /**
     * ('exceptionHandler' with context)
     */
    private H1<Exception> _processExceptionHandler = createExceptionHandlerWithContext("process");
    
    /**
     * ('exceptionHandler' with context)
     */
    private H1<Exception> _emitExceptionHandler = createExceptionHandlerWithContext("emit");    
    
    /**
     * Call from within calling thread, usually sets up the thread-state environment.
     */
    private H0 _threadStateHandler;
    
    /**
     * Returns a custom console or 'Null' console.
     */
    public Console.Interface getConsole() {
        return _console;
    }    

    /**
     * (used in '_timerTasks'; may be extended in future)
     */
    private class TimerEntry {

        public TimerTask timerTask;

    }

    /**
     * Holds all the managed delayed calls
     */
    private Set<TimerEntry> _delayCalls = new HashSet<TimerEntry>();
    
    /**
     * All the managed timers.
     */
    private Set<ManagedTimer> _timers = new HashSet<ManagedTimer>();
    
    /**
     * Holds all the TCP connections
     */
    private Set<ManagedTCP> _tcpConnections = new HashSet<ManagedTCP>();
    
    /**
     * Holds all the UDP sockets
     */
    private Set<ManagedUDP> _udpSockets = new HashSet<ManagedUDP>();

    /**
     * Holds all the SSH connections
     */
    private Set<ManagedSSH> _sshConnections = new HashSet<ManagedSSH>();
    
    /**
     * Holds all the long living managed processes
     */
    private Set<ManagedProcess> _processes = new HashSet<ManagedProcess>();
    
    /**
     * Holds all the quick processes
     */
    private Set<QuickProcess> _quickProcesses = new HashSet<QuickProcess>();
    
    /**
     * Nodel actions
     */
    private Set<ManagedNode> _managedNodes = new HashSet<ManagedNode>();
    
    /**
     * Whether or not this toolkit has been enabled (i.e. TCP connections, timers, etc are activated)
     */
    private boolean _enabled;

    /**
     * An atomically incrementing long integer.
     */
    private AtomicLong _sequenceCounter = new AtomicLong(0);

    /**
     * (constructor)
     */
    public ManagedToolkit(BaseDynamicNode node) {
        _node = node;
    }
    
    /**
     * Attaches a custom console.
     */
    public ManagedToolkit attachConsole(Console.Interface value) {
        _console = value;
        return this;
    }

    /**
     * An exception-handler when invocations within thread-pools fail.
     */
    public ManagedToolkit setExceptionHandler(Handler.H2<String, Exception> handler) {
        _exceptionHandler = handler;
        
        return this;
    }
    
    /**
     * Handler which gets called from the executing threads, usually use to establish thread-state
     * environment.
     */
    public ManagedToolkit setThreadStateHandler(H0 handler) {
        _threadStateHandler = handler;
        
        return this;
    }
    
    /**
     * Sets the callback handler for orderly callback handling. 
     */
    public ManagedToolkit setCallbackHandler(CallbackQueue handler) {
        _callbackQueue = handler;
        
        return this;
    }    
    
    /**
     * Calls a function (optionally delayed) in an optionally thread-safe way and gets its result or exception asynchronously.
     */
    public <T> ManagedToolkit call(final boolean threadSafe, 
                                   final Callable<T> func, 
                                   long delay, 
                                   final H1<T> onComplete, 
                                   final H1<Exception> onError) {
        if (func == null)
            throw new IllegalArgumentException("No function provided.");

        synchronized (_lock) {
            final TimerEntry entry = new TimerEntry();
            entry.timerTask = s_timers.schedule(s_threadPool, new TimerTask() {

                @Override
                public void run() {
                    _threadsInUse.incrementAndGet();
                    
                    try {
                        // call the thread-state handler to allow thread state initialisation
                        _threadStateHandler.handle();
                    
                        // functions are considered long running

                        T result;
                        if (threadSafe)
                            result = _callbackQueue.handle(func);
                        else
                            result = func.call();

                        // call the 'onComplete' callback if it exists
                        _callbackQueue.handle(onComplete, result, _callDelayedExceptionHandler);

                    } catch (Exception th) {
                        if (onError != null)
                            _callbackQueue.handle(onError, th, _callDelayedExceptionHandler);
                        else
                            // call the global exception handler
                            _callDelayedExceptionHandler.handle(th);

                    } finally {
                        synchronized (_lock) {
                            // doesn't matter if doesn't exist
                            _delayCalls.remove(entry);
                        }
                        
                        _threadsInUse.decrementAndGet();
                    }
                }

            }, delay);

            _delayCalls.add(entry);
        }
        
        return this;
    }
    
    public void releaseCalls() {
        synchronized (_lock) {
            // cancel each timer task
            for (TimerEntry entry : _delayCalls) {
                TimerTask timerTask = entry.timerTask;
                if (timerTask != null)
                    timerTask.cancel();
            }
            _delayCalls.clear();
        }
    }
    
    /**
     * Creates a repeating timer.
     */
    public ManagedTimer createTimer(H0 func, long delay, long interval, boolean stopped) {
        synchronized (_lock) {
            // create a timer (will be stopped)
            ManagedTimer timer = new ManagedTimer(func, stopped, _threadStateHandler, s_timers, s_threadPool, _timerExceptionHandler, _callbackQueue);
            
            timer.setDelayAndInterval(delay, interval);
            
            _timers.add(timer);
            
            // if created after normal init, start (if no 'stopped' flag)
            if (_enabled && !stopped)
                timer.start();
            
            // otherwise .start() will be called from 'enable'
            
            return timer;
        }
    }

    /**
     * Safely clears all created timers.
     */
    public void releaseTimers() {
        synchronized (_lock) {
            for (ManagedTimer timer : _timers) {
                Stream.safeClose(timer);
            }
            _timers.clear();
        }
    }
    
    /**
     * Constructs a managed TCP connection.
     */
    public ManagedTCP createTCP(String dest,
                                H0 onConnected,
                                H1<String> onReceived, 
                                H1<String> onSent,
                                H0 onDisconnected,
                                H0 onTimeout,
                                String sendDelimiters,
                                String receiveDelimiters,
                                String binaryStartStopFlags) {
        // create a new TCP connection providing this environment's facilities
        ManagedTCP tcp = new ManagedTCP(_node, dest, _threadStateHandler, _tcpExceptionHandler, _callbackQueue, s_threadPool, s_timers);
        
        // set up the callback handlers as provided by the user
        tcp.setConnectedHandler(onConnected);
        tcp.setReceivedHandler(onReceived);
        tcp.setSentHandler(onSent);
        tcp.setDisconnectedHandler(onDisconnected);
        tcp.setTimeoutHandler(onTimeout);
        tcp.setSendDelimeters(sendDelimiters);
        tcp.setReceiveDelimeters(receiveDelimiters);
        tcp.setBinaryStartStopFlags(binaryStartStopFlags);
        
        synchronized(_lock) {
            if (_closed)
                Stream.safeClose(tcp);
            else
                _tcpConnections.add(tcp);
        }
        
        return tcp;
    }
    
    public ManagedUDP createUDP(String source,
                                String dest,
                                H0 onReady, 
                                H2<String, String> onReceived, 
                                H1<String> onSent,
                                String intf) {
        ManagedUDP udp = new ManagedUDP(_node, source, dest, _threadStateHandler, _udpExceptionHandler, _callbackQueue, s_threadPool, s_timers);
        
        udp.setReadyHandler(onReady);
        udp.setReceivedHandler(onReceived);
        udp.setSentHandler(onSent);
        udp.setIntf(intf);
        
        synchronized(_lock) {
            if (_closed)
                Stream.safeClose(udp);
            else
                _udpSockets.add(udp);
        }
        
        return udp;        
    }

    /**
     * Constructs a managed SSH connection.
     */
    public ManagedSSH createSSH(String dest,
                                H0 onConnected,
                                H1<String> onReceived,
                                H1<String> onSent,
                                H0 onDisconnected,
                                H0 onTimeout,
                                String sendDelimiters,
                                String receiveDelimiters,
                                String username,
                                String password,
                                boolean disableEcho) {
        // create a new TCP connection providing this environment's facilities
        ManagedSSH ssh = new ManagedSSH(_node, dest, _threadStateHandler, _tcpExceptionHandler, _callbackQueue, s_threadPool, s_timers);

        // set up the callback handlers as provided by the user
        ssh.setConnectedHandler(onConnected);
        ssh.setReceivedHandler(onReceived);
        ssh.setSentHandler(onSent);
        ssh.setDisconnectedHandler(onDisconnected);
        ssh.setTimeoutHandler(onTimeout);
        ssh.setSendDelimeters(sendDelimiters);
        ssh.setReceiveDelimiters(receiveDelimiters);

        ssh.setDisableEcho(disableEcho);
        ssh.setUsername(username);
        ssh.setPassword(password);

        synchronized (_lock) {
            if (_closed)
                Stream.safeClose(ssh);
            else
                _sshConnections.add(ssh);
        }

        return ssh;
    }

    /**
     * Constructs a managed OS process.
     */
    public ManagedProcess createProcess(List<String> command,
                                H0 onStarted,
                                H1<String> onOut, 
                                H1<String> onIn,
                                H1<String> onErr,
                                H1<Integer> onStopped,
                                H0 onTimeout,
                                String sendDelimiters,
                                String receiveDelimiters,
                                String working,
                                boolean mergestderr,
                                Map<String, String> env) {
        ManagedProcess process = new ManagedProcess(_node, command, _threadStateHandler, _processExceptionHandler, _callbackQueue, s_threadPool, s_timers);
        
        // set up the callback handlers as provided by the user
        process.setStartedHandler(onStarted);
        process.setOutHandler(onOut);
        process.setInHandler(onIn);
        process.setErrHandler(onErr);
        process.setStoppedHandler(onStopped);
        process.setTimeoutHandler(onTimeout);
        
        // set general arguments
        process.setSendDelimeters(sendDelimiters);
        process.setReceiveDelimeters(receiveDelimiters);
        process.setWorking(working);
        process.setMergeError(mergestderr);
        process.setEnv(env);
        
        synchronized(_lock) {
            if (_closed)
                Stream.safeClose(process);
            else
                _processes.add(process);
        }
        
        // if the toolkit has already been initialised, start the process
        if (_enabled) {
            process.init();
        }

        return process;
    }
    
    public QuickProcess createQuickProcess(List<String> command,
            String stdinPush,
            H1<Integer> onStarted,
            H1<QuickProcess.FinishedArg> onFinished,
            long timeout,
            String working,
            boolean mergeErr,
            Map<String, String> env) {

        final QuickProcess quickProcess = new QuickProcess(_threadStateHandler, s_threadPool, s_timers, _processExceptionHandler, _node, command, stdinPush, onStarted, onFinished, timeout, working, mergeErr, env);
        quickProcess.setClosedHandler(new Handler.H0() {

            @Override
            public void handle() {
                synchronized (_lock) {
                    _quickProcesses.remove(quickProcess);
                }
            }

        });
        
        // all wired, can begin...
        quickProcess.go();

        synchronized (_lock) {
            if (_closed)
                Stream.safeClose(quickProcess);
            else
                _quickProcesses.add(quickProcess);
        }

        return quickProcess;
    }
    
    /**
     * Constructs a managed TCP connection.
     */
    public RequestQueue createRequestQueue(
                                H1<Object> onReceived, 
                                H0 onSent,
                                H0 onTimeout) {
        RequestQueue requestQueue = new RequestQueue(_node, _threadStateHandler, _requestQueueExceptionHandler, _callbackQueue, s_threadPool, s_timers);
        
        // set up the callback handlers as provided by the user
        requestQueue.setReceivedHandler(onReceived);
        requestQueue.setSentHandler(onSent);
        requestQueue.setTimeoutHandler(onTimeout);
        
        synchronized(_lock) {
            if (_closed)
                Stream.safeClose(requestQueue);
        }
        
        return requestQueue;
    }    
    
    /**
     * Releases all TCP connections
     */
    public void releaseTCPs() {
        synchronized (_lock) {
            // close all connections
            for (ManagedTCP conn : _tcpConnections)
                Stream.safeClose(conn);

            _tcpConnections.clear();
        }
    }
    
    /**
     * Releases all UDP connections
     */
    public void releaseUDPs() {
        synchronized (_lock) {
            // close all connections
            for (ManagedUDP socket : _udpSockets)
                Stream.safeClose(socket);

            _udpSockets.clear();
        }
    }
    
    /**
     * Releases all processes
     */
    public void releaseProcesses() {
        synchronized (_lock) {
            // close all long living processes (safe copy)
            for (ManagedProcess process : new ArrayList<>(_processes))
                Stream.safeClose(process);

            _processes.clear();
            
            // and quick processes (safe copy)
            for (QuickProcess quickProcess: new ArrayList<>(_quickProcesses))
                Stream.safeClose(quickProcess);
            
            _quickProcesses.clear();
        }
    }    
    
    /**
     * Releases all secure shells
     */
    public void releaseSecureShells() {
        synchronized (_lock) {
            // close all secure shells
            for (ManagedSSH ssh : _sshConnections)
                Stream.safeClose(ssh);

            _sshConnections.clear();
        }
    }

    /**
     * Creates a managed node
     */
    public ManagedNode createNode(String name) {
        if (Strings.isBlank(name))
            throw new IllegalArgumentException("Name cannot be empty");

        synchronized (_lock) {
            ManagedNode node = new ManagedNode(new SimpleName(name), _callbackQueue, _threadStateHandler);

            _managedNodes.add(node);

            return node;
        }
    }
    
    /**
     * Creates a managed node (sub node)
     */
    public ManagedNode createSubnode(String suffix) {
        if (Strings.isBlank(suffix))
            throw new IllegalArgumentException("Suffix cannot be empty");

        synchronized (_lock) {
            ManagedNode node = new ManagedNode(new SimpleName(Nodel.reduce(_node.getName().getOriginalName(), true) + " " + suffix), _callbackQueue, _threadStateHandler);

            _managedNodes.add(node);

            return node;
        }
    }
    
    /**
     * Removes a previously created managed node, fully releases all of its resources.
     */
    public void releaseNode(ManagedNode node) {
        if (node == null)
            throw new IllegalArgumentException("Node is missing");
        
        synchronized(_lock) {
            node.close();
            
            _managedNodes.remove(node);
        }
    }

    public void releaseNodes() {
        // close all managed nodes
        for (ManagedNode node : _managedNodes) {
            node.close();
        }
        _managedNodes.clear();
    }
    
    public NodelServerAction createAction(String actionName, final Handler.H1<Object> actionFunction, Binding metadata) {
        synchronized (_lock) {
            if (_closed)
                throw new IllegalStateException("Node is closed.");
            
            final NodelServerAction action = new NodelServerAction(_node.getName(), new SimpleName(Nodel.reduce(actionName)), metadata);
            action.registerAction(new ActionRequestHandler() {

                @Override
                public void handleActionRequest(Object arg) {
                    _threadStateHandler.handle();

                    _node.injectLog(DateTime.now(), LogEntry.Source.local, LogEntry.Type.action, action.getAction(), arg);

                    _callbackQueue.handle(actionFunction, arg, _actionExceptionHandler);
                }

            });

            _node.injectLocalAction(action);
            
            return action;
        }
    }
    
    /**
     * (overloaded - metadata as a map)
     */
    public NodelServerAction createAction(String actionName, final Handler.H1<Object> actionFunction, Map<String, Object> metadata) {
        return createAction(actionName, actionFunction, (Binding) Serialisation.coerce(Binding.class, metadata));
    }
    
    public void releaseAction(NodelServerAction action) {
        if (action == null)
            throw new IllegalArgumentException("No action provided");
        
        synchronized(_lock) {
            action.close();
            
            _node.extractLocalAction(action);
        }
    }
    
    /**
     * Looks up a Nodel action from this Node.
     */
    public NodelServerAction getLocalAction(String name) {
        return _node.getLocalActions().get(new SimpleName(name));
    }
    
    public NodelServerEvent createEvent(String eventName, Binding metadata) {
        synchronized (_lock) {
            if (_closed)
                throw new IllegalStateException("Node is closed.");

            NodelServerEvent event = new NodelServerEvent(_node.getName(), new SimpleName(Nodel.reduce(eventName)), metadata);
            event.setThreadingEnvironment(_callbackQueue, _threadStateHandler, _emitExceptionHandler);
            _node.injectLocalEvent(event);

            return event;
        }
    }
    
    public NodelServerEvent createEvent(String eventName, Map<String, Object> metadata) {
        return createEvent(eventName, (Binding) Serialisation.coerce(Binding.class, metadata));
    }
    
    public void releaseEvent(NodelServerEvent event) {
        if (event == null)
            throw new IllegalArgumentException("No event provided");
        
        synchronized (_lock) {
            event.close();

            _node.extractLocalEvent(event);
        }
    }
    
    /**
     * Creates a remote action.
     */
    public NodelClientAction createRemoteAction(String actionName, Map<String, Object> metadata, String suggestedNodeName, String suggestedActionName) {
        return createRemoteAction(actionName, (Binding) Serialisation.coerce(Binding.class, metadata), suggestedNodeName, suggestedActionName);
    }
    
    /**
     * Looks up a Nodel event from this Node.
     */
    public NodelServerEvent getLocalEvent(String name) {
        return _node.getLocalEvents().get(new SimpleName(name));
    }    

    /**
     * Creates a remote action.
     */
    public NodelClientAction createRemoteAction(String actionName, Binding metadata, String suggestedNodeName, String suggestedActionName) {
        synchronized (_lock) {
            if (_closed)
                throw new IllegalStateException("Node is closed.");
            
            final SimpleName action = new SimpleName(actionName);
            
            SimpleName suggestedNode = (suggestedNodeName != null ? new SimpleName(suggestedNodeName) : null);
            SimpleName suggestedAction = (suggestedActionName != null ? new SimpleName(suggestedActionName) : null);
            
            if (metadata == null)
                metadata = new Binding();
            
            if (Strings.isEmpty(metadata.title)) // 'isEmpty' in case blank is deliberate
                metadata.title = actionName;

            final NodelClientAction clientAction = new NodelClientAction(new SimpleName(actionName), metadata, null, null);
            
            clientAction.attachMonitor(new Handler.H1<Object>() {
                
                @Override
                public void handle(Object arg) {
                    if (clientAction.isUnbound())
                        _node.injectLog(DateTime.now(), LogEntry.Source.unbound, LogEntry.Type.action, action, arg);                    
                    else
                        _node.injectLog(DateTime.now(), LogEntry.Source.remote, LogEntry.Type.action, action, arg);
                }
                
            });
            clientAction.attachWiredStatusChanged(new Handler.H1<BindingState>() {
                
                @Override
                public void handle(BindingState status) {
                    _logger.info("Action binding status: {} - '{}'", action.getReducedName(), status);
                    
                    _node.injectLog(DateTime.now(), LogEntry.Source.remote, LogEntry.Type.actionBinding, action, status);
                }
                
            });            
            
            _node.injectRemoteAction(clientAction, suggestedNode, suggestedAction);

            return clientAction;
        }
    }
    
    /**
     * Looks up a Nodel remote action from this Node.
     */
    public NodelClientAction getRemoteAction(String name) {
        return _node.getRemoteActions().get(new SimpleName(name));
    }

    /**
     * Creates a remote event.
     */
    public NodelClientEvent createRemoteEvent(String eventName, Handler.H1<Object> eventFunction, Map<String, Object> metadata, String suggestedNodeName, String suggestedEventName) {
        return createRemoteEvent(eventName, eventFunction, (Binding) Serialisation.coerce(Binding.class, metadata), suggestedNodeName, suggestedEventName);
    }

    /**
     * Creates a remote action.
     */
    public NodelClientEvent createRemoteEvent(String eventName, final Handler.H1<Object> eventFunction, Binding metadata, String suggestedNodeName, String suggestedEventName) {
        synchronized (_lock) {
            if (_closed)
                throw new IllegalStateException("Node is closed.");
            
            final SimpleName event = new SimpleName(eventName);
            
            SimpleName suggestedNode = (suggestedNodeName != null ? new SimpleName(suggestedNodeName) : null);
            SimpleName suggestedEvent = (suggestedEventName != null ? new SimpleName(suggestedEventName) : null);            
            
            if (metadata == null)
                metadata = new Binding();
            
            if (Strings.isEmpty(metadata.title)) // 'isEmpty' in case blank is deliberate
                metadata.title = eventName;

            final NodelClientEvent clientEvent = new NodelClientEvent(new SimpleName(eventName), metadata, null, null);
            clientEvent.setThreadingEnvironment(_callbackQueue, _threadStateHandler, _emitExceptionHandler);
            
            clientEvent.setHandler(new NodelEventHandler() {
                
                @Override
                public void handleEvent(SimpleName node, SimpleName event, Object arg) {
                    _node.injectLog(DateTime.now(), LogEntry.Source.remote, LogEntry.Type.event, event, arg);
                    
                    _threadStateHandler.handle();

                    _callbackQueue.handle(eventFunction, arg, _remoteEventExceptionHandler);                    
                }
                
            });
            
            clientEvent.addBindingStateHandler(new Handler.H1<BindingState>() {
                
                @Override
                public void handle(BindingState status) {
                    _node.injectLog(DateTime.now(), LogEntry.Source.remote, LogEntry.Type.eventBinding, event, status);
                    
                    _logger.info("Event binding status: {} - '{}'", event.getReducedName(), status);
                }
                
            });             
            
            _node.injectRemoteEvent(clientEvent, suggestedNode, suggestedEvent);

            return clientEvent;
        }
    }
    
    /**
     * Looks up a Nodel remote event from this Node.
     */
    public NodelClientEvent getRemoteEvent(String name) {
        return _node.getRemoteEvents().get(new SimpleName(name));
    }
    
    /**
     * Looks up a parameter value using SimpleName resolution.
     */
    public Object lookupParameter(String name) {
        ParameterEntry result = _node.getParameters().get(new SimpleName(name));
        return (result != null ? result.value : null);
    }

    /**
     * Kicks off any resources set up within this toolkit like TCP connections, timers, etc.
     */
    public void enable() {
        synchronized (_lock) {
            if (_enabled)
                return;

            _enabled = true;
            
            for(ManagedTimer timer: _timers) {
                // start only those that haven't opted out
                if (!timer.getStoppedAtFirst() && (timer.getDelay() > 0 || timer.getInterval() > 0)) {
                    timer.start();
                }
            }

            for (ManagedTCP tcp : _tcpConnections) {
                tcp.start();
            }

            for (ManagedUDP udp : _udpSockets) {
                udp.start();
            }

            for (ManagedSSH ssh : _sshConnections) {
                ssh.start();
            }

            for (ManagedProcess process : _processes) {
                process.init();
            }
        }
    }
    
    // Safe URL timeouts are optimised for servers that are likely available and responsive.
    
    /**
     * The re-useable client for this toolkit instance.
     * (lazily created)
     */
    private NodelHTTPClient _httpClient;
    
    /**
     * Release the http client if it was created.
     */
    private void releaseHttpClient() {
        if (_httpClient != null) {
            try {
                synchronized (_lock) {
                    _httpClient.close();
                }
                
            } catch (Exception exc) {
                _logger.warn("HTTP client connection manager may not have shutdown cleanly", exc);
            }
        }
    }
    
    /**
     * Lazily gets the HTTP client
     */
    public NodelHTTPClient getHttpClient() {
        if (_httpClient == null) {
            synchronized (_lock) {
                if (_httpClient == null)
                    _httpClient = NodelHttpClientProvider.instance().create();
            }
        }
        return _httpClient;
    }
    
    
    /**
     * A very simple URL getter. queryArgs, contentType, postData are all optional.
     * 
     * Safe timeouts are used to avoid non-responsive servers being able to hold up connections indefinitely.
     */
    public String getURL(String urlStr, String method, Map<String, String> query, String username, String password, Map<String, String> headers, String contentType, String post,
            Integer connectTimeout, Integer readTimeout, boolean resultWithHeaders) throws IOException {
        return getHttpClient().makeSimpleRequest(urlStr, method, query, username, password, headers, contentType, post, connectTimeout, readTimeout);
    }

    /**
     * Permanently cleans up this instance of the toolkit and related
     * resources.
     */
    public void shutdown() {
        synchronized (_lock) {
            if (_closed)
                return;
            
            _logger.info("Closing toolkit.");

            _closed = true;

            releaseCalls();
            
            releaseTimers();
            
            releaseTCPs();
            
            releaseUDPs();
            
            releaseProcesses();
            
            releaseSecureShells();

            releaseNodes();
            
            releaseHttpClient();
        }
    }

    /**
     * Encodes simple objects into a JSON string.
     */
    public String jsonEncode(Object obj) {
        return Serialisation.serialise(obj);
    }
    
    /**
     * Decodes JSON strings into plain native objects.
     */
    public Object jsonDecode(String str) {
        return Serialisation.deserialise(Object.class, str);
    }
    
    /**
     * Returns an atomically incrementing long integer.
     */
    public long nextSequenceNumber() {
        return _sequenceCounter.getAndIncrement();
    }
    
    /**
     * Returns a high-resolution atomically incrementing system-clock in millis (can wrap).
     */
    public long systemClockInMillis() {
        return System.nanoTime() / 1000000; 
    }
    
    /**
     * Checks whether two objects are effectively of the same value.
     */
    public boolean sameValue(Object obj1, Object obj2) {
        return Objects.sameValue(obj1, obj2);
    }

    /**
     * 'now' as a DateTime instance.
     */
    public DateTime dateNow() {
        return DateTime.now();
    }
    
    /**
     * A DateTime instance from composite date / time values.
     */
    public DateTime dateAt(int year, int month, int day, int hour, int minute, int second, int millisecond) {
        return new DateTime(year, month, day, hour, minute, second, millisecond);
    }
    
    /**
     * A DateTime instance from 1970-based millis.
     */
    public DateTime dateAtInstant(long millis) {
        return new DateTime(millis);
    }    

    /**
     * Parses a DateTime from the specified string.
     */
    public DateTime parseDate(String str) {
        return DateTime.parse(str);
    }

    /**
     * (convenience function)
     */
    private H1<Exception> createExceptionHandlerWithContext(final String context) {
        return new H1<Exception>() {

            @Override
            public void handle(Exception value) {
                Handler.tryHandle(_exceptionHandler, context, value);
            }

        };
    }
    
}
