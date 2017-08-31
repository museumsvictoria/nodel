package org.nodel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class LockFreeList<T> {
    
    @SuppressWarnings("unchecked")
    private List<T> empty() {
        return (List<T>) Collections.emptyList();
    }
    
    /**
     * Snapshot of list able to grow safely without any locking.
     * (actual reference can never be null)
     */
    private AtomicReference<List<T>> _array = new AtomicReference<>(empty());

    /**
     * Adds an element.
     */
    public void add(T obj) {
        if (obj == null)
            throw new IllegalArgumentException("Element cannot be null");

        for (;;) {
            // grow array 'lock-lessly'
            List<T> current = _array.get();
            
            // make space for one more, copy original and new items.
            ArrayList<T> newValue = new ArrayList<>(current.size() + 1);
            newValue.addAll(current);
            newValue.add(obj);

            // swap in the new one if the original hasn't been changed
            if (_array.compareAndSet(current, newValue))
                break;

            // otherwise keep trying
        }
    }

    /**
     * Clears the list.
     */
    public void clear() {
        for (;;) {
            List<T> current = _array.get();
            
            if (_array.compareAndSet(current, empty()))
                break;
        }
    }
    
    /**
     * Returns a snap-shot of the items.
     */
    public List<T> items() {
        return _array.get();
    }

    /**
     * The size of list.
     */
    public int size() {
        return _array.get().size();
    }

}
