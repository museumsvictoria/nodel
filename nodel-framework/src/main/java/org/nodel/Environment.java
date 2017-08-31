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
     * for:
     * ManagementFactory.getRuntimeMXBean()
     */
    private Object _runtimeMxBean;    
    
    /**
     * for:
     * List<String> arguments = runtimeMxBean.getInputArguments();
     */
    private Method _getInputArgumentsMethod;
    
    /**
     * for:
     * string name = ManagementFactory.getRuntimeMXBean().getName() 
     */
    private Method _getNameMethod;
    
    /**
     * Ensures all the late binding only occurs once.
     */
    private Environment() {
        try {
            Class<?> factoryClass = Class.forName("java.lang.management.ManagementFactory");
            Method getRuntimeMXBeanMethod = factoryClass.getMethod("getRuntimeMXBean");
            
            _runtimeMxBean = getRuntimeMXBeanMethod.invoke(null, new Object[] {});
            
            Class<? extends Object> runtimeMxBeanClass = _runtimeMxBean.getClass();

            _getInputArgumentsMethod = runtimeMxBeanClass.getMethod("getInputArguments");
            _getInputArgumentsMethod.setAccessible(true);
            
            _getNameMethod = runtimeMxBeanClass.getMethod("getName");
            _getNameMethod.setAccessible(true);
            
        } catch (Exception exc) {
            // ignore
        }
    }
    
    /**
     * (singleton)
     */
    private static class LazyHolder {
        private static final Environment INSTANCE = new Environment();
    }

    /**
     * Shared, singleton instance.
     */
    public static Environment instance() {
        return LazyHolder.INSTANCE;
    }
    

    /**
     * Gets any special VM args that are used on every platform. We're looking to do this, but not all platforms 
     * support it so reflection is required:
     * 
     * java.lang.management.RuntimeMXBean runtimeMxBean = java.lang.management.ManagementFactory.getRuntimeMXBean();
     * List<String> arguments = runtimeMxBean.getInputArguments();
     */
    public List<String> getVMArgs() {
        try {
            if (_getInputArgumentsMethod == null)
                return null;
            
            Object argObj = _getInputArgumentsMethod.invoke(_runtimeMxBean, new Object[] {});

            @SuppressWarnings("unchecked")
            List<String> result = (List<String>) argObj;

            return result;
            
        } catch (Exception exc) {
            return null;
        }
    }

    /**
     * In Java 7 and 8 there is no elegant way to get the current PID. This has to be done:
     * 
     * int result = Integer.parseInt(ManagementFactory.getRuntimeMXBean().getName().split("@")[0]);
     */
    public int getPID() {
        try {
            if (_getNameMethod == null)
                return 0;

            String name = (String) _getNameMethod.invoke(_runtimeMxBean, new Object[] {});

            int result = Integer.parseInt(name.split("@")[0]);

            return result;
            
        } catch (Exception exc) {
            return 0;
        }
    }

} // (class)
