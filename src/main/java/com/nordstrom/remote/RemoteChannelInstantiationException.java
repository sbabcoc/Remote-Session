package com.nordstrom.remote;

import com.jcraft.jsch.JSchException;

/**
 * This exception is thrown when an attempt to open a new channel fails.
 */
public class RemoteChannelInstantiationException extends RuntimeException {

    private static final long serialVersionUID = -3799742998512527137L;

    /**
     * Constructor for a new "remote channel instantiation" exception with
     * the specified cause.
     * 
     * @param  cause the cause (which is saved for later retrieval by the
     *         {@link #getCause()} method).  (A {@code null} value is
     *         permitted, and indicates that the cause is nonexistent or
     *         unknown.)
     */
    public RemoteChannelInstantiationException(JSchException cause) {
        super(cause);
    }

    /**
     * Constructor for a new "remote channel instantiation" exception with
     * the specified message and cause.
     * 
     * @param  message the detail message (which is saved for later retrieval
     *         by the {@link #getMessage()} method).
     * @param  cause the cause (which is saved for later retrieval by the
     *         {@link #getCause()} method).  (A {@code null} value is
     *         permitted, and indicates that the cause is nonexistent or
     *         unknown.)
     */
    public RemoteChannelInstantiationException(String message, JSchException cause) {
        super(message, cause);
    }

}
