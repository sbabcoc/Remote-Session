package com.nordstrom.remote;

import java.io.IOException;

public class RemoteInputStreamInstantiationError extends Error {

    private static final long serialVersionUID = -6263463958481342873L;
    
    public RemoteInputStreamInstantiationError(String message) {
        super(message);
    }

    public RemoteInputStreamInstantiationError(IOException cause) {
        super(cause);
    }

    public RemoteInputStreamInstantiationError(String message, IOException cause) {
        super(message, cause);
    }

}
