package com.nordstrom.remote;

public class RemoteFileDownloadFailedException extends RuntimeException {

    private static final long serialVersionUID = 1314301469162503715L;

    public RemoteFileDownloadFailedException(String message) {
        super(message);
    }
    
    public RemoteFileDownloadFailedException(Throwable cause) {
        super(cause);
    }
    
    public RemoteFileDownloadFailedException(String message, Throwable cause) {
        super(message, cause);
    }

}
