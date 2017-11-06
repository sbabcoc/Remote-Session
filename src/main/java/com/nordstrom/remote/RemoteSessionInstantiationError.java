package com.nordstrom.remote;

import com.jcraft.jsch.JSchException;

public class RemoteSessionInstantiationError extends Error {

    private static final long serialVersionUID = 1338642330237654343L;
    
    public RemoteSessionInstantiationError(JSchException cause) {
        super(cause);
    }

    public RemoteSessionInstantiationError(String message, JSchException cause) {
        super(message, cause);
    }

}
