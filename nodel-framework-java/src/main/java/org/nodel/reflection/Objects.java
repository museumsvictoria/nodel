package org.nodel.reflection;

import java.lang.reflect.Array;
import java.util.AbstractList;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.nodel.json.JSONArray;
import org.nodel.json.JSONException;
import org.nodel.json.JSONObject;

import java.util.Set;

/**
 * Some convenience methods related to general objects being used within the Nodel platform incl. JSON-related
 * objects which are natural currency in the platform.
 */
public class Objects {
    
    /**
     * Tests whether the two objects are of equal value. 
     */
    public static boolean sameValue(Object obj1, Object obj2) {
        return sameValue(obj1, obj2, 0);
    }

    /**
     * With a recursive limit / circular reference check.
     * (internal use only)
     */
    private static boolean sameValue(Object obj1, Object obj2, int level) {
        // have an arbitrary limit (255 should be more than plenty)
        if (level > 255)
            return false;

        if (obj1 == null && obj2 == null) {
            return true;
        }

        else if (obj1 == null && obj2.equals(obj1)) {
            return true;
        }

        else if (obj2 == null && obj1.equals(obj2)) {
            return true;
        }
        
        // (both 'obj1' and 'obj2' are not null)
        
        // try as collections...
        Collection<?> collection1 = asCollection(obj1);
        Collection<?> collection2 = asCollection(obj2);

        if (collection1 != null && collection2 != null) {
            return sameCollectionValues(collection1, collection2, level++);
        }

        // try as maps...
        Map<?,?> map1 = asMap(obj1);
        Map<?,?> map2 = asMap(obj2);
        
        if (map1 != null && map2 != null) {
            return sameMapKeysAndValues(map1, map2, level++);
        }
        
        // TODO: test annotated native Java classes

        // otherwise, try their native 'equals'
        return obj1.equals(obj2);
    }
    
    /**
     * Returns a collection, wrapped collection (if possible), or null. 
     */
    private static Collection<?> asCollection(Object possibleCollection) {
        if (possibleCollection instanceof Collection)
            return (Collection<?>) possibleCollection;

        else if (possibleCollection.getClass().isArray())
            return arrayAsCollection(possibleCollection);

        else if (possibleCollection instanceof JSONArray)
            return jsonArrayAsCollection((JSONArray) possibleCollection);

        else
            return null;
    }

    /**
     * Wraps an array into a collection object (backing only; data not copied).
     * (assumes parameter is a non-null array)
     */
    private static Collection<?> arrayAsCollection(final Object array) {
        return new AbstractList<Object>() {

            Object _array = array;

            @Override
            public Object get(int index) {
                return Array.get(array, index);
            }

            @Override
            public int size() {
                return Array.getLength(_array);
            }

        };
    }
    
    /**
     * Wraps a JSONArray object into a collection object (backing only; data not copied).
     * (assumes parameter is non-null)
     */
    private static Collection<?> jsonArrayAsCollection(final JSONArray jsonArray) {
        return new AbstractList<Object>() {

            JSONArray _jsonArray = jsonArray;

            @Override
            public Object get(int index) {
                try {
                    return _jsonArray.get(index);
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public int size() {
                return _jsonArray.length();
            }

        };
    }
    
    /**
     * Returns a map or wrapped map (if possible), or null.
     */
    private static Map<?, ?> asMap(Object possibleMap) {
        if (possibleMap instanceof Map)
            return (Map<?, ?>) possibleMap;

        else if (possibleMap instanceof JSONObject)
            return jsonObjectAsMap((JSONObject) possibleMap);

        else
            return null;
    }

    /**
     * A wrapped JSONObject as a map (backing only, data not copied).
     */
    private static Map<?, ?> jsonObjectAsMap(final JSONObject jsonObject) {
        AbstractMap<?, ?> map = new AbstractMap<Object, Object>() {

            @Override
            public Set<Map.Entry<Object, Object>> entrySet() {
                return jsonObjectEntrySet(jsonObject);
            }

        };

        return map;
    }
    
    /**
     * (convenience function used by 'jsonObjectAsMap')
     */
    private static Set<Map.Entry<Object, Object>> jsonObjectEntrySet(final JSONObject jsonObject) {
        return new AbstractSet<Map.Entry<Object, Object>>() {

            Iterator<String> _keys = jsonObject.keySet().iterator();

            JSONObject _backing = jsonObject;

            @Override
            public Iterator<Entry<Object, Object>> iterator() {
                return new Iterator<Map.Entry<Object, Object>>() {

                    @Override
                    public boolean hasNext() {
                        return _keys.hasNext();
                    }

                    @Override
                    public Entry<Object, Object> next() {
                        try {
                            String nextKey = _keys.next();
                            Object nextValue = _backing.get(nextKey);

                            return new AbstractMap.SimpleEntry<Object, Object>(nextKey, nextValue);

                        } catch (JSONException exc) {
                            throw new RuntimeException(exc);
                        }
                    }

                    @Override
                    public void remove() {
                        throw new UnsupportedOperationException();
                    }

                };
            }

            @Override
            public int size() {
                return _backing.length();
            }

        };
    }

    /**
     * Returns true if all values are the same given two collections.
     * (assumes both are instance tested and not null) 
     */
    private static boolean sameCollectionValues(Collection<?> collection1, Collection<?> collection2, int level) {
        Iterator<?> e1 = collection1.iterator();
        Iterator<?> e2 = collection2.iterator();
        
        level++;
        
        while (e1.hasNext() && e2.hasNext()) {
            Object o1 = e1.next();
            Object o2 = e2.next();
            if (!(o1 == null ? o2 == null : sameValue(o1, o2, level)))
                return false;
        }
        return !(e1.hasNext() || e2.hasNext());
    }
    
    /**
     * Returns true if all keys of both map to the same values of both.
     * (assumes both are instance tested and not null)
     */
    private static boolean sameMapKeysAndValues(Map<?, ?> map1, Map<?, ?> map2, int level) {
        Set<?> keys1 = map1.keySet();
        Set<?> keys2 = map2.keySet();

        if (keys1.size() != keys2.size())
            return false;
        
        level++;

        for (Entry<?, ?> entry1 : map1.entrySet()) {
            Object key1 = entry1.getKey();
            Object value1 = entry1.getValue();

            // get the value without necessarily using 'containsKey'
            Object value2 = map2.get(key1);
            if (value2 == null && !map2.containsKey(key1))
                return false;

            // NOTE: value1 and value2 could legitimately both be 'null here

            if (!sameValue(value1, value2, level))
                return false;

            // keep going through the rest of the keys...
        }

        // have gone through the key set
        return true;
    }

}
