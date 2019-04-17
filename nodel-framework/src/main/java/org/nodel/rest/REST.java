package org.nodel.rest;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.nodel.Strings;
import org.nodel.reflection.Schema;
import org.nodel.reflection.ParameterInfo;
import org.nodel.reflection.Reflection;
import org.nodel.reflection.Serialisation;
import org.nodel.reflection.ServiceInfo;
import org.nodel.reflection.ValueInfo;

public class REST {
    
    public static Object resolveRESTcall(Object graph, String[] parts, Map<?, ?> props, byte[] buffer) throws Exception {
        return resolveRESTcall(graph, parts, props, buffer, true, false);
    }
    
    /**
     * Handles a REST call.
     * 
     * NOTE: the props value part is an array
     * 
     * @param strict 'true' if must respect service and value attributes.
     */
    public static Object resolveRESTcall(Object graph, String[] parts, Map<?, ?> props, byte[] buffer, boolean strict) throws Exception {
        return resolveRESTcall(graph, parts, props, buffer, strict, false);
    }
    
    /**
     * (optional 'treatEmptyStringAsNull')
     */
    public static Object resolveRESTcall(Object graph, String[] parts, Map<?, ?> props, byte[] buffer, boolean strict, boolean treatEmptyStringAsNull) throws Exception {
        // the cursor, starting at the root of the graph
        Object object = graph;
        
        // check for default services
        object = Reflection.getDefaultService(object);
        
        // works alongside the cursor, using any class hints
        // because of Java type erasure
        Class<?> classHint = null;
        ServiceInfo serviceInfoHint = null;

        // the REST path as it builds up
        StringBuilder restPath = new StringBuilder();

        // go through each part
        for (int partNum = 0; partNum < parts.length; partNum++) {
            String part = parts[partNum];
            boolean lastPart = partNum == parts.length - 1;
            
            restPath.append("/" + part);
            
            // check for default services except for first time
            // because it has already been done
            if (partNum > 0)
                object = Reflection.getDefaultService(object);            

            if (object == null)
                throw new FileNotFoundException(restPath.toString());

            // test for a map
            if (object instanceof Map<?, ?>) {
                Map<?, ?> map = (Map<?, ?>) object;
                
                Object key;

                // check if a key class hint is being used
                if (classHint != String.class && classHint != Object.class) {
                     // coerce the string into the key
                    //key = Reflection.createInstanceFromString(classHintA, part);
                    key = Serialisation.coerce(classHint, part, null, null, treatEmptyStringAsNull);
                    
                    // conversion failed, so just use it as the string
                    if (key == null)
                        key = part;
                } else {
                    // try treat key as a string
                    key = part;
                }

                // key the value from the map
                Object value = map.get(key);
                if (value == null)
                    throw new EndpointNotFoundException(part);

                classHint = Object.class;
                serviceInfoHint = null;
                
                object = value;
                continue;
            } // (if - map)

            // test for a list
            if (object instanceof List<?>) {
                List<?> list = (List<?>) object;

                int index;
                try {
                    // treat the value as an index into the list
                    index = Integer.parseInt(part);

                } catch (NumberFormatException ignoreEX) {
                    throw new FileNotFoundException(restPath.toString());
                }

                if (index < 0 || index >= list.size())
                    throw new FileNotFoundException(restPath.toString());
                
                classHint = Object.class;
                serviceInfoHint = null;

                object = list.get(index);
                continue;
            } // (if - list)

            // will need class definition later
            Class<?> klass = object.getClass();

            // test for a array
            if (klass.isArray()) {
                int size = Array.getLength(object);

                int index;
                try {
                    // treat the value as an index into the list
                    index = Integer.parseInt(part);

                } catch (NumberFormatException ignoreEX) {
                    throw new FileNotFoundException(restPath.toString());
                }

                if (index < 0 || index >= size)
                    throw new FileNotFoundException(restPath.toString());

                classHint = Object.class;
                serviceInfoHint = null;

                object = Array.get(object, index);

                continue;
            } // (if - list)

            // if not in strict mode, test for annotated "values"
            if (!strict) {
                ValueInfo valueInfo = Reflection.getValueInfosByName(klass, part);
                if (valueInfo != null) {
                    try {
                        if (valueInfo.member instanceof Field) {
                            Field field = (Field) valueInfo.member;

                            Object result = field.get(object);

                            classHint = Object.class;
                            serviceInfoHint = null;

                            object = result;

                            continue;
                        } else {
                            Method method = (Method) valueInfo.member;

                            Object result = method.invoke(object, (Object[]) null);

                            object = result;

                            continue;
                        }
                    } catch (IllegalArgumentException e) {
                        throw new RuntimeException(e);

                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }
            }

            // test for "services" (can be annotated methods or fields)
            ServiceInfo serviceInfo = Reflection.getServiceInfosByName(klass, part);
            if (serviceInfo != null) {
                try {
                    if (serviceInfo.member instanceof Field) {
                        // dealing with a field
                        Field field = (Field) serviceInfo.member;

                        // set the class hint
                        classHint = serviceInfo.annotation.genericClassA();
                        serviceInfoHint = serviceInfo;
                        
                        // set the object
                        Object result = field.get(object);

                        // move the cursor
                        object = result;
                        continue;

                    } else if (serviceInfo.member instanceof Method) {
                        // dealing with a method
                        Method method = (Method) serviceInfo.member;

                        // get the argument types
                        Class<?>[] argTypes = method.getParameterTypes();

                        // create an array for the arguments
                        Object[] args = new Object[argTypes.length];
                        
                        // stores whether an arg has been set or not
                        boolean[] argSet = new boolean[argTypes.length];

                        Map<String, ParameterInfo> paramMap = serviceInfo.parameterMap;

                        // check if the method takes more than one argument, so
                        // fill in args from Query String and / or POST data
                        if (argTypes.length > 0) {
                            if (!lastPart) {
                                // not the last part, try treat REST part as
                                // first argument of method
                                Object firstArg;

                                // check if a key class hint is being used
                                if (classHint != String.class && classHint != Object.class) {
                                    // coerce the string into the key
                                    // key =
                                    // Reflection.createInstanceFromString(classHintA,
                                    // part);
                                    firstArg = Serialisation.coerce(classHint, part, null, null, treatEmptyStringAsNull);

                                    // conversion failed, so just use it as the
                                    // string
                                    if (firstArg == null)
                                        firstArg = part;
                                } else {
                                    // try treat key as a string
                                    firstArg = part;
                                }

                                args[0] = firstArg;
                                argSet[0] = true;
                            } else {
                                // for last part use the query string parameters
                                // and POST data (if it's present)
                                
                                  // try match each argument provided
                                if (props != null) {
                                    Set<?> propertyNames = props.keySet();
                                    for (Object propertyNameObj : propertyNames) {
                                        String propertyName = propertyNameObj.toString();

                                        // try get the key
                                        ParameterInfo paramInfo = paramMap.get(propertyName.toLowerCase());

                                        // skip unexpected parameters
                                        if (paramInfo == null)
                                            continue;

                                        Object value = props.get(propertyNameObj);
                                        if (value.getClass().isArray())
                                            value = Array.get(value, 0);

                                        String paramValueStr = (String) value;
                                        Object paramValueObj = Serialisation.coerce(paramInfo.klass, paramValueStr, null, null, treatEmptyStringAsNull);
                                        args[paramInfo.index] = paramValueObj;
                                        argSet[paramInfo.index] = true;
                                    } // (while)
                                } // (if)

                                // check for POST data

                                if (buffer != null) {
                                    // check the last argument for byte stream
                                    int lastIndex = argTypes.length - 1;
                                    if (lastIndex >= 0 && args[lastIndex] == null && argTypes[lastIndex] == ByteArrayInputStream.class) {
                                        ByteArrayInputStream bais = new ByteArrayInputStream(buffer);
                                        args[lastIndex] = bais;
                                        argSet[lastIndex] = true;
                                    } else {
                                        // deal with the POST data as a string
                                        String data = new String(buffer, "UTF8");
                                        
                                        ParameterInfo[] paramInfos = new ParameterInfo[paramMap.size()];
                                        
                                        int a=0;
                                        for (ParameterInfo item : paramMap.values())
                                            paramInfos[a++] = item;
                                        
                                        // check if the last argument is a 'major' one i.e. what ends up as POST data
                                        int lastParamIndex = paramMap.size() - 1;
                                        if (lastParamIndex >= 0 && paramInfos[lastParamIndex].annotation != null && paramInfos[lastParamIndex].annotation.isMajor()) {
                                            // treat as complete argument (not argument map)
                                            ParameterInfo firstParamInfo = paramInfos[lastParamIndex];
                                            Object argValue = Serialisation.coerceFromJSON(firstParamInfo.klass, data, firstParamInfo.annotation.genericClassA(), firstParamInfo.annotation.genericClassB(), treatEmptyStringAsNull);

                                            args[lastParamIndex] = argValue;
                                            argSet[lastParamIndex] = true;

                                        } else {
                                            // treat it as an argument map
                                            @SuppressWarnings("unchecked")
                                            HashMap<String, Object> argumentMap = (HashMap<String, Object>) Serialisation.coerceFromJSON(HashMap.class, data, null, null, treatEmptyStringAsNull);

                                            for (Entry<String, Object> entry : argumentMap.entrySet()) {
                                                String argument = entry.getKey();

                                                ParameterInfo paramInfo = paramMap.get(argument);

                                                // skip unexpected parameters
                                                if (paramInfo == null)
                                                    continue;

                                                Object argValue = Serialisation.coerce(paramInfo.klass, entry.getValue(), null, null, treatEmptyStringAsNull);

                                                args[paramInfo.index] = argValue;
                                                argSet[paramInfo.index] = true;
                                            } // (for)
                                        }
                                    }
                                }
                            }

                            // possibly dealt with all arguments, but
                            // fill in defaults that might be missing
                            for (ParameterInfo paramInfo : paramMap.values()) {
                                if (argSet[paramInfo.index])
                                    continue;
                                
                                Object argValue = Serialisation.coerce(paramInfo.klass, null, null, null, treatEmptyStringAsNull);
                                
                                args[paramInfo.index] = argValue;

                                // don't need to do this, but record anyway
                                argSet[paramInfo.index] = true;
                            } // (for)
                        }
                        
                        classHint = serviceInfo.annotation.genericClassA();
                        serviceInfoHint = serviceInfo;

                        object = method.invoke(object, args);

                        // if it a void method, return true always
                        if (method.getReturnType() == void.class)
                            object = true;

                        continue;

                    } else {
                        throw new EndpointNotFoundException(part);
                    }
                } catch (IllegalArgumentException e) {
                    throw new RuntimeException(e);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                } catch (InvocationTargetException e) {
                    Throwable targetException = e.getTargetException();
                    if (targetException instanceof Exception)
                        throw (Exception) targetException;
                    
                    // must be an 'Error'
                    throw new Exception("A serious program failure occurred which probably affects the stability of this server.", targetException);
                }

            } // (if - service)

            throw new EndpointNotFoundException(restPath.toString());
        } // (for)

        if (props != null && props.containsKey("schema")) {
            return Schema.getSchemaObject(object);
        } 
        
        // return the object itself or the 'treat as value' method / field.
        Object result = Reflection.getDefaultValue(object);
        
        if (serviceInfoHint != null && !Strings.isBlank(serviceInfoHint.annotation.embeddedFieldName())) {
            Map<String,Object> wrappedResult = new LinkedHashMap<String,Object>();  
            wrappedResult.put(serviceInfoHint.annotation.embeddedFieldName(), result);
            
            return wrappedResult;
        }
        
        return result;
    } // (method)
    
    /**
     * A utility method to convert into the different maps types.
     * Servlettes use Map<String,String[]> whereas simple web servers might use Map<String,String>
     * (or vice versa)
     */
    public static Map<?, Object[]> convertIntoArrayKeyedMap(Map<?, ?> map) {
        Map<Object, Object[]> newMap = new HashMap<Object, Object[]>();
        for (Object key : map.keySet()) {
            newMap.put(key, new Object[] { map.get(key) });
        } // (for)

        return newMap;
    } // (method)

} // (class)