package org.nodel.reflection;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.nodel.Base64;
import org.nodel.Strings;
import org.nodel.UUIDs;
import org.nodel.json.JSONArray;
import org.nodel.json.JSONException;
import org.nodel.json.JSONObject;
import org.nodel.json.JSONString;
import org.nodel.reflection.Reflection.AllowedInstanceInfo;

/**
 * Very flexible serialisation and deserialisation / coercion.
 */
public class Serialisation {
    
    /**
     * Convenience method: coerces from a JSON.
     */
    public static Object coerceFromJSON(Object dstObjOrClass, CharSequence json) {
        return coerceFromJSON(dstObjOrClass, json, null, null, false);
    }
    
    /**
     * Coerces a JSON string into a destination class (providing generics hints and other serialisation rules).
     */    
    public static Object coerceFromJSON(Object dstObjOrClass, CharSequence json, Class<?> genericClassA, Class<?> genericClassB) {
        return coerceFromJSON(dstObjOrClass, json, genericClassA, genericClassB, false);
    }
    
    /**
     * Coerces a JSON string into a destination class (providing generics hints and other serialisation rules).
     */
    public static Object coerceFromJSON(Object dstObjOrClass, CharSequence json, Class<?> genericClassA, Class<?> genericClassB, boolean treatEmptyStringsAsNull) {
        try {
            // unfortunately a String instead of a CharSequence has to be used for this JSON-library
            JSONObject jsonObject = new JSONObject(json.toString());

            return coerce(dstObjOrClass, jsonObject, null, genericClassA, genericClassB, treatEmptyStringsAsNull);
        } catch (JSONException exc) {
            throw new SerialisationException("JSON not formatted correctly.", exc);
        }
    }

    /**
     * Coerces an object (native or from 'json.org') package into a destination class.
     */
    public static Object coerce(Object dstObjOrClass, Object srcObj) {
        return coerce(dstObjOrClass, srcObj, null, null, null, false);
    }
    
    /**
     * Coerces an object into a destination class (providing generics hints).
     */    
    public static Object coerce(Object dstObjOrClass, Object srcObj, Class<?> genericClassA, Class<?> genericClassB) {
        return coerce(dstObjOrClass, srcObj, null, genericClassA, genericClassB, false);
    }    
    
    /**
     * Coerces an object into a destination class (providing generics hints and other serialisation rules).
     */    
    public static Object coerce(Object dstObjOrClass, Object srcObj, Class<?> genericClassA, Class<?> genericClassB, boolean treatEmptyStringsAsNull) {
        return coerce(dstObjOrClass, srcObj, null, genericClassA, genericClassB, treatEmptyStringsAsNull);
    }
    
    /**
     * @param valueInfo used if generics are involved.
     */
    private static Object coerce(Object dstObjOrClass, Object srcObj, ValueInfo valueInfo, Class<?> genericClassA, Class<?> genericClassB, boolean treatEmptyStringsAsNull) {
        // The order that the following tests are performed relates to the likelihood 
        // of each test being required, hopefully leading to faster 
        // typical runtime operation; readability could otherwise be improved.
        
        Class<?> klass;
        
        if (dstObjOrClass instanceof Class<?>) {
            klass = (Class<?>) dstObjOrClass;
        } else {
            klass = (dstObjOrClass != null ? dstObjOrClass.getClass() : null);
        }
        
        if (klass == null || klass.equals(Object.class)) {
            // we have no idea what specific class so just return the object itself
            // but normalise to standard maps and lists if needed (instead of 
            // JSONArrays of JSONObjects)
            if (srcObj instanceof JSONObject) {
                return coerce(Map.class, srcObj, null, null, treatEmptyStringsAsNull);
                
            } else if (srcObj instanceof JSONArray) {
                return coerce(List.class, srcObj, null, null, treatEmptyStringsAsNull);
                
            } else if (srcObj == JSONObject.NULL){
                return null;
                
            } else {
                // return the objects as they are
                
                
                // (treat empty strings as nulls?)
                if (treatEmptyStringsAsNull && srcObj instanceof String && ((String)srcObj).length() == 0)
                    return null;
                else
                    return srcObj;
            }
        }
        
        // test string at the top because it's most commonly used
        
        if (klass == String.class) {
            if (srcObj == null)
                return null;
            else if (srcObj instanceof String) {
                if (treatEmptyStringsAsNull && ((String)srcObj).length() == 0)
                    return null;
                else
                    return srcObj;
            } else if (srcObj instanceof JSONObject || srcObj instanceof JSONArray)
                throw new SerialisationException("Could not coerce into a string.");
            else if (srcObj.equals(JSONObject.NULL))
                return null;
            else
                // the rest should be value types (boolean, int, float, etc.)
                return srcObj.toString();
        }

        // test all the primitive types first
        else if (klass == Integer.class || klass == int.class) {
            if (srcObj == null)
                return 0;
            else if (srcObj instanceof Number)
                return ((Number) srcObj).intValue();
            else if (srcObj instanceof String)
                return Integer.parseInt((String)srcObj);
            else if (srcObj.equals(JSONObject.NULL))
                return null;
            else
                return (Integer)srcObj;
        }

        else if (klass == Long.class || klass == long.class) {
            if (srcObj == null)
                return 0;
            else if (srcObj instanceof Number)
                return ((Number) srcObj).longValue();
            else if (srcObj instanceof String)
                return Long.parseLong((String)srcObj);
            else if (srcObj.equals(JSONObject.NULL))
                return null;            
            else
                return (Long)srcObj;
        }
        
        else if (klass == Double.class || klass == double.class) {
            if (srcObj == null)
                return 0;
            else if (srcObj instanceof Number)
                return ((Number) srcObj).doubleValue();
            else if (srcObj instanceof String)
                return Double.parseDouble((String) srcObj);
            else if (srcObj.equals(JSONObject.NULL))
                return null;            
            else
                return (Double) srcObj;
        }

        else if (klass == Float.class || klass == float.class) {
            if (srcObj == null)
                return 0;
            else if (srcObj instanceof Number)
                return ((Number) srcObj).floatValue();
            else if (srcObj instanceof String)
                return Float.parseFloat((String) srcObj);
            else if (srcObj.equals(JSONObject.NULL))
                return null;            
            else
                return (Float)srcObj;
        }

        else if (klass == Byte.class || klass == byte.class) {
            if (srcObj == null)
                return 0;
            else if (srcObj instanceof Number)
                return ((Number) srcObj).byteValue();
            else if (srcObj instanceof String)
                return Byte.parseByte((String) srcObj);
            else if (srcObj.equals(JSONObject.NULL))
                return null;            
            else
                return (Byte) srcObj;
        }

        else if (klass == Boolean.class || klass == boolean.class) {
            if (srcObj == null)
                return false;
            else if (srcObj instanceof String)
                return Boolean.parseBoolean((String) srcObj);
            else if (srcObj.equals(JSONObject.NULL))
                return null;            
            else
                return (Boolean) srcObj;
            
        } else if (klass == Byte[].class || klass == byte[].class) {
            if (srcObj == null)
                return null;
            else if (srcObj instanceof String)
                return Base64.decode((String) srcObj);
            else if (srcObj.equals(JSONObject.NULL))
                return null;            
            else
                return (Byte[])srcObj;
        }

        // Test non-primitives now
        
        else if (srcObj == null) {
            // will be null regardless
            return null;
        }
        
        // test object types now

        else if (srcObj.equals(JSONObject.NULL)) {
            return null;
        }

        else if (klass == DateTime.class) {
            return tryParseDate(srcObj);
        }
        
        else if (klass == Date.class) {
            try {
                return DateFormat.getDateInstance().parse(srcObj.toString());
            } catch (ParseException e) {
                throw new SerialisationException(e);
            }
        }
        
        else if (klass == UUID.class) {
            return UUIDs.fromString(srcObj.toString());
        }

        else if (klass.isEnum()) {
            return Reflection.getEnumConstantInfo(klass, srcObj.toString()).constant;
        }

        // pure arrays
        else if (klass.isArray()) {
            return coerceIntoArray(dstObjOrClass, srcObj, treatEmptyStringsAsNull);
        }
        
        // collections (*)
        else if (Collection.class.isAssignableFrom(klass)) {
            if (valueInfo == null)
                return coerceIntoCollection(dstObjOrClass, srcObj, genericClassA, treatEmptyStringsAsNull);
            else
                return coerceIntoCollection(dstObjOrClass, srcObj, valueInfo.genericClassA, treatEmptyStringsAsNull);
        }
        
        // maps (*)
        else if (Map.class.isAssignableFrom(klass)) {
            if (valueInfo == null)
                return coerceIntoMap(dstObjOrClass, srcObj, genericClassA, genericClassB, treatEmptyStringsAsNull);
            else
                return coerceIntoMap(dstObjOrClass, srcObj, valueInfo.genericClassA, valueInfo.genericClassB, treatEmptyStringsAsNull);
        }
        
        // plain object
        else {
            return coerceIntoPlainObject(dstObjOrClass, srcObj, treatEmptyStringsAsNull);
        }
        
    } // (method)
    
	/**
     * All other objects
     * 
     * (prechecked args)
     */
    private static Object coerceIntoPlainObject(Object dstObjOrClass, Object srcObj, boolean treatEmptyStringsAsNull) {
        Class<?> klass;
        Object dstObj;
        
        if (dstObjOrClass instanceof Class<?>) {
            klass = (Class<?>) dstObjOrClass;
            dstObj = null;
        } else {
            klass = (dstObjOrClass != null ? dstObjOrClass.getClass() : null);
            dstObj = dstObjOrClass;
        }
        
        if (srcObj instanceof String) {
            // try create an instance of it using String arg constructor or other standard methods
            return Reflection.createInstanceFromString(klass, (String) srcObj);
        }
        
        // common currency
        JSONObject jsonObject;
        
        if (srcObj instanceof JSONObject) {
            jsonObject = (JSONObject) srcObj;
        } else {
            Object wrapped = wrap(srcObj);
            
            if (wrapped instanceof JSONObject)
                jsonObject = (JSONObject)wrapped;
            else
                throw new SerialisationException("The provided object could not be wrapped into a usable form (special object).");
        }
        
        // if it's just a general 'Object', no point in doing anything else but using the 'wrapped' form
        if (klass == Object.class) {
            return srcObj;
        }
        
        // check whether to choose one of allowable instances
        AllowedInstanceInfo[] allowedInstanceInfos = Reflection.getAllowedInstances(klass);
        if (allowedInstanceInfos != null && allowedInstanceInfos.length > 0) {
            Class<?> selectedClass = null;
            
            // go through the allowed instances, rejecting the ones that don't contain matching fields
            for (AllowedInstanceInfo allowedInstance : allowedInstanceInfos) {
                selectedClass = allowedInstance.clazz;
                
                Map<String, ValueInfo> valueMap = Reflection.getValueInfoMap(selectedClass);
                
                // go through all the present fields
                Iterator<String> fields = jsonObject.keys();
                while(fields.hasNext()) {
                    String field = fields.next();
                    
                    if (!valueMap.containsKey(field.toLowerCase())) {
                        selectedClass = null;
                        break;
                    }
                }
                
                // keep going until we have a selected class
                if (selectedClass == null)
                    continue;
                else
                    break;
            } // (for)
            
            // if we still don't have a selected class, just use the first one
            if (selectedClass == null)
                klass = allowedInstanceInfos[0].clazz;
            else
                klass = selectedClass;
        }
        
        // create a new instance of the object if required
        Object object;
        
        // create a new instance or ...
        if (dstObj == null) {
            try {
                object = klass.newInstance();
            } catch (Exception exc) {
                throw new SerialisationException("Could not create instance of requested type plain object, " + klass.getName(), exc);
            }
        } else {
            // ... use provided object
            object = dstObj;
        }
        
        // go through each listed field info and deserialise
        Iterator<String> keys = jsonObject.keys();
        while (keys.hasNext()) {
            String key = keys.next();

            ValueInfo fieldInfo = Reflection.getValueInfosByName(klass, key);
            if (fieldInfo == null || !(fieldInfo.member instanceof Field))
                // only 0 argument methods are valid as fields 
                continue;
            
            Field field = (Field) fieldInfo.member;

            Class<?> valueClass = field.getType();

            Object jsonValue;
            try {
                jsonValue = jsonObject.get(key);
            } catch (JSONException exc) {
                throw new SerialisationException("Could not get entry '" + key + "' out of the object.");
            }

            // choose when set the field or use a designated 'setter'
            SetterInfo setterInfo = fieldInfo.setter;
            if (setterInfo == null) {
                Object objValue = coerce(valueClass, jsonValue, fieldInfo, null, null, treatEmptyStringsAsNull);
                
                try {
                    // set field directly
                    field.setAccessible(true);

                    if (objValue != null) {
                        field.set(object, objValue);
                    }
                    
                } catch (Exception e) {
                    throw new SerialisationException("Could not set field '" + field.getName() + "'.");
                }

            } else {
                // set indirectly using a 'setter' method
                Method setterMethod = setterInfo.method;
                
                Class<?>[] paramClasses = setterMethod.getParameterTypes();
                
                Object objValue = coerce(paramClasses[0], jsonValue, fieldInfo, null, null, treatEmptyStringsAsNull);

                Exception exception = null;
                
                try {
                    if (objValue != null)
                        setterMethod.invoke(object, objValue);
                    
                } catch (InvocationTargetException exc) {
                    Throwable actualException = exc.getTargetException();
                    throw new RuntimeException(actualException);
                    
                } catch (IllegalAccessException e) {
                    exception = e;
                    
                } catch (IllegalArgumentException e) {
                    exception = e;
                }
                
                if (exception != null)
                    throw new SerialisationException("Could not set field '" + field.getName() + "'.", exception);
            }
        } // (while)

        return object;        
    }
    
        
    /**
     * (args all prechecked) 
     * @param treatEmptyStringsAsNull 
     */
    private static Object coerceIntoArray(Object dstObjOrClass, Object srcObj, boolean treatEmptyStringsAsNull) {
        Class<?> klass;
        Object dstObj;
        
        if (dstObjOrClass instanceof Class<?>) {
            klass = (Class<?>) dstObjOrClass;
            dstObj = null;
        } else {
            klass = (dstObjOrClass != null ? dstObjOrClass.getClass() : null);
            dstObj = dstObjOrClass;
        }
        
        JSONArray jsonArray;
        if (srcObj instanceof JSONArray) {
            jsonArray = (JSONArray) srcObj;
        } else {
            // not an array, convert it into one
            Object wrapped = wrap(srcObj);
            if (wrapped instanceof JSONArray)
                jsonArray = (JSONArray) wrapped;
            else {
                // convert whatever object it is into a single-element array
                jsonArray = new JSONArray();
                jsonArray.put(wrapped);
            }
        }
        
        // get the class of the items in the array
        Class<?> componentType = klass.getComponentType();

        // get the length of the array
        int length = jsonArray.length();

        // create the array instance or use the provided object
        Object array;
        if (dstObj == null)
            array = Array.newInstance(componentType, length);
        else
            array = dstObj;

        for (int index = 0; index < length; index++) {
            Object jsonValue;
            try {
                jsonValue = jsonArray.get(index);
            } catch (JSONException e) {
                throw new SerialisationException("Could not retrieve an item out of the array.");
            }
            Object objValue = coerce(componentType, jsonValue, null, null, null, treatEmptyStringsAsNull);

            if (objValue != null)
                Array.set(array, index, objValue);
        } // (for)

        return array;
    } // (method)
    
    /**
     * (args all prechecked) 
     * @param treatEmptyStringsAsNull 
     */
    @SuppressWarnings("unchecked")
    private static Object coerceIntoCollection(Object dstObjOrClass, Object srcObj, Class<?> componentType, boolean treatEmptyStringsAsNull) {
        Class<?> klass;
        Object dstObj;
        
        if (dstObjOrClass instanceof Class<?>) {
            klass = (Class<?>) dstObjOrClass;
            dstObj = null;
        } else {
            klass = (dstObjOrClass != null ? dstObjOrClass.getClass() : null);
            dstObj = dstObjOrClass;
        }
        
        JSONArray jsonArray;
        if (srcObj instanceof JSONArray) {
            jsonArray = (JSONArray) srcObj;
        } else {
            // not an array, convert it into one
            Object wrapped = wrap(srcObj);
            if (wrapped instanceof JSONArray)
                jsonArray = (JSONArray) wrapped;
            else {
                // convert whatever object it is into a single-element array
                jsonArray = new JSONArray();
                jsonArray.put(wrapped);
            }
        }
        
        Collection<Object> instance;
        
        // create own instance or...
        if (dstObj == null) {
            if (klass.isInterface() || Modifier.isAbstract(klass.getModifiers())) {
                // cannot create instance of interface or abstract classes, so use
                // a well new 'Collection' class, ArrayList
                instance = new ArrayList<Object>();
            } else {
                try {
                    instance = (Collection<Object>) klass.newInstance();
                } catch (Exception exc) {
                    throw new SerialisationException("Could not create instance of requested type, Collection.", exc);
                }
            }
        } else {
            // ...use provided one
            instance = (Collection<Object>) dstObj;
        }

        // get the length of the array
        int length = jsonArray.length();

        for (int index = 0; index < length; index++) {
            Object jsonValue;
            try {
                jsonValue = jsonArray.get(index);
            } catch (JSONException exc) {
                throw new SerialisationException("Could not get an item out of an array - index " + index, exc);
            }
            Object objValue = coerce(componentType, jsonValue, null, null, null, treatEmptyStringsAsNull);
            
            instance.add(objValue);
        } // (for)

        return instance;
    } // (method)
    
    /**
     * (args all prechecked) 
     * @param treatEmptyStringsAsNull 
     */
    @SuppressWarnings("unchecked")
    private static Object coerceIntoMap(Object dstObjOrClass, Object srcObj, Class<?> keyType, Class<?> valueType, boolean treatEmptyStringsAsNull) {
        Class<?> klass;
        Object dstObj;
        
        if (dstObjOrClass instanceof Class<?>) {
            klass = (Class<?>) dstObjOrClass;
            dstObj = null;
        } else {
            klass = (dstObjOrClass != null ? dstObjOrClass.getClass() : null);
            dstObj = dstObjOrClass;
        }     
        
        JSONObject jsonMap;
        if (srcObj instanceof JSONObject) {
            jsonMap = (JSONObject) srcObj;
        } else {
            Object wrappedObject = wrap(srcObj);
            
            if (!(wrappedObject instanceof JSONObject))
                throw new SerialisationException("The provided object could not be wrapped into a usable form (special map).");
            
            jsonMap = (JSONObject) wrappedObject;
        }
        
        Map<Object, Object> instance;

        // create a fresh instance or...
        if (dstObj == null) {
            if (klass.isInterface() || Modifier.isAbstract(klass.getModifiers())) {
                // cannot create instance of interface or abstract classes, so use
                // a well new 'Map' class, LinkedHashMap which preservers order
                instance = new LinkedHashMap<Object, Object>();
            } else {
                // use the klass that was specified
                try {
                    instance = (Map<Object, Object>) klass.newInstance();
                } catch (Exception exc) {
                    throw new SerialisationException("Could not create an instance of a requested type, Map.", exc);
                }
            }
        } else {
            // ... use the provided object
            instance = (Map<Object, Object>) dstObj;
        }
        
        Iterator<String> keys = jsonMap.keys();
        while (keys.hasNext()) {
            String jsonKey = keys.next();
            
            Object objKey;
            if (keyType != String.class && keyType != Object.class) {
                objKey = coerce(keyType, jsonKey, null, null, null, treatEmptyStringsAsNull);
            } else {
                objKey = jsonKey;
            }

            Object jsonValue;
            try {
                jsonValue = jsonMap.get(jsonKey);
                
            } catch (JSONException exc) {
                throw new SerialisationException("Could not retrieve entry '" + jsonKey + "' out of map.");
            }
            Object objValue = coerce(valueType, jsonValue, null, null, null, treatEmptyStringsAsNull);
            
            instance.put(objKey, objValue);

        } // (while)

        return instance;
    } // (method)

    /**
     * (overloaded - no indentation specified)
     */
    public static String serialise(Object object) throws SerialisationException {
        return serialise(object, 0);
    }

    /**
     * Performs serialisation of an object.
     */
    public static String serialise(Object object, int indent) {
        return serialise(object, indent, false);
    }
    
    /**
     * (optionally exclude passwords)
     */
    public static String serialise(Object object, int indent, boolean excludePasswords) {
        try {
            Object wrappedObject = wrap(object, excludePasswords);

            if (wrappedObject instanceof JSONObject)
                return ((JSONObject) wrappedObject).toString(indent);

            else if (wrappedObject instanceof JSONArray)
                return ((JSONArray) wrappedObject).toString(indent);
            
            else 
                return wrappedObject.toString();

        } catch (JSONException exc) {
            throw new SerialisationException(exc);
        }
    } // (method)
    
    /**
     * Deserialises an object from JSON text.
     */
    public static Object deserialise(Class<?> klass, String json) {
        return coerceFromJSON(klass, json);
    }
    
    /**
     * Wraps a object into a JSONObject-supported type.
     * 
     * NOTE: This is different from JSONObject.wrap(...) in that it
     *       handles specially "annotated" classes correctly instead of
     *       Java Bean objects. 
     */
    public static Object wrap(Object object) {
        return wrap(object, false);
    }
    
    /**
     * (optionally exclude passwords for annotated classes)
     */
    public static Object wrap(Object object, boolean excludePasswords) {
        try {
            if (object == null) {
                return JSONObject.NULL;
            }
            
            if (object instanceof JSONObject       || object instanceof JSONArray  ||
                    JSONObject.NULL.equals(object) || object instanceof JSONString ||
                    object instanceof Number       || object instanceof Character  ||
                    object instanceof Boolean      || object instanceof String) {
                return object;
            }
            
            if (object instanceof byte[]) {
                return Base64.encode((byte[])object);
            }
            
            if (object instanceof Collection) {
                JSONArray jsonArray = new JSONArray();

                Iterator<?> iter = ((Collection<?>) object).iterator();
                while (iter.hasNext())
                    jsonArray.put(wrap(iter.next(), excludePasswords));

                return jsonArray;
            }
            
            if (object.getClass().isArray()) {
                JSONArray jsonArray = new JSONArray();
                
                int length = Array.getLength(object);
                for (int i = 0; i < length; i++)
                    jsonArray.put(wrap(Array.get(object, i), excludePasswords));
                
                return jsonArray;
            }
            
            if (object instanceof Map) {
                JSONObject jsonObject = new JSONObject();
                
                Iterator<?> i = ((Map<?, ?>)object).entrySet().iterator();
                while (i.hasNext()) {
                    Map.Entry<?, ?> e = (Map.Entry<?, ?>)i.next();
                    Object value = e.getValue();
                    if (value != null) {
                        jsonObject.put(e.getKey().toString(), wrap(value, excludePasswords));
                    }
                }                
                
                return jsonObject;
            }
            
            // revert to specially annotated classes
            Class<?> klass = object.getClass();
            
            if (klass.isEnum()) {
                return Reflection.getEnumConstantInfo(klass, object).title;
            }            
            
            // get the fields if there are any
            ValueInfo[] fieldInfos = Reflection.getValueInfos(klass);
            if (fieldInfos.length > 0) {
                JSONObject jsonObject = new JSONObject();

                for (ValueInfo fieldInfo : fieldInfos) {
                    try {
                    	String key = fieldInfo.name;
                        if (Strings.isNullOrEmpty(key))
                            key = fieldInfo.member.getName();

                        if (excludePasswords) {
                            // if directed to, skip the format if it's 'password'
                            String format = fieldInfo.format;
                            if ("password".equalsIgnoreCase(format))
                                continue;
                        }
                        
                        Object result = null;
                        
                        if (fieldInfo.member instanceof Field) {
                            Field field = (Field) fieldInfo.member;
                            field.setAccessible(true);

                            // invoke the getter
                            result = field.get(object);
                        } else {
                            // treat as method
                            Method method = (Method) fieldInfo.member;
                            
                            result = method.invoke(object);
                        }

                        if (result != null) {
                            // jsonObject()
                            jsonObject.put(key, wrap(result, excludePasswords));
                        }
                    } catch (Exception ignore) {
                        System.out.println(ignore);
                        // ignore any reflection related issues
                    }
                } // (for)

                return jsonObject;
            }
            
            // no fields, so just return the .toString() method
            return object.toString();
            
        } catch(Exception exception) {
            return null;
        }
    }
    

    private static DateTimeFormatter _customFullFormatter = DateTimeFormat.forPattern("EEE MMM dd HH:mm:ss 'EST' yyyy");

    /**
     * Tries to parse a date
     */
    private static DateTime tryParseDate(Object jsonValue) {
        if (jsonValue == null)
            return null;

        String strValue = jsonValue.toString();
        DateTime result;

        result = tryParseISODateTime(strValue);
        if (result != null)
            return result;

        result = tryParseFullDateTime(strValue);
        if (result != null)
            return result;

        result = tryParseLongDateTime(strValue);
        if (result != null)
            return result;

        result = tryParseMediumDateTime(strValue);
        if (result != null)
            return result;

        result = tryParseShortDateTime(strValue);
        if (result != null)
            return result;

        result = tryParseCustomFullDateTime(strValue);
        if (result != null)
            return result;

        throw new SerialisationException("Could not parse into a common date format - '" + jsonValue + "'");
    }

    private static DateTime tryParseISODateTime(String value) {
        try {
            return DateTime.parse(value);
        } catch (Exception exc) {
            return null;
        }
    }

    private static DateTime tryParseFullDateTime(String value) {
        try {
            return DateTime.parse(value, DateTimeFormat.fullDateTime());
        } catch (Exception exc) {
            return null;
        }
    }

    private static DateTime tryParseLongDateTime(String value) {
        try {
            return DateTime.parse(value, DateTimeFormat.longDateTime());
        } catch (Exception exc) {
            return null;
        }
    }

    private static DateTime tryParseMediumDateTime(String value) {
        try {
            return DateTime.parse(value, DateTimeFormat.mediumDateTime());
        } catch (Exception exc) {
            return null;
        }
    }

    private static DateTime tryParseShortDateTime(String value) {
        try {
            return DateTime.parse(value, DateTimeFormat.shortDateTime());
        } catch (Exception exc) {
            return null;
        }
    }

    private static DateTime tryParseCustomFullDateTime(String value) {
        try {
            return DateTime.parse(value, _customFullFormatter);
        } catch (Exception exc) {
            return null;
        }
    }
    
} // (class)
