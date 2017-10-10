package org.nodel.net;

import java.lang.reflect.Constructor;

/**
 * The factory class for creating HTTP clients. Providers may be different depending on the platform / environment.
 * 
 * This is based on late binding methodology used by SLF4j to split API vs impl. when dealing with logger factories.
 */
public abstract class NodelHttpClientProvider {
    
    public final static String CLASS = "org.nodel.http.StaticNodelHttpClientProvider";
    
    public final static Object s_lock = new Object();
    
    public static NodelHttpClientProvider s_provider;
    
    /**
     * Dynamically instantiates the provider only once. 
     */
    public static NodelHttpClientProvider instance() {
        if (s_provider != null)
            return s_provider;
        
        synchronized (s_lock) {
            // check again while locked
            if (s_provider != null)
                return s_provider;
            
            try {
                // resolve class
                Class<?> clazz = Class.forName(CLASS);
                
                // resolve method
                Constructor<?> constructor = clazz.getConstructor();
                
                // invoke constructor
                s_provider = (NodelHttpClientProvider) constructor.newInstance();
                
                // return the instance
                return s_provider;
                
            } catch (Exception exc) {
                throw new RuntimeException("Could not create an HTTP client provider", exc);
            }
        }
    }
    
    /**
     * Creates a Nodel HTTP client.
     */
    public abstract NodelHTTPClient create();
    
}
