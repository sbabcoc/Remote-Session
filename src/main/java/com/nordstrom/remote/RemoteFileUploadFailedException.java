package com.nordstrom.remote;

/**
 * This exception is thrown when a remote file upload fails.
 */
public class RemoteFileUploadFailedException extends RuntimeException {

    private static final long serialVersionUID = 864769035299350489L;
    
    /**
     * Constructor for a new "remote file upload failed" exception with
     * the specified message.
     * 
     * @param  message the detail message (which is saved for later retrieval
     *         by the {@link #getMessage()} method).
     */
    public RemoteFileUploadFailedException(String message) {
        super(message);
    }
    
    /**
     * Constructor for a new "remote file upload failed" exception with
     * the specified cause.
     * 
     * @param  cause the cause (which is saved for later retrieval by the
     *         {@link #getCause()} method).  (A {@code null} value is
     *         permitted, and indicates that the cause is nonexistent or
     *         unknown.)
     */
    public RemoteFileUploadFailedException(Throwable cause) {
        super(cause);
    }
    
    /**
     * Constructor for a new "remote file upload failed" exception with
     * the specified message and cause.
     * 
     * @param  message the detail message (which is saved for later retrieval
     *         by the {@link #getMessage()} method).
     * @param  cause the cause (which is saved for later retrieval by the
     *         {@link #getCause()} method).  (A {@code null} value is
     *         permitted, and indicates that the cause is nonexistent or
     *         unknown.)
     */
    public RemoteFileUploadFailedException(String message, Throwable cause) {
        super(message, cause);
    }

}
