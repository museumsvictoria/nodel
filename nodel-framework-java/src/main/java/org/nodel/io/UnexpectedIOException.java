package org.nodel.io;

import java.io.IOException;

/**
 * Runtime version of IO exception.
 * 
 * TODO: sort this class out
 */
public class UnexpectedIOException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public UnexpectedIOException(String message) {
        super(message);
    }

    public UnexpectedIOException(IOException cause) {
        super(cause);
    }

    public UnexpectedIOException(String message, IOException cause) {
        super(message, cause);
    }

} // (class)