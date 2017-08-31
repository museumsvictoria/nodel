package org.nodel.jyhost;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.nodel.SimpleName;
import org.nodel.Strings;
import org.nodel.host.Binding;
import org.nodel.host.Bindings;
import org.nodel.host.LocalBindings;
import org.nodel.host.NodelActionInfo;
import org.nodel.host.NodelEventInfo;
import org.nodel.host.ParameterBinding;
import org.nodel.host.ParameterBindings;
import org.nodel.host.RemoteBindings;
import org.nodel.reflection.Serialisation;
import org.python.core.PyDictionary;
import org.python.core.PyFunction;
import org.python.core.PyNone;
import org.python.core.PyObject;
import org.python.util.PythonInterpreter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BindingsExtractor {
    
    /**
     * (logging related)
     */
    private static Logger s_logger = LoggerFactory.getLogger(BindingsExtractor.class.getName());
    
    /**
     * Examines the Python interpreter, extracting all the parts that form the bindings and returns
     * any warnings.
     */
    public static Bindings extract(PythonInterpreter python, List<String> outWarnings) {
        Map<SimpleName, Binding> localActions = new LinkedHashMap<SimpleName, Binding>();
        Map<SimpleName, Binding> localEvents = new LinkedHashMap<SimpleName, Binding>();
        Map<SimpleName, Binding> remoteActions = new LinkedHashMap<SimpleName, Binding>();
        Map<SimpleName, Binding> remoteEvents = new LinkedHashMap<SimpleName, Binding>();
        Map<SimpleName, Binding> paramValues = new LinkedHashMap<SimpleName, Binding>();
        
        String desc = null;
        
        // get all the listed functions
        PyObject locals = python.getLocals();
        
        // sort them first because Python uses HashMap which is unsorted.
        // can only do alphabetical unfortunately
        
        List<PyObject> localsList = new ArrayList<PyObject>();
        for(PyObject local : locals.asIterable()) {
            localsList.add(local);
        }
        
        Collections.sort(localsList, new Comparator<PyObject>() {
            
            @Override
            public int compare(PyObject o1, PyObject o2) {
                return o1.__cmp__(o2);
            }
            
        });
            
        // test each item in locals
        for(PyObject key : localsList) {
            
            // the the value of the local variable
            PyObject value = locals.__getitem__(key);
            
            // look for description held in '__doc__'
            if (key.toString().equals("__doc__")) {
                if (!value.getType().equals(PyNone.TYPE))
                    desc = value.toString();
            }
            
            
            // test for functions
            if (value instanceof PyFunction) {
                PyFunction pyFunc = (PyFunction) value;
                
                // this is provided by the 'monkey patching'
                SimpleName localActionName = testForBinding(pyFunc.__name__, "local_action_"); 
                if (localActionName != null) {
                    localActions.put(localActionName, createBinding(localActionName, pyFunc.__doc__, outWarnings));
                    continue;
                }
                
                // this is provided by the 'monkey patching'
                SimpleName remoteEventName = testForBinding(pyFunc.__name__, "remote_event_");
                if (remoteEventName != null) {
                    remoteEvents.put(remoteEventName, createBinding(remoteEventName, pyFunc.__doc__, outWarnings));
                    continue;
                }
                
            } else {
                // use the name of the variable (key)
                SimpleName localEventName = testForBinding(key.toString(), "local_event_");
                if (localEventName != null) {
                    localEvents.put(localEventName, createBinding(localEventName, value, outWarnings));
                    continue;
                }
                
                // use the name of the variable (key)
                SimpleName remoteActionName = testForBinding(key.toString(), "remote_action_");
                if (remoteActionName != null) {
                    remoteActions.put(remoteActionName, createBinding(remoteActionName, value, outWarnings));
                    continue;
                }
                
                SimpleName paramName = testForBinding(key.toString(), "param_");
                if (paramName != null) {
                    paramValues.put(paramName, createBinding(paramName, value, outWarnings));
                    continue;
                }
            }
            
        } // (for)
        
        Bindings bindings = new Bindings();
        
        bindings.desc = desc;
        
        // local actions and events
        bindings.local = new LocalBindings();
        bindings.local.actions = localActions;
        bindings.local.events = localEvents;
        
        
        // remote action and events
        bindings.remote = new RemoteBindings();
        
        bindings.remote.actions = new LinkedHashMap<SimpleName, NodelActionInfo>();
        for (Entry<SimpleName, Binding> entry : remoteActions.entrySet()) {
            Binding binding = entry.getValue();
            NodelActionInfo action = new NodelActionInfo();
            
            action.group = binding.group;
            action.title = binding.title;
            action.desc = binding.desc;
            action.caution = binding.caution;
            action.order = binding.order;
            
            bindings.remote.actions.put(entry.getKey(), action);
        }
        
        bindings.remote.events = new LinkedHashMap<SimpleName, NodelEventInfo>();
        for (Entry<SimpleName, Binding> entry : remoteEvents.entrySet()) {
            Binding binding = entry.getValue();
            NodelEventInfo event = new NodelEventInfo();
            
            event.group = binding.group;
            event.title = binding.title;
            event.desc = binding.desc;
            event.caution = binding.caution;
            event.order = binding.order;
            
            bindings.remote.events.put(entry.getKey(), event);
        }
        
        // parameters
        bindings.params = new ParameterBindings();
        for(Entry<SimpleName, Binding> entry : paramValues.entrySet()) {
            Binding binding = entry.getValue();
            ParameterBinding paramBinding = new ParameterBinding();
            
            paramBinding.group =  binding.group;
            paramBinding.title = binding.title;
            paramBinding.desc = binding.desc;
            paramBinding.order = binding.order;
            paramBinding.schema = binding.schema;
             
            bindings.params.put(entry.getKey(), paramBinding);
        }
        
        return bindings;
    } // (method)

    /**
     * Creates a binding from the function definition.
     */
    private static Binding createBinding(SimpleName bindingName, PyObject definition, List<String> outWarnings) {
        Exception exc = null;
        Binding binding = null;

        try {
            if (definition.getType().equals(PyDictionary.TYPE)) {
                binding = (Binding) Serialisation.coerce(Binding.class, definition, String.class, Object.class);

            } else {
                String asJSONorTitle = null;

                if (!definition.getType().equals(PyNone.TYPE)) {
                    asJSONorTitle = definition.toString();
                }

                if (!Strings.isNullOrEmpty(asJSONorTitle)) {
                    if (asJSONorTitle.startsWith("{")) {
                        binding = (Binding) Serialisation.coerceFromJSON(Binding.class, asJSONorTitle);
                    } else {
                        binding = new Binding();
                        binding.title = asJSONorTitle;
                    }
                }
            }
        } catch (Exception e) {
            exc = e;
        }

        if (exc != null) {
            // ignore binding exception but note warning
            String warnMessage = "Binding '" + bindingName + "' had invalid meta-data; ignoring - " + compressedErrorMessage(exc);

            outWarnings.add(warnMessage);
            s_logger.warn(warnMessage);
        }

        if (binding == null)
            binding = new Binding();

        if (Strings.isNullOrEmpty(binding.title))
            binding.title = bindingName.getReducedName();
        
        return binding;
    } // (method)

    /**
     * Tests whether the function is defined properly.
     */
    private static SimpleName testForBinding(String rawName, String prefix) {
        String lowerCaseName = rawName.toLowerCase();
        
        int index = lowerCaseName.indexOf(prefix);
         
        if(index < 0 || rawName.length() == prefix.length())
            return null;
        
        String name = rawName.substring(prefix.length());
        
        SimpleName nodelName = new SimpleName(name);
        
        return nodelName;
    } // (method)
    
    private static String compressedErrorMessage(Throwable exc) {
        StringBuilder sb = new StringBuilder();
        
        Throwable cause = exc.getCause();
        
        if (cause == null)
            return exc.getMessage();
        
        else
            return sb.append(exc.getMessage()).append(" (").append(compressedErrorMessage(cause)).append(")").toString();
    }

} // (class)
