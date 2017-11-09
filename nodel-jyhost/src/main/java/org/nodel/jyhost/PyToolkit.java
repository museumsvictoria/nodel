package org.nodel.jyhost;

import org.python.core.Py;
import org.python.core.PyDictionary;
import org.python.core.PyObject;

/**
 * This class is reserved for any (Jy)Python-Java interfacing.
 */
public class PyToolkit {
    
    private static class FrozenDict extends PyDictionary {
        
        private static final long serialVersionUID = 1L;

        @Override
        public void __setitem__(int key, PyObject value) {
            throw Py.TypeError(String.format("object does not support item assignment", getType().fastGetName()));
        }
        
        @Override
        public void __setitem__(PyObject key, PyObject value) {
            throw Py.TypeError(String.format("object does not support item assignment", getType().fastGetName()));
        }
        
        @Override
        public void __setitem__(String key, PyObject value) {
            throw Py.TypeError(String.format("object does not support item assignment", getType().fastGetName()));
        }
        
        @Override
        public void __delitem__(PyObject key) {
            throw Py.TypeError(String.format("object has no items", getType().fastGetName()));
        }
    }
    
    /**
     * A convenient immutable constant for sharing.
     */
    public final static FrozenDict EmptyDict = new FrozenDict();
    
}
