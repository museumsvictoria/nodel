package org.nodel.reflection;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.nodel.SimpleName;
import org.nodel.Strings;

/**
 * Utilities to assist with reflection.
 * 
 * This class is thread-safe and avoids using any locking. Map operations may overlap however,
 * the structure will still remain consistent.
 * 
 */
public class Reflection {
    
    /**
     * Stores 'allowed instance' info.
     */
    public static class AllowedInstanceInfo {
        
        public String title;
        
        public Class<?> clazz;
    }
    
    /**
     * (internal data structure)
     */
    private static class ReflectionInfo {
        
        private ValueInfo[] valueInfos;

        private Map<String, ValueInfo> valueInfoByName;

        private ServiceInfo[] serviceInfos;

        private Map<String, ServiceInfo> serviceInfoByName;
        
        private Constructor<?> constructorByString;

        private Map<SimpleName, EnumInfo> enumConstantMapByName;
        
        private Map<Object, EnumInfo> enumConstantMap;
        
        private EnumInfo[] enumInfos;
        
        private String[] enumValues;

        private Member defaultValue;

        /**
         * (can be null)
         */
        private AllowedInstanceInfo[] allowedInstances;

    } // (class)
    
    /**
     * Reflection info map by class.
     */
    private static Map<Class<?>, ReflectionInfo> reflectionInfoMap = new ConcurrentHashMap<Class<?>, ReflectionInfo>();
    
    /**
     * Holds an adapter class map. Keys are original classes, values are adapters classes. 
     */
    private static Map<Class<?>, Class<?>> adapterMap = new ConcurrentHashMap<Class<?>, Class<?>>();
    
    /**
     * Registers any 'adaptor' classes (method for opting in existing classes). Must be called
     * before any reflection-related operations are performed e.g. serialisation, etc.
     */
    public static void registerAdaptor(Class<?> clazz, Class<?> adaptorClass) {
        adapterMap.put(clazz, adaptorClass);
    }

    /**
     * Gets a list or ordered field information.
     */
    public static ValueInfo[] getValueInfos(Class<?> klass) {
        return tryInitReflectionData(klass).valueInfos;
    } // (class)

    /**
     * Gets a map of the field information.
     */
    public static ValueInfo getValueInfosByName(Class<?> klass, String name) {
        return tryInitReflectionData(klass).valueInfoByName.get(name.toLowerCase());
    }
    
    /**
     * Returns the value info map.
     */
    public static Map<String, ValueInfo> getValueInfoMap(Class<?> klass) {
        return tryInitReflectionData(klass).valueInfoByName;
    }

    /**
     * Gets a list or ordered service information.
     */
    public static ServiceInfo[] getServiceInfos(Class<?> klass) {
        return tryInitReflectionData(klass).serviceInfos;
    }

    /**
     * Gets a map of the field information
     */
    public static ServiceInfo getServiceInfosByName(Class<?> klass, String name) {
        return tryInitReflectionData(klass).serviceInfoByName.get(name.toLowerCase());
    } // (method)
    
    /**
     * Gets the constructor that takes a string as an argument.
     */
    public static Constructor<?> getConstructorByString(Class<?> klass) {
        return tryInitReflectionData(klass).constructorByString;
    } // (class)
    
    /**
     * Gets the member (field or arg-less method) that returns
     * its value.
     */
    public static Member getTreatAsValueMember(Class<?> klass) {
        return tryInitReflectionData(klass).defaultValue;
    }

    /**
     * Initialises the lookup tables if they haven't been initialised already.
     */
    private static ReflectionInfo tryInitReflectionData(Class<?> klazz) {
        ReflectionInfo reflectionInfo = reflectionInfoMap.get(klazz);
        if (reflectionInfo != null)
            return reflectionInfo;
        
        reflectionInfo = new ReflectionInfo();
        
        // the class to target
        Class<?> targetClass;
        
        // check if there are any adapters
        Class<?> adapterClass = adapterMap.get(klazz);
        if (adapterClass != null)
            targetClass = adapterClass;
        else
            targetClass = klazz;

        // the list
        List<ValueInfo> valueInfos = new ArrayList<ValueInfo>();
        List<ServiceInfo> serviceInfos = new ArrayList<ServiceInfo>();

        // the name lookup maps
        Map<String, ValueInfo> valueInfoMap = new HashMap<String, ValueInfo>();
        Map<String, ServiceInfo> serviceInfoMap = new HashMap<String, ServiceInfo>();

        // the working lists
        List<Field> allFields = new ArrayList<Field>();
        List<Method> allMethods = new ArrayList<Method>();
        
        // add declared members (excl. those from base, etc.)
        
        Field[] declaredFields = targetClass.getDeclaredFields(); 
        for(Field field : declaredFields)
            allFields.add(field);
        
        Method[] declaredMethods = targetClass.getDeclaredMethods();
        for(Method method: declaredMethods)
            allMethods.add(method);
        
        // traverse through all the base classes' fields
        Class<?> baseClass = targetClass.getSuperclass();
        while (baseClass != null) {
            for (Field field : baseClass.getDeclaredFields())
                allFields.add(field);

            for (Method method : baseClass.getDeclaredMethods())
                allMethods.add(method);

            baseClass = baseClass.getSuperclass();
        }

        // go through the discovered fields...
        for (Field field : allFields) {
            Field originalField = tryMatchField(field, klazz);

            tryMemberAsValue(reflectionInfo, valueInfos, valueInfoMap, field, originalField);

            tryMemberAsService(serviceInfos, serviceInfoMap, field, originalField);
        }

        // go through the discovered methods...
        for (Method method : allMethods) {
            Method originalMethod = tryMatchMethod(method, klazz);

            tryMemberAsService(serviceInfos, serviceInfoMap, method, originalMethod);
            
            tryMemberAsValue(reflectionInfo, valueInfos, valueInfoMap, method, originalMethod);
        } // (for)
        
        // find a constructor by string...
        Constructor<?> constructorByString = null;
        
        Constructor<?>[] constructors = targetClass.getConstructors();
        for (Constructor<?> constructor : constructors) {
            Class<?>[] types = constructor.getParameterTypes();
            if (types != null && types.length > 0 && types[0] == String.class) {
                constructorByString = constructor;
                break;
            }
        } // (for)
        
        // find the member that is used to define a default value
        Member defaultValueMember = null;
        
        for (ValueInfo value : valueInfos) {
            if (value.annotation.treatAsDefaultValue())
                defaultValueMember = value.member;
        } // (for)
        
        for (ServiceInfo service : serviceInfos) {
            if (service.annotation.treatAsDefaultValue())
                defaultValueMember = service.member;
        } // (for)
        
        // get the enum map if they're enum class
        Map<SimpleName, EnumInfo> enumConstantMapByName = null;
        Map<Object, EnumInfo> enumConstantMap = null;
        EnumInfo[] enumInfos = null;
        String[] enumValues = null;
        
        if (klazz.isEnum()) {
            List<String> enumValuesList = new ArrayList<String>();
            List<EnumInfo> enumInfosList = new ArrayList<EnumInfo>();
            
            enumConstantMapByName = new HashMap<SimpleName, EnumInfo>();
            enumConstantMap = new HashMap<Object, EnumInfo>();
            
            // look for 'title' and 'desc' fields
            Field titleLookup = null;
            Field descLookup = null;
            
            for(Field value : targetClass.getDeclaredFields()) {
                EnumTitle enumTitle = (EnumTitle) (value.getAnnotation(EnumTitle.class));
                if (enumTitle != null) {
                    titleLookup = value;
                    continue;
                }
                
                EnumDesc enumDesc = (EnumDesc) (value.getAnnotation(EnumDesc.class));
                if (enumDesc != null) {
                    descLookup = value;
                    continue;
                }
            }
            
            // populate the enumeration map for efficiency
            for (Object constant : klazz.getEnumConstants()) {
                EnumInfo enumInfo = new EnumInfo(constant);
                
                String title = null;
                String desc = null;
                
                if (titleLookup != null)
                    title = safeGetterAsString(titleLookup, constant);
                
                if (descLookup != null)
                    desc = safeGetterAsString(descLookup, constant);
                
                // overwrite the title if one has been provided
                if (!Strings.isNullOrEmpty(title))
                    enumInfo.title = title;
                
                // add the description if one has been provided
                if (!Strings.isNullOrEmpty(desc))
                    enumInfo.desc = desc;
                
                enumConstantMap.put(constant, enumInfo);
                // map by the constant name
                enumConstantMapByName.put(new SimpleName(constant.toString()), enumInfo);
                
                // also map by the title if it's present (would be different to constant) 
                if (!Strings.isNullOrEmpty(title))
                    enumConstantMapByName.put(new SimpleName(title), enumInfo);
                
                enumInfosList.add(enumInfo);
                enumValuesList.add(enumInfo.title);
            }
            
            enumValues = enumValuesList.toArray(new String[enumValuesList.size()]);
            enumInfos = enumInfosList.toArray(new EnumInfo[enumInfosList.size()]);
        }

        // look for declared allowed instances in the class annotations
        MultiClassValue classValueAnnotation = (MultiClassValue) klazz.getAnnotation(MultiClassValue.class);
        if (classValueAnnotation != null) {
            Class<?>[] classes = classValueAnnotation.allowedItemClasses();
            String[] titles = classValueAnnotation.allowedItemTitles();
            
            // make sure the array lengths are consistent
            if (classes != null && classes.length > 0 && titles != null && titles.length == classes.length) {
                
                AllowedInstanceInfo[] allowedInfos = new AllowedInstanceInfo[classes.length];
                
                // go through the arrays
                for (int a = 0; a < classes.length; a++) {
                    AllowedInstanceInfo info = new AllowedInstanceInfo();
                    info.title = titles[a];
                    info.clazz = classes[a];
                    
                    allowedInfos[a] = info;
                }
                
                reflectionInfo.allowedInstances = allowedInfos;
            }
        }

        // sort the lists
        Collections.sort(valueInfos);
        Collections.sort(serviceInfos);

        reflectionInfo.valueInfos = valueInfos.toArray(new ValueInfo[valueInfos.size()]);
        reflectionInfo.valueInfoByName = valueInfoMap;
        reflectionInfo.serviceInfos = serviceInfos.toArray(new ServiceInfo[serviceInfos.size()]);
        reflectionInfo.serviceInfoByName = serviceInfoMap;
        reflectionInfo.constructorByString = constructorByString;
        reflectionInfo.defaultValue = defaultValueMember;
        reflectionInfo.enumInfos = enumInfos;
        reflectionInfo.enumConstantMapByName = enumConstantMapByName;
        reflectionInfo.enumConstantMap = enumConstantMap;
        reflectionInfo.enumValues = enumValues;

        reflectionInfoMap.put(klazz, reflectionInfo);

        return reflectionInfo;
    } // (method)
    
    /**
     * Retrieves a field via reflection without throwing any exceptions, safely returning
     * null or .toString().
     */
    private static String safeGetterAsString(Field field, Object obj) {
        try {
            Object result = field.get(obj);
            return (result != null) ? result.toString() : null;
            
        } catch (Exception exc) {
            // ignore the lot
            return null;
        }
    }

    private static boolean tryMemberAsValue(ReflectionInfo reflectionInfo, List<ValueInfo> fieldInfos, Map<String, ValueInfo> fieldNameMap, Member member, Member actualMember) {
        Value valueAnnotation;
        if (member instanceof Field) {
            valueAnnotation = (Value) ((Field) member).getAnnotation(Value.class);
        } else {
            // must be a method
            valueAnnotation = (Value) ((Method) member).getAnnotation(Value.class);
        }

        // skip if doesn't have a Value annotation
        if (valueAnnotation == null)
            return false;

        ValueInfo fieldInfo = new ValueInfo();

        // fallback to the field/method name if the annotated name is left unspecified.
        fieldInfo.name = valueAnnotation.name();
        if (Strings.isNullOrEmpty(fieldInfo.name))
            fieldInfo.name = member.getName();

        
        fieldInfo.member = (actualMember == null ? member : actualMember);
        fieldInfo.annotation = valueAnnotation;

        fieldInfos.add(fieldInfo);

        fieldNameMap.put(fieldInfo.name.toLowerCase(), fieldInfo);
        
        return true;
    } // (method)

    private static boolean tryMemberAsService(List<ServiceInfo> serviceInfos, Map<String, ServiceInfo> serviceNameMap, Member member, Member actualMember) {
        // for later use
        Method method = null;
        Field field = null;

        Service serviceAnnotation;
        if (member instanceof Method) {
            method = (Method) (actualMember == null ? member : actualMember);
            serviceAnnotation = (Service) method.getAnnotation(Service.class);
        } else if (member instanceof Field) {
            field = (Field) (actualMember == null ? member : actualMember);
            serviceAnnotation = (Service) field.getAnnotation(Service.class);
        } else {
            return false;
        }

        // skip if doesn't have a Service annotation
        if (serviceAnnotation == null)
            return false;

        ServiceInfo serviceInfo;
        if (Strings.isNullOrEmpty(serviceAnnotation.name()))
            serviceInfo = new ServiceInfo(member.getName(), serviceAnnotation);
        else
            serviceInfo = new ServiceInfo(serviceAnnotation.name(), serviceAnnotation);

        serviceInfo.member = (actualMember == null ? member : actualMember);

        // (using LinkedHashMap to preserve order)
        Map<String, ParameterInfo> parameterMap = new LinkedHashMap<String, ParameterInfo>();

        // get parameter metadata
        if (method != null) {
            Class<?>[] paramClasses = method.getParameterTypes();
            Annotation[][] paramsAnnotations = method.getParameterAnnotations();

            for (int a = 0; a < paramClasses.length; a++) {
                Class<?> klass = paramClasses[a];
                Annotation[] paramAnnotations = paramsAnnotations[a];
                Param param = findParamAnnotation(paramAnnotations);

                ParameterInfo paramInfo;
                if (param == null)
                    paramInfo = new ParameterInfo("value", a, klass);
                else
                    paramInfo = new ParameterInfo(param.name(), a, klass, param);

                parameterMap.put(paramInfo.name.toLowerCase(), paramInfo);
            } // (for)

        } else if (field != null) {
            Class<?> paramClass = field.getClass();
            ParameterInfo paramInfo = new ParameterInfo("value", 0, paramClass);

            parameterMap.put(paramInfo.name.toLowerCase(), paramInfo);
        }

        serviceInfo.parameterMap = parameterMap;

        serviceInfos.add(serviceInfo);

        serviceNameMap.put(serviceInfo.name.toLowerCase(), serviceInfo);
        
        return true;
    } // (method)
    
    private static Param findParamAnnotation(Annotation[] annotations) {
        for (Annotation annotation : annotations) {
            if (annotation instanceof Param) {
                Param result = (Param) annotation;
                return result;
            }
        } // (for)

        return null;
    } // (method)
    
    /**
     * Determines whether can be created from a plain string.
     */
    public static boolean canCreateFromString(Class<?> klass) {
        Constructor<?> constructor = getConstructorByString(klass);
        if (constructor == null)
            return false;
        
        return true;
    } // (method)
    
    /**
     * Attempts to create an instance of a class from a given value. Returns null 
     * if a failure occurs.
     */
    public static Object createInstanceFromString(Class<?> klass, String value) {
        Constructor<?> constructor = getConstructorByString(klass);
        if (constructor == null)
            return null;
        
        try {
            Object result = constructor.newInstance(value);
            return result;
        } catch (IllegalArgumentException e) {
            return null;
        } catch (InstantiationException e) {
            return null;
        } catch (IllegalAccessException e) {
            return null;
        } catch (InvocationTargetException e) {
            return null;
        }
    } // (method)
    
    /**
     * Attempts to create a default instance of a class (one with no arguments). 
     * Returns null if a failure occurs.
     * @param <T>
     */
    public static Object createDefaultInstance(Class<?> klass) {
        try {
            Object result = klass.newInstance();
            
            return result;
        } catch (IllegalArgumentException e) {
            return null;
        } catch (InstantiationException e) {
            return null;
        } catch (IllegalAccessException e) {
            return null;
        }
    } // (method)    
    
    /**
     * Deals with 'treatAsDefaultValue' annotations.
     */
    public static Object getDefaultValue(Object obj) {
        Class<?> klass = obj.getClass();
        
        Member member = getTreatAsValueMember(klass);
        
        // nothing specified so return the object itself
        if (member == null)
            return obj;
        
        if (member instanceof Field) {
            try {
                return ((Field) member).get(obj);
            } catch (IllegalArgumentException e1) {
                return obj;
            } catch (IllegalAccessException e1) {
                return obj;
            }
        } else if (member instanceof Method) {
            try {
                return ((Method) member).invoke(obj);
            } catch (IllegalArgumentException e) {
                return obj;
            } catch (IllegalAccessException e) {
                return obj;
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e.getTargetException());
            }
        } else {
            // should never get here
            assert obj != null : "Is neither a Field or Method."; 
            return obj;
        }
    } // (method)
    
    /**
     * Does a shallow copy from one object to another.
     */
    public static void shallowCopy(Object src, Object dst) {
        Class<?> srcClass = src.getClass();
        Class<?> dstClass = dst.getClass();
        
        Exception refExc = null;
        
        ValueInfo[] dstValues = Reflection.getValueInfos(dstClass);
        for (ValueInfo dstValueInfo : dstValues) {
            if (!(dstValueInfo.member instanceof Field))
                continue;

            ValueInfo srcValueInfo = Reflection.getValueInfosByName(srcClass, dstValueInfo.name);
            if (srcValueInfo == null || !(srcValueInfo.member instanceof Field))
                continue;

            try {
                Field dstField = (Field) dstValueInfo.member;
                Field srcField = (Field) srcValueInfo.member;

                Object srcValue = srcField.get(src);

                dstField.set(dst, srcValue);
            } catch (IllegalArgumentException exc) {
                refExc = exc;
            } catch (IllegalAccessException exc) {
                refExc = exc;
            }

            if (refExc != null)
                throw new ReflectionException("Could not complete shallow copy.", refExc);
        } // (for)
        
    } // (method)
    
    /**
     * For use with adaptors. Returns the adaptor's field if it matches
     * otherwise the original field.
     */
    private static Field tryMatchField(Field original, Class<?> klass) {
        try {
            Field match = klass.getField(original.getName());
            
            if (match == null)
                return original;
            
            if (!match.getDeclaringClass().equals(original.getDeclaringClass()))
                return original;
            
            return match;
            
        } catch (Exception exc) {
            return original;
        }
    } // (method)
    
    /**
     * For use with adaptors. Returns the adaptor's method if it matches
     * otherwise the original method.
     */
    private static Method tryMatchMethod(Method original, Class<?> klass) {
        try {
            Method match = klass.getMethod(original.getName());
            
            if (match == null)
                return null;
           
            // check the arg and return types
            
            if (!Arrays.equals(original.getParameterTypes(), match.getParameterTypes()))
                return original;
            
            if (!original.getReturnType().equals(match.getReturnType()))
                return original;
            
            return match;

        } catch (Exception exc) {
            return original;
        }
    } // (method)

    /**
     * Gets an enum constant using loose equality rules (see 'NodelName')
     */
    public static EnumInfo getEnumConstantInfo(Class<?> clazz, String value) {
        ReflectionInfo reflectionInfo = tryInitReflectionData(clazz);

        if (reflectionInfo == null || reflectionInfo.enumConstantMapByName == null)
            return null;

        return reflectionInfo.enumConstantMapByName.get(new SimpleName(value));
    } // (method)
    
    /**
     * Gets an enum info given the enum constant itself.
     */
    public static EnumInfo getEnumConstantInfo(Class<?> clazz, Object enumConstant) {
        ReflectionInfo reflectionInfo = tryInitReflectionData(clazz);

        if (reflectionInfo == null || reflectionInfo.enumConstantMapByName == null)
            return null;

        return reflectionInfo.enumConstantMap.get(enumConstant);
    } // (method)    
    
    /**
     * Gets full enum info.
     */
    public static EnumInfo[] getEnumInfos(Class<?> clazz) {
        ReflectionInfo reflectionInfo = tryInitReflectionData(clazz);

        if (reflectionInfo == null || reflectionInfo.enumInfos == null)
            return null;

        return reflectionInfo.enumInfos;
    } // (method)    
    
    /**
     * Gets the enum values (as String array for convenience)
     */
    public static String[] getEnumValues(Class<?> clazz) {
        ReflectionInfo reflectionInfo = tryInitReflectionData(clazz);

        if (reflectionInfo == null || reflectionInfo.enumValues == null)
            return null;
        
        return reflectionInfo.enumValues;
    }

    /**
     * Returns the list of allowed sub-classes for a given class.
     */
    public static AllowedInstanceInfo[] getAllowedInstances(Class<?> clazz) {
        ReflectionInfo reflectionInfo = tryInitReflectionData(clazz);

        if (reflectionInfo == null || reflectionInfo.allowedInstances == null)
            return null;

        return reflectionInfo.allowedInstances;
    }

} // (class)
