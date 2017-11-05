package com.nordstrom.remote;

public class RemoteFileDownloadFailedError extends Error {

    private static final long serialVersionUID = 1314301469162503715L;

    public RemoteFileDownloadFailedError(String message) {
        super(message);
    }
    
    public RemoteFileDownloadFailedError(Throwable cause) {
        super(cause);
    }
    
    public RemoteFileDownloadFailedError(String message, Throwable cause) {
        super(message, cause);
    }

}
