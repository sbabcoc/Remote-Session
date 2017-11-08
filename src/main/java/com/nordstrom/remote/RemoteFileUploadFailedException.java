package com.nordstrom.remote;

public class RemoteFileUploadFailedException extends RuntimeException {

    private static final long serialVersionUID = 864769035299350489L;
    
    public RemoteFileUploadFailedException(String message) {
        super(message);
    }
    
    public RemoteFileUploadFailedException(Throwable cause) {
        super(cause);
    }
    
    public RemoteFileUploadFailedException(String message, Throwable cause) {
        super(message, cause);
    }

}
