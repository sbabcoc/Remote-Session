package com.nordstrom.remote;

import com.jcraft.jsch.JSchException;

public class RemoteSessionInstantiationException extends RuntimeException {

    private static final long serialVersionUID = 1338642330237654343L;
    
    public RemoteSessionInstantiationException(JSchException cause) {
        super(cause);
    }

    public RemoteSessionInstantiationException(String message, JSchException cause) {
        super(message, cause);
    }

}
