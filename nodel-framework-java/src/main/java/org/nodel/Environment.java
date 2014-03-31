package org.nodel;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.lang.reflect.Method;
import java.util.List;

/**
 * Some utility methods related to the Java environment.
 */
public class Environment {

    /**
     * Gets any special VM args that are used on every platform. We're looking to do this, but not all platforms 
     * support it so reflection is required:
     * 
     * java.lang.management.RuntimeMXBean runtimeMxBean = java.lang.management.ManagementFactory.getRuntimeMXBean();
     * List<String> arguments = runtimeMxBean.getInputArguments();
     * 
     * @return
     */
    public static List<String> getVMArgs() {
        try {
            Class<?> factoryClass = Class.forName("java.lang.management.ManagementFactory");

            Method getRuntimeMXBeanMethod = factoryClass.getMethod("getRuntimeMXBean");

            Object runtimeMxBean = getRuntimeMXBeanMethod.invoke(null, new Object[] {});
            
            Class<?> runtimeMxBeanClass = runtimeMxBean.getClass();
            
            Method getInputArgumentsMethod = runtimeMxBeanClass.getMethod("getInputArguments");
            getInputArgumentsMethod.setAccessible(true);

            Object argObj = getInputArgumentsMethod.invoke(runtimeMxBean, new Object[] {});

            @SuppressWarnings("unchecked")
            List<String> args = (List<String>) argObj;

            return args;
        } catch (Exception exc) {
            return null;
        }
    }

} // (class)
