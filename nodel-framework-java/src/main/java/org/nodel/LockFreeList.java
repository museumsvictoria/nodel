package org.nodel;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

public class LockFreeList<T> {
    
    private final static Object[] EMPTY = new Object[0];

    /**
     * Snapshot of list able to grow safely without any locking.
     * (reference can never be null)
     */
    private AtomicReference<Object[]> _array = new AtomicReference<>(EMPTY);

    /**
     * Adds an element.
     */
    public void add(T obj) {
        if (obj == null)
            throw new IllegalArgumentException("Element cannot be null");

        Object[] current = _array.get();

        // grow array 'lock-lessly'
        for (;;) {
            // make space for one more
            int currentSize = current.length;
            Object[] newValue = Arrays.copyOf(current, currentSize + 1);
            newValue[current.length] = obj;

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
        Object[] current = _array.get();

        for (;;) {
            if (_array.compareAndSet(current, EMPTY))
                break;
        }
    }
    
    /**
     * Returns a snap-shot of the items.
     */
    @SuppressWarnings("unchecked")
    public T[] items() {
        return (T[]) _array.get();
    }

    /**
     * The size of list.
     */
    public int size() {
        return _array.get().length;
    }

}
