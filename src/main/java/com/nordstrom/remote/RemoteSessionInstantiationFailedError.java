package com.nordstrom.remote;

import com.jcraft.jsch.JSchException;

public class RemoteSessionInstantiationFailedError extends Error {

    private static final long serialVersionUID = 1338642330237654343L;
    
    public RemoteSessionInstantiationFailedError(JSchException cause) {
        super(cause);
    }

    public RemoteSessionInstantiationFailedError(String message, JSchException cause) {
        super(message, cause);
    }

}
