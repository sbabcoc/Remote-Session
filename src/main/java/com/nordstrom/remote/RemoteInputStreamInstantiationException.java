package com.nordstrom.remote;

import java.io.IOException;

public class RemoteInputStreamInstantiationException extends RuntimeException {

    private static final long serialVersionUID = -6263463958481342873L;
    
    public RemoteInputStreamInstantiationException(String message) {
        super(message);
    }

    public RemoteInputStreamInstantiationException(IOException cause) {
        super(cause);
    }

    public RemoteInputStreamInstantiationException(String message, IOException cause) {
        super(message, cause);
    }

}
