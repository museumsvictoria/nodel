package org.nodel.io;

/**
 * A simple byte-buffer builder (think StringBuilder)
 * (not thread-safe)
 */
public class BufferBuilder {
    
    /**
     * The buffer itself (gets replaced as growth is required) 
     */
    private byte[] _buffer;
    
    /**
     * The current size of the builder.
     */
    private int _size = 0;
    
    /**
     * Constructs a new byte builder with a given initial capacity.
     */
    public BufferBuilder(int initialCapacity) {
        if (initialCapacity <= 0)
            throw new IllegalArgumentException("Initial capacity must be positive.");
        
        _buffer = new byte[initialCapacity];
    }

    /**
     * Appends a byte.
     */
    public BufferBuilder append(byte b) {
        ensureSize(_size + 1);

        _buffer[_size] = b;

        _size++;
        
        return this;
    }
    
    /**
     * Appends a byte (providing an 'int')
     * (convenience function for streams)
     */
    public BufferBuilder append(int b) {
        ensureSize(_size + 1);

        _buffer[_size] = (byte) b;

        _size++;

        return this;
    }
    
    /**
     * Appends an entire buffer.
     */
    public BufferBuilder append(byte[] buffer, int offset, int len) {
        ensureSize(_size + len);
        
        System.arraycopy(buffer, offset, _buffer, _size, len);
        
        _size += len;
        
        return this;
    }
    
    /**
     * Ensures the underlying buffer if large enough.
     */
    private void ensureSize(int size) {
        if (size > _buffer.length) {
            // buffer is full, double in size until it's big enough
            int newSize = _buffer.length * 2;
            while (newSize <= size)
                newSize *= 2;
            
            byte[] newBuffer = new byte[newSize];

            System.arraycopy(_buffer, 0, newBuffer, 0, _buffer.length);

            _buffer = newBuffer;
        }
    }
    
    /**
     * The current capacity of the buffer before any enlargement will be required. 
     */
    public int getCapacity() {
        return _buffer.length;
    }
    
    /**
     * Resets the size of the buffer (does not actually release any memory)
     */
    public BufferBuilder reset() {
        _size = 0;
        
        return this;
    }
    
    /**
     * The current size of the buffer.
     */
    public int getSize() {
        return _size;
    }
    
    /**
     * Returns the buffer itself.
     */
    public byte[] getBuffer() {
        return _buffer;
    }
    
    /**
     * Returns a UTF8 encoded trimmed string or null if its empty
     */
    public String getTrimmedString() {
        int end = _size;
        if (end > 0) {
            // trim
            int offset = 0;
            while (offset < end && _buffer[offset] <= ' ')
                offset++;

            while (offset < end && _buffer[end - 1] <= ' ')
                end--;

            // make sure there still something after the
            if (offset < end) {
                // String trimmed = new String(_buffer, offset, end - offset, UTF8Charset.instance());
                String trimmed = bufferToString(_buffer, offset, end-offset);
                return trimmed;
            }
        }
        
        return null;
    }

    /**
     * Non-decoded binary buffer as String.
     */
    public String getRawString() {
        char[] cBuffer = new char[_size];

        for (int a = 0; a < _size; a++)
            cBuffer[a] = (char) (_buffer[a] & 0xff);

        return new String(cBuffer);
    }

    private static String bufferToString(byte[] buffer, int offset, int len) {
        StringBuilder sb = new StringBuilder();
        for (int a = 0; a < len; a++)
            sb.append((char) (buffer[offset + a] & 0xff));

        return sb.toString();
    }

}
