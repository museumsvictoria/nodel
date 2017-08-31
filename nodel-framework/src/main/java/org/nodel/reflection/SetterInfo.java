package org.nodel.reflection;

import java.lang.reflect.Method;

public class SetterInfo {
    
    public String name;
    
    public Method method;

    public SetterInfo(String name, Method method) {
        this.name = name;
        this.method = method;
    }

}
