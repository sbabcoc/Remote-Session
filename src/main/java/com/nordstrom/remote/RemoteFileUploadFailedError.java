package com.nordstrom.remote;

public class RemoteFileUploadFailedError extends Error {

    private static final long serialVersionUID = 864769035299350489L;
    
    public RemoteFileUploadFailedError(String message) {
        super(message);
    }
    
    public RemoteFileUploadFailedError(Throwable cause) {
        super(cause);
    }
    
    public RemoteFileUploadFailedError(String message, Throwable cause) {
        super(message, cause);
    }

}
