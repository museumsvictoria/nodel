package org.nodel.io;

import java.io.IOException;

/**
 * Runtime version of IO exception.
 */
public class UnexpectedIOException extends RuntimeException {

    private static final long serialVersionUID = -685861555660909086L;

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