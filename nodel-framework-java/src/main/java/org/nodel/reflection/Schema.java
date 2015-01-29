package org.nodel.reflection;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import org.joda.time.DateTime;
import org.nodel.Strings;
import org.nodel.reflection.Reflection.AllowedInstanceInfo;

/**
 * Facilitates with generating a schema from a given object or class similar to common 
 * JSON-schema definitions.
 */
public class Schema {

    /**
     * Class thread lock.
     */
    private static Object s_staticLock = new Object();

    public static Map<String, Object> getSchemaObject(Object object) {
        synchronized (s_staticLock) {
            if (object == null)
                throw new NullPointerException("Object");

            return getSchemaObject(0, object.getClass(), null, null);
        }
    } // (method)

    public static Map<String, Object> getSchemaObject(Class<?> klass) {
        synchronized (s_staticLock) {
            return getSchemaObject(0, klass, null, null);
        }
    }

    private static Map<String, Object> getSchemaObject(int level, Class<?> klass, ValueInfo classFieldInfo, ServiceInfo classServiceInfo) {
        level++;
        
        // holds any allowed instances (normally sub-classes)
        AllowedInstanceInfo[] allowedInstanceInfos = null;
        
        Map<String, Object> schema = new LinkedHashMap<String, Object>();
        
        if (level > 128) {
            // probably got a circular dependency, so bail out now with an empty schema
            return schema;
        }

        if (klass == int.class || klass == short.class || klass == long.class ||
                klass == Integer.class || klass == Long.class || klass == Short.class) {
            updateSchema("integer", schema, classFieldInfo, classServiceInfo);
            return schema;

        } else if (klass == Float.class || klass == Double.class || klass == float.class || klass == double.class) {
            updateSchema("number", schema, classFieldInfo, classServiceInfo);
            return schema;

        } else if (klass == String.class) {
            updateSchema("string", schema, classFieldInfo, classServiceInfo);
            return schema;

        } else if (klass == Boolean.class || klass == boolean.class) {
            updateSchema("boolean", schema, classFieldInfo, classServiceInfo);
            return schema;

        } else if (klass == DateTime.class || klass == Date.class) {
            updateSchema("string", schema, classFieldInfo, classServiceInfo);
            schema.put("format", "date-time");
            return schema;

        } else if (klass == UUID.class) {
            updateSchema("string", schema, classFieldInfo, classServiceInfo);
            // this is not necessarily a standard JSON format
            schema.put("format", "uuid");
            return schema;
            
        } else if (klass.isEnum()) {
            updateSchema("string", schema, classFieldInfo, classServiceInfo);
            
            schema.put("enum", Reflection.getEnumValues(klass));
            
            StringBuilder desc = new StringBuilder();
            for(EnumInfo enumInfo : Reflection.getEnumInfos(klass)) {
                if (!Strings.isNullOrEmpty(enumInfo.desc)) {
                    if (desc.length() == 0)
                        desc.append("(");
                    else if(desc.length() > 0)
                        desc.append(", ");
                    
                    desc.append("'" + enumInfo.title + "' - ");
                    desc.append(enumInfo.desc);
                }
            }
            if (desc.length() > 0) {
                desc.append(")");
                
                Object currentDesc = schema.get("description");

                // add it to the current description if one already exists (using 'desc' annotation)
                schema.put("description", currentDesc != null ? currentDesc + " " + desc.toString() : desc);
            }
            
            return schema;
            
        } else if (klass == byte[].class) {
            updateSchema("string", schema, classFieldInfo, classServiceInfo);
            schema.put("format", "base64");
            return schema;

        } else if (klass.isArray() || klass == List.class || Collection.class.isAssignableFrom(klass)) {
            // "array"
            updateSchema("array", schema, classFieldInfo, classServiceInfo);

            // get class of the items
            Class<?> itemClass;
            if (klass.isArray())
                itemClass = klass.getComponentType();
            else {
                if (classServiceInfo != null && classServiceInfo.annotation != null)
                    itemClass = classServiceInfo.annotation.genericClassA();
                else if (classFieldInfo != null && classFieldInfo.annotation != null)
                    itemClass = classFieldInfo.annotation.genericClassA();
                else
                    itemClass = Object.class;
            }

            Object itemClassSchema = getSchemaObject(level, itemClass, null, null);
            schema.put("items", itemClassSchema);

            return schema;

        } else if (klass == Map.class || Map.class.isAssignableFrom(klass)) {
            // must be keyed by 'String'
            updateSchema("object", schema, classFieldInfo, classServiceInfo);

            // because of Java type erasure with generics, this has to be done
            Class<?> valueClass;
            if (classServiceInfo != null && classServiceInfo.annotation != null) {
                valueClass = classServiceInfo.annotation.genericClassB();
            } else if (classFieldInfo != null && classFieldInfo.annotation != null) {
                valueClass = classFieldInfo.annotation.genericClassB();
            } else {
                valueClass = Object.class;
            }
            
            Object valueSchema = getSchemaObject(level, valueClass, null, null);
            schema.put("items", valueSchema);

            return schema;

        } else {
            // "object" types
            allowedInstanceInfos = Reflection.getAllowedInstances(klass);

            // look for allowed instances
            List<Map<String, Object>> allowedSchemas = null;
            if (allowedInstanceInfos != null && allowedInstanceInfos.length > 0) {
                allowedSchemas = new ArrayList<Map<String, Object>>();
                for (AllowedInstanceInfo info : allowedInstanceInfos) {
                    // get the schema object with 'null' as service and value info to prevent
                    // duplication

                    Class<?> subClass = info.clazz;

                    Map<String, Object> subClassSchema = getSchemaObject(level, subClass, null, null);

                    // give it its title
                    subClassSchema.put("title", info.title);

                    allowedSchemas.add(subClassSchema);
                }
            }

            if (allowedSchemas == null)
                updateSchema("object", schema, classFieldInfo, classServiceInfo);
            else
                updateSchema(allowedSchemas, schema, classFieldInfo, classServiceInfo);

            // get the 'values' or JSON 'properties'
            Map<String, Object> propertiesSchema = new LinkedHashMap<String, Object>();

            ValueInfo[] valueInfos = Reflection.getValueInfos(klass);
            for (ValueInfo valueInfo : valueInfos) {
                try {
                    String name = valueInfo.annotation.name();
                    if (name == null || name.equals(""))
                        name = valueInfo.member.getName();

                    Member member = valueInfo.member;

                    Class<?> memberClass = null;

                    if (member instanceof Field) {
                        Field field = (Field) member;
                        memberClass = field.getType();
                    } else if (member instanceof Method) {
                        Method method = (Method) member;
                        memberClass = method.getReturnType();
                    } else {
                        // neither a field nor method, just continue
                        continue;
                    }

                    // retrieve the field value
                    Object propSchema = getSchemaObject(level, memberClass, valueInfo, null);
                    propertiesSchema.put(name, propSchema);
                } catch (Exception ignore) {
                    // ignore
                }
            } // (for)
            
            if (propertiesSchema.size() > 0)
                schema.put("properties", propertiesSchema);
            
            // if there have been no properties ('valueInfos') detected,
            // check if it can be constructed using a string
            if (valueInfos.length == 0 && Reflection.canCreateFromString(klass)) {
                // overwrite type 'object' to type 'string'
                updateSchema("string", schema, classFieldInfo, classServiceInfo);
                
                // specific the "format" if it hasn't been specified
                if (!schema.containsKey("format"))
                    schema.put("format", klass.getSimpleName());
            }

            Map<String, Object> servicesSchema = new LinkedHashMap<String, Object>();

            // get the 'services'
            ServiceInfo[] serviceInfos = Reflection.getServiceInfos(klass);
            for (ServiceInfo serviceInfo : serviceInfos) {
                try {
                    String name = serviceInfo.name;
                    
                    if (name == null || name.equals(""))
                        name = serviceInfo.member.getName();

                    Member member = serviceInfo.member;

                    Class<?> serviceSchemaType;

                    if (member instanceof Field) {
                        // get the type of the field
                        Field field = (Field) member;
                        serviceSchemaType = field.getType();
                        Object serviceSchema = getSchemaObject(level, serviceSchemaType, null, serviceInfo);
                        servicesSchema.put(name, serviceSchema);

                    } else if (member instanceof Method) {
                        // get the return type of the method
                        Method method = (Method) member;
                        serviceSchemaType = method.getReturnType();
                        if (serviceSchemaType == void.class) {
                            // it'll be boolean for void methods ('true' always)
                            serviceSchemaType = boolean.class;
                        }

                        Object serviceSchema = getSchemaObject(level, serviceSchemaType, null, serviceInfo);
                        servicesSchema.put(name, serviceSchema);

                    } else {
                        // (should either be field or method)
                        continue;
                    }

                } catch (Exception ignore) {
                    throw new RuntimeException(ignore);
                }
            } // (for)
            
            // only add if the services schema has something
            if (servicesSchema.size() > 0)
                schema.put("services", servicesSchema);
            
            return schema;
        } // (if)

    } // (static method)

    private static void updateSchema(String type, Map<String, Object> schema, ValueInfo classFieldInfo, ServiceInfo serviceInfo) {
        updateSchema(type, null, schema, classFieldInfo, serviceInfo);
    }

    private static void updateSchema(List<Map<String, Object>> allowed, Map<String, Object> schema, ValueInfo classFieldInfo, ServiceInfo serviceInfo) {
        updateSchema(null, allowed, schema, classFieldInfo, serviceInfo);
    }

    private static void updateSchema(String type, List<Map<String, Object>> allowed, Map<String, Object> schema, ValueInfo classFieldInfo, ServiceInfo serviceInfo) {
        if (type != null) {
            schema.put("type", type);

        } else if (allowed != null) {
            schema.put("type", allowed);
            
        }
            
        if (classFieldInfo != null) {
            Value value = classFieldInfo.annotation;

            schema.put("required", value.required());
            
            if (value.order() > 0)
            	schema.put("order",  value.order());

            String title = value.title();
            if (title != null && !title.equals(""))
                schema.put("title", title);

            String[] suggestions = value.suggestions();
            if (suggestions != null && suggestions.length > 0)
                schema.put("enum", suggestions);

            String desc = value.desc();
            if (!Strings.isNullOrEmpty(desc))
                schema.put("description", desc);
            
            String format = value.format();
            if (!Strings.isNullOrEmpty(format))
                schema.put("format", format);
            
            int minItems = value.minItems();
            if (minItems >= 0)
                schema.put("minItems", minItems);
            
            int maxItems = value.maxItems();
            if (maxItems >= 0)
                schema.put("maxItems", maxItems);            

        } else if (serviceInfo != null) {
            Service service = serviceInfo.annotation;
            
            if (service.order() > 0)
            	schema.put("order",  service.order());

            String title = service.title();
            if (title != null && !title.equals(""))
                schema.put("title", title);
            
            String desc = service.desc();
            if (!Strings.isNullOrEmpty(desc))
                schema.put("description", desc);

            if (serviceInfo.member instanceof Method) {
                // for the 'takes' (parameters) section if 1 or more parameters
                int paramCount = serviceInfo.parameterMap.size();
                if (paramCount > 0) {
                    LinkedHashMap<String, Object> paramSchemas = new LinkedHashMap<String, Object>();
                    int counter = 0;
                    for (Entry<String, ParameterInfo> entry : serviceInfo.parameterMap.entrySet()) {
                        ParameterInfo paramInfo = entry.getValue();

						Map<String, Object> paramSchema = getSchemaObject(0, paramInfo.klass, null, null);

						if (paramInfo.annotation != null) {
							if (!Strings.isNullOrEmpty(paramInfo.annotation.title()))
								paramSchema.put("title", paramInfo.annotation.title());
							if (!Strings.isNullOrEmpty(paramInfo.annotation.desc()))
								paramSchema.put("description", paramInfo.annotation.desc());
						}

						paramSchema.put("order", counter);
						counter++;
						paramSchemas.put(paramInfo.name, paramSchema);
					} // (for)
                    
                    schema.put("takes", paramSchemas);
                }
            }
        }
    } // (method)

} // (class)
