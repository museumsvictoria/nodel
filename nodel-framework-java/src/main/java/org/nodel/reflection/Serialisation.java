package org.nodel.reflection;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
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
    public static Object coerceFromJSON(Class<?> klass, String json) {
        return coerceFromJSON(klass, json, null, null);
    } // (method)
    
    /**
     * Convenience method: coerces from a JSON (giving 'generic class' hints)
     */
    public static Object coerceFromJSON(Class<?> klass, String json, Class<?> genericClassA, Class<?> genericClassB) {
        try {
            JSONObject jsonObject = new JSONObject(json);

            return coerce(klass, jsonObject, null, genericClassA, genericClassB);
        } catch (JSONException exc) {
            throw new SerialisationException("JSON not formatted correctly.", exc);
        }
    } // (method)    

    /**
     * Coerces an object (native or from 'json.org') package into a destination class.
     */
    public static Object coerce(Class<?> klass, Object obj) {
        return coerce(klass, obj, null, null, null);
    }
    
    /**
     * @param valueInfo used if generics are involved.
     */
    private static Object coerce(Class<?> klass, Object obj, ValueInfo valueInfo, Class<?> genericClassA, Class<?> genericClassB) {
        if (klass == null || klass.equals(Object.class)) {
            // we have no idea what class so just return the object itself
            // which may include primitive data types, JSONArrays of JSONObjects
            return obj;
        }
        
        // test string at the top because it's most commonly used
        if (klass == String.class) {
            if (obj == null)
                return null;
            
            else if (obj instanceof String) {
                return obj;
                
            } else if (obj instanceof JSONObject || obj instanceof JSONArray) {
                throw new SerialisationException("Could not coerce into a string.");
                
            } else {
                // the rest should be value types (boolean, int, float, etc.)
                return obj.toString();
            }
        }

        // test all the primitive types first
        else if (klass == Integer.class || klass == int.class) {
            if (obj == null)
                return 0;
            else if (obj instanceof Number)
                return ((Number) obj).intValue();
            else if (obj instanceof String)
                return Integer.parseInt((String)obj);
            else
                return (Integer)obj;
        }

        else if (klass == Long.class || klass == long.class) {
            if (obj == null)
                return 0;
            else if (obj instanceof Number)
                return ((Number) obj).longValue();
            else if (obj instanceof String)
                return Long.parseLong((String)obj);
            else
                return (Long)obj;
        }
        
        else if (klass == Double.class || klass == double.class) {
            if (obj == null)
                return 0;
            else if (obj instanceof Number)
                return ((Number) obj).doubleValue();
            else if (obj instanceof String)
                return Double.parseDouble((String) obj);
            else
                return (Double) obj;
        }

        else if (klass == Float.class || klass == float.class) {
            if (obj == null)
                return 0;
            else if (obj instanceof Number)
                return ((Number) obj).floatValue();
            else if (obj instanceof String)
                return Float.parseFloat((String) obj);
            else
                return (Float)obj;
        }

        else if (klass == Byte.class || klass == byte.class) {
            if (obj == null)
                return 0;
            else if (obj instanceof Number)
                return ((Number) obj).byteValue();
            else if (obj instanceof String)
                return Byte.parseByte((String) obj);
            else
                return (Byte) obj;
        }

        else if (klass == Boolean.class || klass == boolean.class) {
            if (obj == null)
                return false;
            else if (obj instanceof String)
                return Boolean.parseBoolean((String) obj);
            else
                return (Boolean) obj;
            
        } else if (klass == Byte[].class || klass == byte[].class) {
            if (obj == null)
                return null;
            else if (obj instanceof String)
                return Base64.decode((String) obj);
            else
                return (Byte[])obj;
        }

        // Test non-primitives now
        
        else if (obj == null) {
            // will be null regardless
            return null;
        }
        
        // test object types now

        else if (obj.equals(JSONObject.NULL)) {
            return null;
        }

        else if (klass == DateTime.class) {
            return tryParseDate(obj);
        }
        
        else if (klass == Date.class) {
            try {
                return DateFormat.getDateInstance().parse(obj.toString());
            } catch (ParseException e) {
                throw new SerialisationException(e);
            }
        }
        
        else if (klass == UUID.class) {
            return UUIDs.fromString(obj.toString());
        }

        else if (klass.isEnum()) {
            return Reflection.getEnumConstantInfo(klass, obj.toString()).constant;
        }

        // pure arrays
        else if (klass.isArray()) {
            return coerceIntoArray(klass, obj);
        }
        
        // collections (*)
        else if (Collection.class.isAssignableFrom(klass)) {
            if (valueInfo == null)
                return coerceIntoCollection(klass, obj, genericClassA);
            else
                return coerceIntoCollection(klass, obj, valueInfo.annotation.genericClassA());
        }
        
        // maps (*)
        else if (Map.class.isAssignableFrom(klass)) {
            if (valueInfo == null)
                return coerceIntoMap(klass, obj, genericClassA, genericClassB);
            else
                return coerceIntoMap(klass, obj, valueInfo.annotation.genericClassA(), valueInfo.annotation.genericClassB());
        }
        
        // plain object
        else {
            return coerceIntoPlainObject(klass, obj);
        }
        
    } // (method)

	/**
     * All other objects
     * 
     * (prechecked args)
     */
    private static Object coerceIntoPlainObject(Class<?> klass, Object obj) {
        if (obj instanceof String) {
            // try create an instance of it using String arg constructor or other standard methods
            return Reflection.createInstanceFromString(klass, (String) obj);
        }
        
        if (!(obj instanceof JSONObject)) {
            // leave it in its 'wrapped' form
            return obj;
        }
        
        // if it's just a general 'Object', no point in doing anything else but using the 'wrapped' form
        if (klass == Object.class) {
            return obj;
        }
        
        JSONObject jsonObject = (JSONObject) obj;
        
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
        
        // create a new instance of the object
        Object object;
        try {
            object = klass.newInstance();
        } catch (Exception exc) {
            throw new SerialisationException("Could not create instance of requested type plain object, " + klass.getName(), exc);
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
            Object objValue = coerce(valueClass, jsonValue, fieldInfo, null, null);

            field.setAccessible(true);

            try {
                if (objValue != null)
                    field.set(object, objValue);
            } catch (Exception e) {
                throw new SerialisationException("Could not set field '" + field.getName() + "'.");
            }
        } // (while)

        return object;        
    }
    
        
    /**
     * (args all prechecked) 
     */
    private static Object coerceIntoArray(Class<?> klass, Object jsonObject) {
        JSONArray jsonArray;
        if (jsonObject instanceof JSONArray) {
            jsonArray = (JSONArray) jsonObject;
        } else {
            // not an array, convert it into one
            jsonArray = new JSONArray();
            jsonArray.put(jsonObject);
        }
        
        // get the class of the items in the array
        Class<?> componentType = klass.getComponentType();

        // get the length of the array
        int length = jsonArray.length();

        // create the array instance
        Object array = Array.newInstance(componentType, length);

        for (int index = 0; index < length; index++) {
            Object jsonValue;
            try {
                jsonValue = jsonArray.get(index);
            } catch (JSONException e) {
                throw new SerialisationException("Could not retrieve an item out of the array.");
            }
            Object objValue = coerce(componentType, jsonValue, null, null, null);

            if (objValue != null)
                Array.set(array, index, objValue);
        } // (for)

        return array;
    } // (method)
    
    /**
     * (args all prechecked) 
     */
    @SuppressWarnings("unchecked")
    private static Object coerceIntoCollection(Class<?> klass, Object jsonObject, Class<?> componentType) {
        JSONArray jsonArray;
        if (jsonObject instanceof JSONArray) {
            jsonArray = (JSONArray) jsonObject;
        } else {
            // not an array, convert it into one
            jsonArray = new JSONArray();
            jsonArray.put(jsonObject);
        }
        
        Collection<Object> instance;
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
        
        // get the length of the array
        int length = jsonArray.length();

        for (int index = 0; index < length; index++) {
            Object jsonValue;
            try {
                jsonValue = jsonArray.get(index);
            } catch (JSONException exc) {
                throw new SerialisationException("Could not get an item out of an array - index " + index, exc);
            }
            Object objValue = coerce(componentType, jsonValue, null, null, null);
            
            instance.add(objValue);
        } // (for)

        return instance;
    } // (method)
    
    /**
     * (args all prechecked) 
     */
    @SuppressWarnings("unchecked")
    private static Object coerceIntoMap(Class<?> klass, Object obj, Class<?> keyType, Class<?> valueType) {
        JSONObject jsonMap;
        if (obj instanceof JSONObject) {
            jsonMap = (JSONObject) obj;
        } else {
            throw new SerialisationException("The object is not a map");
        }
        
        Map<Object,Object> instance;
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
        
        Iterator<String> keys = jsonMap.keys();
        while (keys.hasNext()) {
            String jsonKey = keys.next();
            
            Object objKey;
            if (keyType != String.class && keyType != Object.class) {
                objKey = coerce(keyType, jsonKey, null, null, null);
            } else {
                objKey = jsonKey;
            }

            Object jsonValue;
            try {
                jsonValue = jsonMap.get(jsonKey);
            } catch (JSONException exc) {
                throw new SerialisationException("Could not retrieve entry '" + jsonKey + "' out of map.");
            }
            Object objValue = coerce(valueType, jsonValue, null, null, null);
            
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
        try {
            Object wrappedObject = wrap(object);

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
     * Wraps a object into a JSONObject-supported type.
     * 
     * NOTE: This is different from JSONObject.wrap(...) in that it
     *       handles specially "annotated" classes correctly instead of
     *       Java Bean objects. 
     */
    private static Object wrap(Object object) {
        try {
            if (object == null) {
                return JSONObject.NULL;
            }
            
            if (object instanceof JSONObject       || object instanceof JSONArray  ||
                    JSONObject.NULL.equals(object) || object instanceof JSONString ||
                    object instanceof Byte         || object instanceof Character  ||
                    object instanceof Short        || object instanceof Integer    ||
                    object instanceof Long         || object instanceof Boolean    ||
                    object instanceof Float        || object instanceof Double     ||
                    object instanceof String) {
                return object;
            }
            
            if (object instanceof byte[]) {
                return Base64.encode((byte[])object);
            }
            
            if (object instanceof Collection) {
                JSONArray jsonArray = new JSONArray();

                Iterator<?> iter = ((Collection<?>) object).iterator();
                while (iter.hasNext())
                    jsonArray.put(wrap(iter.next()));

                return jsonArray;
            }
            
            if (object.getClass().isArray()) {
                JSONArray jsonArray = new JSONArray();
                
                int length = Array.getLength(object);
                for (int i = 0; i < length; i++)
                    jsonArray.put(wrap(Array.get(object, i)));
                
                return jsonArray;
            }
            
            if (object instanceof Map) {
                JSONObject jsonObject = new JSONObject();
                
                Iterator<?> i = ((Map<?, ?>)object).entrySet().iterator();
                while (i.hasNext()) {
                    Map.Entry<?, ?> e = (Map.Entry<?, ?>)i.next();
                    Object value = e.getValue();
                    if (value != null) {
                        jsonObject.put(e.getKey().toString(), wrap(value));
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
                    	String key = fieldInfo.annotation.name();
                        if (Strings.isNullOrEmpty(key))
                            key = fieldInfo.member.getName();

                        Object result = null;
                        
                        if (fieldInfo.member instanceof Field) {
                            Field field = (Field) fieldInfo.member;
                            field.setAccessible(true);

                            // invoke the getter
                            result = field.get(object);
                        } else {
                            // treat as method
                            Method method = (Method) fieldInfo.member;
                            
                            result = method.invoke(object, new Object[] {});
                        }
                        if (result != null) {
                            // jsonObject()
                            jsonObject.put(key, wrap(result));
                        }
                    } catch (Exception ignore) {
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
