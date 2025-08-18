package com.nordstrom.remote;

import com.jcraft.jsch.JSchException;

/**
 * This exception is thrown when remote session instantiation fails.
 */
public class RemoteSessionInstantiationException extends RuntimeException {

    private static final long serialVersionUID = 1338642330237654343L;
    
    /**
     * Constructor for a new "remote session instantiation" exception with
     * the specified cause.
     * 
     * @param  cause the cause (which is saved for later retrieval by the
     *         {@link #getCause()} method).  (A {@code null} value is
     *         permitted, and indicates that the cause is nonexistent or
     *         unknown.)
     */
    public RemoteSessionInstantiationException(JSchException cause) {
        super(cause);
    }

    /**
     * Constructor for a new "remote session instantiation" exception with
     * the specified message and cause.
     * 
     * @param  message the detail message (which is saved for later retrieval
     *         by the {@link #getMessage()} method).
     * @param  cause the cause (which is saved for later retrieval by the
     *         {@link #getCause()} method).  (A {@code null} value is
     *         permitted, and indicates that the cause is nonexistent or
     *         unknown.)
     */
    public RemoteSessionInstantiationException(String message, JSchException cause) {
        super(message, cause);
    }

}
