package com.nordstrom.remote;

import java.io.IOException;

/**
 * This exception is thrown when remote input stream instantiation fails.
 */
public class RemoteInputStreamInstantiationException extends RuntimeException {

    private static final long serialVersionUID = -6263463958481342873L;
    
    /**
     * Constructor for a new "remote input stream instantiation" exception with
     * the specified message.
     * 
     * @param  message the detail message (which is saved for later retrieval
     *         by the {@link #getMessage()} method).
     */
    public RemoteInputStreamInstantiationException(String message) {
        super(message);
    }

    /**
     * Constructor for a new "remote input stream instantiation" exception with
     * the specified cause.
     * 
     * @param  cause the cause (which is saved for later retrieval by the
     *         {@link #getCause()} method).  (A {@code null} value is
     *         permitted, and indicates that the cause is nonexistent or
     *         unknown.)
     */
    public RemoteInputStreamInstantiationException(IOException cause) {
        super(cause);
    }

    /**
     * Constructor for a new "remote input stream instantiation" exception with
     * the specified message and cause.
     * 
     * @param  message the detail message (which is saved for later retrieval
     *         by the {@link #getMessage()} method).
     * @param  cause the cause (which is saved for later retrieval by the
     *         {@link #getCause()} method).  (A {@code null} value is
     *         permitted, and indicates that the cause is nonexistent or
     *         unknown.)
     */
    public RemoteInputStreamInstantiationException(String message, IOException cause) {
        super(message, cause);
    }

}
