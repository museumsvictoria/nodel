package org.nodel.jyhost;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import org.joda.time.DateTime;
import org.nodel.DateTimes;
import org.nodel.Exceptions;
import org.nodel.Handler;
import org.nodel.Handler.H0;
import org.nodel.Handler.H1;
import org.nodel.Handler.H2;
import org.nodel.SimpleName;
import org.nodel.Strings;
import org.nodel.Threads;
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
import org.nodel.host.Bindings;
import org.nodel.host.LocalBindings;
import org.nodel.host.LogEntry;
import org.nodel.host.NodeConfig;
import org.nodel.host.NodelActionInfo;
import org.nodel.host.NodelEventInfo;
import org.nodel.host.OperationPendingException;
import org.nodel.host.ParamValues;
import org.nodel.host.ParameterBinding;
import org.nodel.host.ParameterBindings;
import org.nodel.host.RemoteBindingValues;
import org.nodel.host.RemoteBindings;
import org.nodel.io.Files;
import org.nodel.io.Stream;
import org.nodel.reflection.Schema;
import org.nodel.reflection.Param;
import org.nodel.reflection.Serialisation;
import org.nodel.reflection.Service;
import org.nodel.reflection.Value;
import org.nodel.threading.CallbackQueue;
import org.nodel.threading.TimerTask;
import org.nodel.toolkit.Console;
import org.nodel.toolkit.ManagedToolkit;
import org.python.core.Py;
import org.python.core.PyBaseCode;
import org.python.core.PyDictionary;
import org.python.core.PyException;
import org.python.core.PyFunction;
import org.python.core.PyObject;
import org.python.core.PyString;
import org.python.core.PySystemState;
import org.python.util.PythonInterpreter;

/**
 * Represents a Python-enabled Node.
 * 
 * Files will be monitored.
 */
public class PyNode extends BaseDynamicNode {
    
    /**
     * Lazy flag to help (but not enforce) avoid overlapping
     * operations.
     */
    private ReentrantLock _busy = new ReentrantLock();
    
    /**
     * When permanently closed (disposed)
     */
    private boolean _closed;
            
    /**
     * The script file.
     */
    private File _scriptFile;
    
    /**
     * The hash used to determine whether the config and / or script files were modified.
     */
    private long _fileModifiedHash;
    
    /**
     * The current Python interpreter.
     */
    private PythonInterpreter _python;
    
    /**
     * This ensures 'exec' and 'main()' calls on the Jython are executed serially.
     * Lock instance can change if deadlocks occur.
     * (WORKAROUND for a Jython loading bug related to the XML parser)
     * (synced on 's_globalLock')
     */
    private static ReentrantLock s_currentGlobalRentrantLock = new ReentrantLock();
    
    /**
     * Used to synchronise 's_globalReentrantLock'. Never changes.
     */
    private static Object s_globalLock = new Object();
    
    /**
     * Used when calling functions in a multithreaded Python environment.
     */
    private AtomicLong _funcSeqNumber = new AtomicLong();
    
    /**
     * For detecting never-ending functions. 
     * Stores the time-in by function name.
     */
    private Map<String, Long> _activeFunctions = new HashMap<String, Long>();
    
    /**
     * The general purpose toolkit related to this node.
     */
    protected ManagedToolkit _toolkit;

    /**
     * (init. in constructor)
     */
    private NodelHost _nodelHost;
    
    public NodelHost getNodelHost() {
        return _nodelHost;
    }
    
    /**
     * Create a new pyNode.
     */
    public PyNode(NodelHost nodelHost, SimpleName name, File root) throws IOException {
        super(name, root);
        
        _nodelHost = nodelHost;

        init();
    }

    /**
     * Returns the Python interpreter related to this node.
     */
    protected PythonInterpreter getPython() {
        return _python;
    }
    
    public void saveConfig(NodeConfig config) throws Exception {
        if (!_busy.tryLock())
            throw new OperationPendingException();
        
        try {
            saveConfig0(config);

        } finally {
            _busy.unlock();
        }
    } // (method)
    
    /**
     * (internal version)
     */
    private void saveConfig0(NodeConfig config) throws Exception {
        try {
            _logger.info("saveConfig called.");

            if (config == null)
                return;

            this.applyConfig(config);

            String configStr = Serialisation.serialise(config, 4);

            Stream.writeFully(_configFile, configStr);

            // make sure it's not triggered again
            _fileModifiedHash = _configFile.lastModified() + _scriptFile.lastModified();
            
            _logger.info("saveConfig completed.");
        } catch (Exception exc) {
            _logger.warn("saveConfig failed.", exc);
            throw exc;
        }        
    }
    
    public class ScriptInfo {
        
        @Value(name = "modified", title = "Modified", desc = "When the script was last modified.")
        public DateTime modified;
        
        @Value(name = "script", title = "Script", desc = "The script itself.")
        public String script;
        
    } // (method)
    
    /**
     * Used to provide the '/script' end point. 
     */
    public class Script {
        
        @Value(name = "scriptInfo", title = "Script info", desc = "Retrieves the script info including the script itself.", treatAsDefaultValue = true)
        public ScriptInfo getScriptInfo() throws IOException {
            ScriptInfo scriptInfo = new ScriptInfo();
            scriptInfo.modified = new DateTime(_scriptFile.lastModified());
            scriptInfo.script = Stream.readFully(_scriptFile); 
            return scriptInfo;
        }
        
        @Service(name = "raw", title = "Raw script", desc = "Retrieves the actual .py script itself.", contentType = "text/plain")
        public String getRawScript() throws IOException {
            return Stream.readFully(_scriptFile);
        }
        
        @Service(name = "save", title = "Save", desc = "Saves the script.")
        public void save(@Param(name = "script", title = "Script", desc = "The script text.") String script) throws IOException {
            // perform a rolling backup
            
            final String scriptFilePrefix = "script_backup_";
            
            // get the list of 'script (backup *.py files)
            File[] files = _root.listFiles(new FileFilter() {
                
                @Override
                public boolean accept(File pathname) {
                    String lc = pathname.getName().toLowerCase();
                    return lc.startsWith(scriptFilePrefix) && lc.endsWith(".py");
                }
                
            });
            
            // sort by last modified
            Arrays.sort(files, new Comparator<File>() {
                
                @Override
                public int compare(File f1, File f2) {
                    long l1 = f1.lastModified();
                    long l2 = f2.lastModified();
                    return (l1 == l2 ? 0 : (l2 > l1 ? 0 : 1));
                }
                
            });
            
            // drop the oldest one if more than 10 are listed
            if (files.length > 5)
                files[0].delete();
            
            // make the backup file
            String timestamp = DateTime.now().toString("YYYY-MM-dd_HHmmssSSS");
            Files.copy(_scriptFile, new File(_root, scriptFilePrefix + timestamp + ".py"));
                
            Stream.writeFully(_scriptFile, script);
            
            // do not update time stamp here, let the monitoring take care of that
            
        } // (method)
        
    } // (class)
    
    /**
     * The script end-point.
     */
    private Script _script = new Script();

    /**
     * The Python "globals"
     * (never null, initialised every new 'python' instance)
     */
    private PyDictionary _globals = new PyDictionary();

    /**
     * Holds the "system state" for this interpreter.
     * (required because of threading)
     */
    private PySystemState _pySystemState;

    /**
     * The thread-state handler
     */
    private H0 _threadStateHandler = new Handler.H0() {

        @Override
        public void handle() {
            Py.setSystemState(_pySystemState);
        }
        
    };
    
    /**
     * A callback queue for orderly, predictable handling of callbacks.
     * (gets recycled)
     */
    private CallbackQueue _callbackQueue;

    /**
     * The exception handler.
     */
    private H2<String, Exception> _exceptionHandler = new Handler.H2<String, Exception>() {

        @Override
        public void handle(String context, Exception th) {
            String message = "(" + context + ") " + th.toString();
            _logger.info(message);
            _errReader.inject(message);
        }

    };

    /**
     * Provides context
     */
    private H1<Exception> _emitExceptionHandler = new H1<Exception>() {

        @Override
        public void handle(Exception value) {
            Handler.tryHandle(_exceptionHandler, "Emitter", value);
        }

    };
    
    /**
     * The Python "globals"
     */
    public PyDictionary getPyGlobals() {
        return _globals;
    }
    
    @Service(name = "script", title = "Script", desc = "The .py script itself and meta-data.")
    public Script getScript() {
        return _script;
    }

    /**
     * Once-off initialisation routine.
     */
    private void init() throws IOException {
        String filePrefix = "nodeConfig";
        
        // dump the bootstrap config schema if it hasn't already been dumped
        Object schema = Schema.getSchemaObject(NodeConfig.class);
        String schemaString = Serialisation.serialise(schema, 4);
        
        // load for the bootstrap config schema file    
        File schemaFile = new File(_root, "_" + filePrefix + "_schema.json");
        String schemaFileString = null;
        if(schemaFile.exists()) {
            schemaFileString = Stream.readFully(schemaFile);
        }
        
        // only write out the schema file if it's different or never been written out
        if(schemaFileString == null || !schemaFileString.equals(schemaString)) {
            Stream.writeFully(schemaFile, schemaString);
        }
        
        // dump the example config if it hasn't already been dumped
        String exampleString = Serialisation.serialise(NodeConfig.Example, 4);
        
        // load for the bootstrap config schema file    
        File exampleFile = new File(_root, "_" + filePrefix + "_example.json");
        String exampleFileString = null;
        if(exampleFile.exists()) {
            exampleFileString = Stream.readFully(exampleFile);
        }
        
        // only write out the schema file if it's different or never been written out
        if(exampleFileString == null || !exampleFileString.equals(schemaString)) {
            Stream.writeFully(exampleFile, exampleString);
        }        
        
        _configFile = new File(_root, filePrefix + ".json");
        
        // dump an *empty one* if there's nothing there
        if (!_configFile.exists()) {
            Stream.writeFully(_configFile, Serialisation.serialise(NodeConfig.Empty, 4));
        }
        
        _scriptFile = new File(_root, "script.py");
        if (!_scriptFile.exists())
            Stream.writeFully(_scriptFile, ExampleScript.get());
        
        s_threadPool.execute(new Runnable() {
            
            @Override
            public void run() {
                monitorConfig();
            }
            
        });
        
        // check the active functions every min or so
        if (!_closed) {
            s_timerThread.schedule(new TimerTask() {
                
                @Override
                public void run() {
                    checkActiveFunctions();
                }
                
            }, 60000);
        }
    } // (method)
    
    private void checkActiveFunctions() {
        StringBuilder sb = null;
        try {
            synchronized (_activeFunctions) {
                int count = _activeFunctions.size();
                if (count <= 0)
                    return;

                for (Entry<String, Long> entry : _activeFunctions.entrySet()) {
                    String name = entry.getKey();
                    long timeIn = entry.getValue();
                    
                    // check if it's been stuck for more than 2 minutes
                    long stuckMillis = (System.nanoTime() - timeIn) / 1000000;
                    if (stuckMillis > 2 * 60000) {
                        if (sb == null)
                            sb = new StringBuilder();
                        else
                            sb.append(", ");
                        
                        sb.append(name + " (" + DateTimes.formatShortDuration(stuckMillis) + " ago)");
                    }
                }
            }

            if (sb != null) {
                String message = "These functions are stuck - " + sb;
                _errReader.inject(message);
                _logger.warn(message);
            }
        } finally {

            if (!_closed) {
                s_timerThread.schedule(new TimerTask() {

                    @Override
                    public void run() {
                        checkActiveFunctions();
                    }

                }, 60000);
            }
        }
    }
    
    /**
     * Monitors changes to the config or script file and re-launches.
     * I/O is involved so may be blocking.
     */
    private void monitorConfig() {
        try {
            NodeConfig config;
            if (_configFile.exists()) {
                // use most recent 'modified' of the config or script file
                
                // (neither file might exists, but functions safely return '0')
                long lastModifiedHash = _configFile.lastModified() + _scriptFile.lastModified();
                
                if (lastModifiedHash != _fileModifiedHash) {
                    config = (NodeConfig) Serialisation.coerceFromJSON(NodeConfig.class, Stream.readFully(_configFile));
                    applyConfig(config);
                    
                    _fileModifiedHash = lastModifiedHash;
                    
                    _logger.info("Config updated successfully.");
                }
            }
            
        } catch (Exception exc) {
            _errReader.inject("Could not parse node config file; node will not be (re)started. " + Exceptions.formatExceptionGraph(exc));
            
            _logger.warn("Config monitoring failed; will backoff and retry.", exc);
            exc.printStackTrace();
            
        } finally {
            if (!_closed) {
                s_timerThread.schedule(s_threadPool, new TimerTask() {

                    @Override
                    public void run() {
                        monitorConfig();
                    }

                }, 10000);
            }
        }
    } // (method)
    
    /**
     * Applies the full config (and executes script file)
     */
    private void applyConfig(final NodeConfig config) throws Exception {
        try {
            _busy.lock();

            Threads.AsyncResult<Void> op = Threads.executeAsync(new Callable<Void>() {
                
                @Override
                public Void call() throws Exception {
                    applyConfig0(config);
                    return null;
                }
                
            });

            op.waitForResultOrThrowException();
        } finally {
            _busy.unlock();
        }
    } // (method)
    
    /**
     * This needs to be done from a clean thread (non-pooled, daemon) otherwise Python
     * cannot cleanup after itself waiting for, what becomes, its 'MainThread' thread to die.
     */
    private void applyConfig0(NodeConfig config) throws Exception {
        boolean hasErrors = false;
        
        cleanupInterpreter();
        
        long startTime = System.nanoTime();
        
        _logger.info("Initialising new Python interpreter...");
        
        PySystemState pySystemState = new PySystemState();

        // set the current working directory
        pySystemState.setCurrentWorkingDir(_root.getAbsolutePath());
        
        // append the Node's root directory to the path
        pySystemState.path.append(new PyString(_root.getAbsolutePath()));
        pySystemState.path.append(new PyString(_metaRoot.getAbsolutePath()));
        Py.setSystemState(pySystemState);
        
        _globals = new PyDictionary();
        
        ReentrantLock lock = null;
        
        try {
            lock = getAReentrantLock();
            
            trackFunction("(instance creation)");

            // _python = new PythonInterpreter(globals, pySystemState);
            _python = PythonInterpreter.threadLocalStateInterpreter(_globals);

        } finally {
            untrackFunction("(instance creation)");
            
            if (lock != null)
                lock.unlock();
        }

        _logger.info("Interpreter initialised (took {}).", DateTimes.formatPeriod(startTime)); 
        
        // redirect 
        _python.setErr(_errReader);
        _python.setOut(_outReader);
        
        // dump a new example script if necessary
        String exampleScript = ExampleScript.get();
        File exampleScriptFile = new File(_root, "_script_example.py");
        String exampleStringFileStr = null;
        if (exampleScriptFile.exists())
            exampleStringFileStr = Stream.readFully(exampleScriptFile);
        if (exampleStringFileStr == null || !exampleScript.equals(exampleStringFileStr))
            Stream.writeFully(exampleScriptFile, exampleScript);
        
        // TODO: extract stream only once
        
        // dump a new example PySp page if necessary
        try (InputStream exampleStream = PyNode.class.getResourceAsStream("example.pysp")) {
            String examplePySp = Stream.readFully(exampleStream);
            examplePySp = examplePySp.replace("$VERSION", Launch.VERSION);
            File examplePySpFile = new File(_root, "_example.pysp");
            String examplePySpFileStr = null;
            if (examplePySpFile.exists())
                examplePySpFileStr = Stream.readFully(examplePySpFile);
            if (examplePySpFileStr == null || !examplePySp.equals(examplePySpFileStr))
                Stream.writeFully(examplePySpFile, examplePySp);
        }
        
        Bindings bindings = Bindings.Empty;
        
        List<String> dependenciesUsed = new ArrayList<>(); // holds the dependencies actually used (for logging purposes)
        
        try {
            cleanupBindings();
            
            // inject "self" as '_node'
            _python.set("_node", this);
            
            // inject toolkit before script is called... 
            injectToolkit();
            
            lock = null;
            try {
                lock = getAReentrantLock();
                
                trackFunction("(toolkit injection)");
                
                // use this import to provide a toolkit directly into the script
                _python.exec("from nodetoolkit import *");
                
            } finally {
                untrackFunction("(toolkit injection)");
                
                if (lock != null)
                    lock.unlock();
            }
            
            
            // initialise dependency list with configured ones OR the main script (non-fixed)
            List<String> dependencies = new ArrayList<>(4);
            if (config.dependencies != null)
                dependencies.addAll(Arrays.asList(config.dependencies));
            else
                dependencies.add("script.py");
            
            
            // load ingredients_.py and custom_.py scripts into list (in that order)
            String[] filenames = _root.list();
            Arrays.sort(filenames);
            
            for (String name : filenames)
                if (name.startsWith("ingredient") && name.endsWith(".py"))
                    dependencies.add(name);
            
            for (String name : filenames)
                if (name.startsWith("custom") && name.endsWith(".py"))
                    dependencies.add(name);
            
            
            // go through the list of dependencies / scripts
            for (String filename : dependencies) {
                File pythonFile = new File(_root, filename);
                
                if (!pythonFile.exists())
                    throw new FileNotFoundException(filename + " is listed as a dependency but missing");
                
                lock = null;
                try {
                    lock = getAReentrantLock();
                    
                    trackFunction("(" + filename + " loading)");
                    
                    // execute the script file
                    _python.execfile(pythonFile.getAbsolutePath());
                    
                    dependenciesUsed.add(filename);
                    
                } finally {
                    untrackFunction("(" + filename + " loading)");
                    
                    if (lock != null)
                        lock.unlock();
                }
            }
            
            List<String> warnings = new ArrayList<String>();
            
            bindings = BindingsExtractor.extract(_python, warnings);

            if (warnings.size() > 0) {
                for (String warning : warnings) {
                    _errReader.inject(warning);
                }
            }
            
            // apply the binding and parameter values
            injectRemoteBindingValues(config, bindings.remote);
            
            injectParamValues(config, bindings.params);
            
            _config = config;
        } catch (Exception exc) {
            hasErrors = true;
            
            // inject into the error
            _errReader.inject(exc.toString());
            
            // log cleaner Python exception trace if possible 
            if (exc instanceof PyException)
                _logger.warn("The bindings could not be applied to the Python instance. {}", exc.toString());
            else
                _logger.warn("The bindings could not be applied to the Python instance.", exc);
        }
        
        applyBindings(bindings);
        
        _bindings = bindings;
        
        try {
            // log a message to the console and the program log
            String msg;
            if (!hasErrors) {
                msg = String.format("(Python %s loaded in %s; calling 'main' if present...)",
                        dependenciesUsed.size() == 0 ? "with no scripts" : "and " + String.join(", ", dependenciesUsed),
                        DateTimes.formatPeriod(startTime));
                _outReader.inject(msg);
                _logger.info(msg);
                
            } else {
                msg = "(Python and Node script(s) loaded with errors (took " + DateTimes.formatPeriod(startTime) + "); calling 'main' if present...)";
                _errReader.inject(msg);
                _logger.warn(msg);
            }
            
            try {
                lock = null;
                try {
                    lock = getAReentrantLock();
                    
                    // the commentary list for main-related
                    List<String> commentary = new ArrayList<>(3);

                    trackFunction("mains");
                    
                    // handle @before_main functions (if present)
                    PyFunction processBeforeMainFunctions = (PyFunction) _globals.get(Py.java2py("processBeforeMainFunctions"));
                    long beforeFnCount = processBeforeMainFunctions.__call__().asLong();
                    
                    if (beforeFnCount > 0)
                        commentary.add("'@before_main' function" + (beforeFnCount == 1 ? "" : "s"));
                    
                    // call 'main' if it exists
                    PyFunction mainFunction = (PyFunction) _python.get("main");
                    if (mainFunction != null) {
                        mainFunction.__call__();

                        commentary.add("'main'");
                    }
                    
                    // handle @after_main functions (if present)
                    PyFunction processAfterMainFunctions = (PyFunction) _globals.get(Py.java2py("processAfterMainFunctions"));
                    long afterFnCount = processAfterMainFunctions.__call__().asLong();
                    if (afterFnCount > 0)
                        commentary.add("'@after_main' function" + (afterFnCount == 1 ? "" : "s"));
                    

                    // nothing went wrong, kick off toolkit
                    _toolkit.enable();
                    
                    // prepare a neat message to indicate clearly the entry-points of the script
                    int commentarySize =commentary.size(); 
                    if (commentarySize == 0) {
                        msg = "(no 'main' to call)";
                        
                    } else if(commentarySize == 1) {
                        msg = String.format("(%s completed cleanly)", commentary.get(0));
                        
                    } else {
                        StringBuilder sb = new StringBuilder().append("(");
                        
                        for (int a = 0; a < commentarySize; a++) {
                            // neatly separate commentary
                            if (a == commentarySize - 1)
                                sb.append(" and ");
                            else if (a > 0)
                                sb.append(", ");
                            
                            sb.append(commentary.get(a));
                        }
                        
                        sb.append(" completed cleanly)");
                        
                        msg = sb.toString();
                    }
                    
                    _logger.info(msg);
                    _outReader.inject(msg);

                } finally {
                    untrackFunction("mains");

                    if (lock != null)
                        lock.unlock();
                }
                
                // config has changed, so update creation time
                synchronized (_signal) {
                    _started = DateTime.now();
                    _desc = _bindings.desc;
                    _signal.notifyAll();
                }                
                
            } catch (Exception exc) {
                // don't let this interrupt anything

                // log will end up in console anyway...
                msg = null;

                // ...but suppress the stack-trace if it's a Warning or UserWarning
                if (exc instanceof PyException) {
                    PyObject pyExc = ((PyException) exc).value;

                    if (Py.isInstance(pyExc, Py.Warning) || Py.isInstance(pyExc, Py.UserWarning))
                        msg = "Warning: " + pyExc.__str__();
                }

                if (msg == null)
                    msg = "'main' completed with errors - " + exc;

                _logger.warn(msg);
                _errReader.inject(msg);
            }
        } finally {
            _config = config;
        }        
    }

    /**
     * Injects the toolkit.
     * (assumes locked)
     * 
     * (suppressed 'resource' because it gets cleaned up using 'Stream.safeClose' in this method.
     */
    private void injectToolkit() throws IOException {
        // toolkit and callback queue are cleaned up by 'cleanupInterpreter'

        _pySystemState = Py.getSystemState();
        _callbackQueue = new CallbackQueue();
        _toolkit = new ManagedToolkit(this)
            .setExceptionHandler(_exceptionHandler)
            .setThreadStateHandler(_threadStateHandler)
            .setCallbackHandler(_callbackQueue)
            .attachConsole(new Console.Interface() {
                
                @Override
                public void warn(Object obj) {
                    logWarning(String.valueOf(obj));
                }
                
                @Override
                public void log(Object obj) {
                    PyNode.this.log(String.valueOf(obj));
                }
                
                @Override
                public void info(Object obj) {
                    logInfo(String.valueOf(obj));
                }
                
                @Override
                public void error(Object obj) {
                    logError(String.valueOf(obj));
                }
                
            });
        
        // write out the toolkit (if it's new or updated)
        try (InputStream toolkitIS = PyNode.class.getResourceAsStream("nodetoolkit.py")) {
            File existing = new File(_metaRoot, "nodetoolkit.py");
            String toolkit = Stream.readFully(toolkitIS);
            if (!existing.exists() || (!Stream.tryReadFully(existing).equals(toolkit))) {
                Stream.writeFully(existing, toolkit);
            }
        }
        
        // inject the toolkit into global context
        _python.set("_toolkit", _toolkit);
        
        // inject into 'sys'
        _pySystemState.__setattr__("nodetoolkit", Py.java2py(_toolkit));
        
        
    }

    /**
     * Cleans up the interpreter and the toolkit
     */
    private void cleanupInterpreter() {
        String message;
        
        if (_python != null) {
            message = "(closing this interpreter...)";

            _logger.info(message);
            _outReader.inject(message);
            
            try {
                PyFunction processCleanupFunctions = (PyFunction) _globals.get(Py.java2py("processCleanupFunctions"));
                long cleanupFnCount = processCleanupFunctions.__call__().asLong();
                
                if (cleanupFnCount > 0) {
                    message = "('@at_cleanup' function" + (cleanupFnCount == 1 ? "" : "s") + " completed.)";
                    _logger.info(message);
                    _outReader.inject(message);
                }
            } catch (Exception exc) {
                // upstream exception handling should mean we never get here, but just in case
                _logger.warn("Unexpected exception during cleaning up; should be safe to ignore", exc);
            }
            
            _python.cleanup();
            
            message = "(clean up complete)";
            _logger.info(message);
            _outReader.inject(message);
        }
        
        if (_toolkit != null) {
            _toolkit.shutdown();
        }
        
        if (_callbackQueue != null) {
            _callbackQueue = null;
        }
    } // (method)
    
    /**
     * (assumes locked)
     */
    private void applyBindings(Bindings bindings) {
        if (bindings == null) {
            _logger.info("No bindings were specified.");
            return;
        }
        
        // deal with the local bindings
        int count = bindLocalBindings(bindings.local);
        
        // check if we need to use a 'dummy' binding
        if (count == 0) {
            // bind 'dummy' so advertisement still takes place
            _dummyBinding = new NodelServerAction(_name.getOriginalName(), "Dummy", null);
            _dummyBinding.registerAction(new ActionRequestHandler() {

                @Override
                public void handleActionRequest(Object arg) {
                    // no-op
                }

            });
        }

        // deal with the remote bindings
        bindRemoteBindings(bindings.remote);

        // deals with the parameters
        bindParams(bindings.params);
    } // (method)    

    /**
     * (assumes locked)
     * @return The number of bindings
     */
    private int bindLocalBindings(LocalBindings provides) {
        if (provides == null) {
            _logger.info("This node does not provide any events nor actions.");
            return 0;
        }
        
        int count = 0;
        
        // go through the actions
        count += bindLocalActions(provides.actions);
        
        // go through the events
        count += bindLocalEvents(provides.events);
        
        return count;
    }
        
    /**
     * Binds actions.
     */
    private int bindLocalActions(Map<SimpleName, Binding> actions) {
        if (actions == null)
            return 0;
        
        StringBuilder sb = new StringBuilder();

        for (final Entry<SimpleName, Binding> entry : actions.entrySet()) {
            Binding binding = entry.getValue();
            // (Nodel layer)
            //String title, String desc, String group, String caution, Map<String, Object> argSchema
            NodelServerAction serverAction = new NodelServerAction(_name.getOriginalName(), entry.getKey().getReducedName(), binding);
            serverAction.setThreadingEnvironment(_callbackQueue, _threadStateHandler, _emitExceptionHandler);
            serverAction.registerAction(new ActionRequestHandler() {

                @Override
                public void handleActionRequest(Object arg) {
                    addLog(DateTime.now(), LogEntry.Source.local, LogEntry.Type.action, entry.getKey(), arg);
                    PyNode.this.handleActionRequest(entry.getKey(), arg);
                }

            });
            
            super.addLocalAction(serverAction);
            
            if (sb.length() > 0)
                sb.append(", ");
            
            sb.append("local_action_" + entry.getKey().getReducedName());
        }

        if (sb.length() > 0)
            _logger.info("Mapped this Node's actions to Python functions {}", sb);
        else
            _logger.info("This Node has no actions.");

        return actions.size();
    } // (method)
    
    /**
     * When an action request arrives via Nodel layer.
     * @throws Exception 
     */
    protected Object handleActionRequest(SimpleName action, Object arg) {
        _logger.info("Action requested - {}", action);
        
        // is a threaded environment so need sequence numbering
        long num = _funcSeqNumber.getAndIncrement();
        
        String functionName = "local_action_" + action;
        
        String functionKey = functionName + "_" + num;

        try {
            synchronized (_activeFunctions) {
                _activeFunctions.put(functionKey, System.nanoTime());
            }
            
            PySystemState systemState = _pySystemState;
            if (systemState == null)
                throw new IllegalStateException("Python interpreter not ready.");
            
            Py.setSystemState(systemState);
            
            PyObject pyObject = _globals.get(Py.java2py(functionName));
            if (!(pyObject instanceof PyFunction)) {
                _logger.warn("Python interpreter function resolution failed when it should not have. name:'{}', class:{} value:{}", 
                        functionName, pyObject == null? null: pyObject.getClass(), pyObject);
                
                throw new IllegalStateException("Action call failure (internal server error) - '" + functionName + "'");
            }
            
            PyFunction pyFunction = (PyFunction) pyObject;
            PyBaseCode code = (PyBaseCode) pyFunction.func_code;

            // only support either 0 or 1 args
            PyObject pyResult;
            if (code.co_argcount == 0)
                pyResult = pyFunction.__call__();
            else
                pyResult = pyFunction.__call__(Py.java2py(arg));
            
            return pyResult;

        } catch (Exception exc) {
            String message = "Action call failed - " + exc;
            _logger.info(message);
            _errReader.inject(message);

            throw new RuntimeException(exc);
            
        } finally {
            // clean up the active function map and temporary argument
            synchronized(_activeFunctions) {
                _activeFunctions.remove(functionKey);
            }
        }
    } // (method)
        
    /**
     * Binds the 'provided events'.
     */
    private int bindLocalEvents(Map<SimpleName, Binding> events) {
        if (events == null)
            return 0;
        
        StringBuilder sb = new StringBuilder();

        for(final Entry<SimpleName, Binding> eventBinding : events.entrySet()) {
            // (Nodel layer and Python)
            Binding binding = eventBinding.getValue();
            
            NodelServerEvent nodelServerEvent = new NodelServerEvent(_name.getOriginalName(), eventBinding.getKey().getReducedName(), binding, true);
            nodelServerEvent.setThreadingEnvironment(_callbackQueue, _threadStateHandler, _emitExceptionHandler);
            nodelServerEvent.attachMonitor(new Handler.H2<DateTime, Object>() {

                @Override
                public void handle(DateTime timestamp, Object arg) {
                    addLog(timestamp, LogEntry.Source.local, LogEntry.Type.event, eventBinding.getKey(), arg);
                }

            });
            addLocalEvent(nodelServerEvent);
            
            String varName = "local_event_" + eventBinding.getKey().getOriginalName();
            _python.set(varName, nodelServerEvent);
                        
            if (sb.length() > 0)
                sb.append(", ");
            
            sb.append(varName);            
        } // (for)
        
        if (sb.length() > 0)
            _logger.info("Mapped this Node's events to Python variables {}", sb);
        else
            _logger.info("This Node has no events.");
        
        return events.size();
    } // (method)
    
    private void bindRemoteBindings(RemoteBindings remoteBindings) {
        if (remoteBindings == null) {
            _logger.info("This node does not require any events nor actions.");
            return;
        }
        
        bindRemoteActions(remoteBindings.actions);
        
        bindRemoteEvents(remoteBindings.events);
    } // (method)
    
    public class Remote {

        /**
         * The parameters bindings.
         */
        @Service(name = "schema", title = "Schema", desc = "Returns the processed schema that produces data that can be used by 'save'.")
        public Map<String, Object> getSchema() {
            return _bindings.remote.asSchema();
        }
        
        @Service(name = "save", title = "Save", desc = "Saves the remote binding values.")
        public void save(@Param(name = "value", desc = "The remoting binding values.", isMajor = true, title = "Value") 
                         RemoteBindingValues remoteBindingValues) throws Exception {
            if (!_busy.tryLock())
                throw new OperationPendingException();
            
            try {
                _config.remoteBindingValues = remoteBindingValues;
                injectRemoteBindingValues(_config, _bindings.remote);

                saveConfig0(_config);
            } finally {
                _busy.unlock();
            }
        }
        
        @Value(name = "value", title = "Value", desc = "The remote binding values.", treatAsDefaultValue = true)
        public RemoteBindingValues getValue() {
            return _config.remoteBindingValues;
        }        

    } // (class)
    
    /**
     * Holds the live params.
     */
    private Remote _remote = new Remote();

    @Service(name = "remote", title = "Remote", desc = "The remote bindings.")
    public Remote getRemote() {
        return _remote;
    }    
        
    private void bindRemoteActions(Map<SimpleName, NodelActionInfo> actions) {
        if (actions == null) {
            _logger.info("This node does not require any actions.");
            return;
        }
        
        for(final Entry<SimpleName, NodelActionInfo> action : actions.entrySet()) {
            NodelActionInfo actionInfo = action.getValue();
            
            String varName = "remote_action_" + action.getKey().getReducedName();
            
            String nodeName = actionInfo.node;
            String actionName = actionInfo.action;
            
            // empty bindings are allowed and will show up as "unbound" sources
            
            // (Nodel layer)
            final NodelClientAction nodelAction = new NodelClientAction(action.getKey(),
                    !Strings.isBlank(nodeName) ? new SimpleName(nodeName) : null, 
                    !Strings.isBlank(actionName) ? new SimpleName(actionName) : null);

            nodelAction.attachMonitor(new Handler.H1<Object>() {
                
                @Override
                public void handle(Object arg) {
                    if (nodelAction.isUnbound())
                        addLog(DateTime.now(), LogEntry.Source.unbound, LogEntry.Type.action, action.getKey(), arg);                    
                    else
                        addLog(DateTime.now(), LogEntry.Source.remote, LogEntry.Type.action, action.getKey(), arg);
                }
                
            });
            nodelAction.attachWiredStatusChanged(new Handler.H1<BindingState>() {
                
                @Override
                public void handle(BindingState status) {
                    _logger.info("Action binding status: {} - '{}'", action.getKey().getReducedName(), status);
                    
                    addLog(DateTime.now(), LogEntry.Source.remote, LogEntry.Type.actionBinding, action.getKey(), status);
                }
                
            });
            nodelAction.registerActionInterest();
            
            // (Python)
            _python.set(varName, nodelAction);

            _remoteActions.put(nodelAction.getName(), nodelAction);
            
            _logger.info("Mapped peer action to Python variable '{}'", varName);
        } // (for)
        
    } // (method)
        
    private void bindRemoteEvents(Map<SimpleName, NodelEventInfo> events) {
        if (events == null) {
            _logger.info("This node does not require any events.");
            return;
        }
        
        // for summary in log
        StringBuilder sb = new StringBuilder();

        for (Entry<SimpleName, NodelEventInfo> entry : events.entrySet()) {
            final SimpleName alias = entry.getKey();
            NodelEventInfo eventInfo = entry.getValue();
            
            final String pythonEvent = "remote_event_" + alias;

            String nodeName = eventInfo.node;
            String eventName = eventInfo.event;
            
            if (Strings.isBlank(nodeName) || Strings.isBlank(eventName))
                // skip for now
                continue;

            final NodelClientEvent nodelClientEvent = new NodelClientEvent(alias, new SimpleName(nodeName), new SimpleName(eventName));
            
            nodelClientEvent.setThreadingEnvironment(_callbackQueue, _threadStateHandler, _emitExceptionHandler);
            
            nodelClientEvent.setHandler(new NodelEventHandler() {
                
                @Override
                public void handleEvent(SimpleName node, SimpleName event, Object arg) {
                    handleRemoteEventArrival(alias, nodelClientEvent, pythonEvent, arg);
                }
                
            });
            
            nodelClientEvent.addBindingStateHandler(new Handler.H1<BindingState>() {

                @Override
                public void handle(BindingState status) {
                    addLog(DateTime.now(), LogEntry.Source.remote, LogEntry.Type.eventBinding, alias, status);

                    _logger.info("Event binding status: {} - '{}'", alias.getReducedName(), status);
                }

            });
            
            addRemoteEvent(nodelClientEvent);
            
            if (sb.length() > 0)
                sb.append(", ");

            sb.append('"').append(pythonEvent).append('"');
        } // (for)
        
        if (sb.length() > 0)
            _logger.info("Mapped peer events to Python events {}", sb.toString());

    } // (method)
    
    /**
     * When an event arrives via Nodel layer.
     */
    private void handleRemoteEventArrival(SimpleName alias, NodelClientEvent nodelClientEvent, String functionName, Object arg) {
        _logger.info("Event arrived - {}", nodelClientEvent.getNodelPoint());
        
        addLog(DateTime.now(), LogEntry.Source.remote, LogEntry.Type.event, alias, arg);
        
        // is a threaded environment so need sequence numbering
        long num = _funcSeqNumber.getAndIncrement();
        
        String functionKey = functionName + "_" + num;
        
        synchronized (_activeFunctions) {
            _activeFunctions.put(functionKey, System.nanoTime());
        }

        try {
            PySystemState systemState = _pySystemState;
            if (systemState == null)
                throw new IllegalStateException("Python interpreter not ready.");
            
            Py.setSystemState(systemState);
            
            PyObject pyObject = _globals.get(Py.java2py(functionName));
            if (!(pyObject instanceof PyFunction)) {
                _logger.warn("Python interpreter function resolution failed when it should not have. name:'{}', class:{} value:{}", 
                        functionName, pyObject == null ? null : pyObject.getClass(), pyObject);

                throw new IllegalStateException("Event handling failure (internal server error) - '" + functionName + "'");
            }

            PyFunction pyFunction = (PyFunction) pyObject;
            PyBaseCode code = (PyBaseCode) pyFunction.func_code;

            // only support either 0 or 1 args
            if (code.co_argcount == 0)
                pyFunction.__call__();
            else
                pyFunction.__call__(Py.java2py(arg));

        } catch (Exception exc) {
            String message = "Remote event handling failed - " + exc;
            _logger.info(message);
            _errReader.inject(message);

            throw new RuntimeException(exc);
            
        } finally {
            // clean up the and active function map temporary argument
            synchronized (_activeFunctions) {
                _activeFunctions.remove(functionKey);
            }
        }

    } // (method)
    
    /**
     * Binds the parameters to the script.
     * @param params
     */
    private void bindParams(ParameterBindings params) {
        if (params == null) {
            _logger.info("This node does use any parameters.");
            return;
        }

        for (Entry<SimpleName, ParameterBinding> entry : params.entrySet()) {
            SimpleName name = entry.getKey();
            ParameterBinding paramBinding = entry.getValue();
            
            Object value = paramBinding.value;
            
            String paramName = "param_" + name.getReducedName();
            
            _python.set(paramName, value);
            
            _parameters.put(name, new ParameterEntry(name, value));

            _logger.info("Created parameter '{}' in script (initial value '{}').", paramName, value);
        } // (for)

    } // (method)

    public class Params {

        /**
         * The parameters bindings.
         */
        @Service(name = "schema", title = "Schema", desc = "Returns the processed schema that produced data that can be used by 'save'.")
        public Map<String, Object> getSchema() {
            return _bindings.params.asSchema();
        } // (method)
        
        @Service(name = "save", title = "Save", desc = "Saves a set of parameters.")
        public void save(@Param(name = "value", title = "Value", desc = "The parameter values.", isMajor = true, genericClassA = SimpleName.class, genericClassB = Object.class) 
                         ParamValues paramValues) throws Exception {
            if (!_busy.tryLock())
                throw new OperationPendingException();
            
            try {
                _config.paramValues = paramValues;
                injectParamValues(_config, _bindings.params);

                saveConfig0(_config);
            } finally {
                _busy.unlock();
            }
        } // (method)
        
        @Value(name = "value", title = "Value", desc = "The value object.", treatAsDefaultValue = true)
        public ParamValues getValue() {
            return _config.paramValues;
        }

    } // (class)
    
    /**
     * Holds the live params.
     */
    private Params _params = new Params();

    @Service(name = "params", title = "Params", desc = "The live parameters.")
    public Params getParams() {
        return _params;
    }
    
    /**
     * Evaluates a Python expression related to the current interpreter instance.
     */
    @Service(name = "eval", title = "Evaluate", desc = "Evaluates a Python expression.")
    public Object eval(@Param(name = "expr", title = "Expression", desc = "A Python expression.") final String expr, String source) throws Exception {
        final PythonInterpreter python = _python;
        
        if (python == null)
            throw new RuntimeException("The interpreter has not been initialised yet.");
        
        // for keeping track of stuck threads
        String functionKey = "eval" + (!Strings.isBlank(source) ? "_" + source : "") + "_" + _funcSeqNumber.getAndIncrement();
        
        try {
            trackFunction(functionKey);

            Threads.AsyncResult<Object> op = Threads.executeAsync(new Callable<Object>() {

                @Override
                public Object call() throws Exception {
                    return python.eval(expr);
                }

            });

            return op.waitForResultOrThrowException();
            
        } finally {
            // clean up
            untrackFunction(functionKey);
        }
    } // (method)

    /**
     * Evaluates a Python expression related to the current interpreter instance.
     * 
     * @param source The caller source (for logging, etc.)
     * @param onThread Code that should be run on the thread (normally used when threadlocal used)
     */
    @Service(name = "exec", title = "Execute", desc = "Execute Python code fragment.")
    public void exec(@Param(name = "code", title = "Code", desc = "A Python expression.") final String code, String source, final Runnable onThread) throws Exception {
        if (code == null)
            throw new IllegalArgumentException("'code' argument cannot be missing.");
        
        final PythonInterpreter python = _python;
        
        if (python == null)
            throw new RuntimeException("The interpreter has not been initialised yet.");

        String functionKey = "exec" + (!Strings.isBlank(source) ? "_" + source : "") + "_" + _funcSeqNumber.getAndIncrement();

        try {
            trackFunction(functionKey);
            
            Threads.AsyncResult<Object> op = Threads.executeAsync(new Callable<Object>() {

                @Override
                public Object call() throws Exception {
                    if (onThread != null)
                        onThread.run();
                    
                    python.exec(code);

                    return null;
                }

            });

            op.waitForResultOrThrowException();
        } catch (Exception exc) {
            String message = "exec " + (!Strings.isBlank(source) ? "_" + source : "") + "- " + exc;
            _logger.info(message);
            _errReader.inject(message);
            
            throw exc;
            
        } finally {
            // clean up
            untrackFunction(functionKey);
        }
    } // (method)
    
    /**
     * Restarts the node.
     */
    @Service(name = "restart", title = "Restart", desc = "Restarts this node.")
    public void restart() {
        _logger.info("restart() called");
        
        _fileModifiedHash = 0;
        
        // graceful restart as background task...
    }
    
    /**
     * Renames the node.
     */
    @Service(name = "rename", title = "Rename", desc = "Renames a node.")
    public void rename(SimpleName newName) {
        _nodelHost.renameNode(this, newName);
        
        // would get here without any exceptions, the folder should have been renamed
        _logger.info("This node has been renamed. It will close down and restart under a new name shortly.");
    }
    
    /**
     * Renames the node.
     */
    @Service(name = "update", title = "Updates", desc = "Updates a node from a recipe.")
    public void update(@Param(name = "path") String path) {
        if (Strings.isBlank(path))
            throw new RuntimeException("No recipe path name was provided");

        File baseDir = _nodelHost.recipes().getRecipeFolder(path);

        if (baseDir == null)
            throw new RuntimeException("A recipe with path \"" + path + "\" could not be found.");
        
        Files.updateDir(baseDir, _root);

        _logger.info("This node has been update (basePath={})", path);
    }
    
    /**
     * Renames the node.
     */
    @Service(name = "remove", title = "Remove", desc = "Removes a node.")
    public void remove(@Param(name = "confirm") boolean confirm) {
        if (!confirm)
            throw new RuntimeException("'confirm' flag was not set. Nothing removed.");

        this.close();
        
        // in case of lingering operations, try a few times... 

        int triesLeft = 5;
        for (; triesLeft > 0; triesLeft--) {
            
            // flush all files
            Files.tryFlushDir(_root);

            // files are flushed, now the directory itself
            _root.delete();

            if (!_root.exists())
                break;

            Threads.safeWait(_signal, 1000);

            // and try some more...
        }

        if (triesLeft <= 0)
            throw new RuntimeException("Attempts were made to remove the node however it still exists. Temporary file locking might be preventing the removal of the node. Try again later.");

        // the folder should have been removed!
        _logger.info("The node has been deleted.");
    }

    /**
     * (end-point)
     */
    private FilesEndPoint _files = new FilesEndPoint(_root);
    
    /**
     * An end-point that support simple file management.
     */
    @Service(name="files")
    public FilesEndPoint files() { return _files;}
    
    protected void injectError(String source, Throwable th) {
        String excValue = th.toString();
        _logger.info(source + " - " + excValue);
        _errReader.inject(source + " - " + excValue);
    }

    /**
     * Outside callers can inject error messages related to this node.
     */
    protected void notifyOfError(Exception exc) {
        _errReader.inject(exc.toString());
    }

    /**
     * Permanently shuts down the node.
     */
    public void close() {
        synchronized (_signal) {
            if (_closed)
                return;

            _closed = true;

            _logger.info("Closing node...");

            cleanupBindings();

            cleanupInterpreter();

            super.close();

            // stuff
        }
    } // (method)

    /**
     * Tracks dead (never-ending) functions.
     * Done just after 'try' section.
     */
    private void trackFunction(String functionKey) {
        synchronized (_activeFunctions) {
            _activeFunctions.put(functionKey, System.nanoTime());
        }
    }

    /**
     * (opposite of 'trackFunction')
     * Done within 'finally' section.
     */
    private void untrackFunction(String functionKey) {
        synchronized (_activeFunctions) {
            _activeFunctions.remove(functionKey);
        }
    }

    /**
     * Waits no longer than 10s to try and get a reentrant lock.
     * Starts off sharing one lock, but may need more if nodes lock up or take too long to initialise.
     */
    private ReentrantLock getAReentrantLock() throws InterruptedException {
        ReentrantLock lock = null;

        for (;;) {
            if (lock != null && lock.tryLock(10, TimeUnit.SECONDS)) {
                return lock;

            } else {
                // refresh the lock if its the same one
                synchronized (s_globalLock) {
                    if (lock == null)
                        // should get here first iteration
                        lock = s_currentGlobalRentrantLock;

                    else if (lock == s_currentGlobalRentrantLock) {
                        // won't get here the first iteration
                        s_currentGlobalRentrantLock = new ReentrantLock();

                        _logger.warn("The interlocked initialisation sequence of the scripting engines is slow or dead-locked. A new lock has been created to hopefully release any potential dead-lock.");

                        lock = s_currentGlobalRentrantLock;
                    }

                    // otherwise the lock is different to what was previously used,
                    // so try lock on that one
                }
            }
        }
    }

} // (class)
