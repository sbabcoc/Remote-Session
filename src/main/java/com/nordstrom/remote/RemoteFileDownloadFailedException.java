package com.nordstrom.remote;

/**
 * This exception is thrown when a remote file download fails.
 */
public class RemoteFileDownloadFailedException extends RuntimeException {

    private static final long serialVersionUID = 1314301469162503715L;

    /**
     * Constructor for a new "remote file download failed" exception with
     * the specified message.
     * 
     * @param  message the detail message (which is saved for later retrieval
     *         by the {@link #getMessage()} method).
     */
    public RemoteFileDownloadFailedException(String message) {
        super(message);
    }
    
    /**
     * Constructor for a new "remote file download failed" exception with
     * the specified cause.
     * 
     * @param  cause the cause (which is saved for later retrieval by the
     *         {@link #getCause()} method).  (A {@code null} value is
     *         permitted, and indicates that the cause is nonexistent or
     *         unknown.)
     */
    public RemoteFileDownloadFailedException(Throwable cause) {
        super(cause);
    }
    
    /**
     * Constructor for a new "remote file download failed" exception with
     * the specified message and cause.
     * 
     * @param  message the detail message (which is saved for later retrieval
     *         by the {@link #getMessage()} method).
     * @param  cause the cause (which is saved for later retrieval by the
     *         {@link #getCause()} method).  (A {@code null} value is
     *         permitted, and indicates that the cause is nonexistent or
     *         unknown.)
     */
    public RemoteFileDownloadFailedException(String message, Throwable cause) {
        super(message, cause);
    }

}
