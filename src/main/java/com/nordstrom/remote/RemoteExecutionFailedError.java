package com.nordstrom.remote;

import com.jcraft.jsch.JSchException;

public class RemoteExecutionFailedError extends Error {

    private static final long serialVersionUID = -8912623362751983307L;
    
    public RemoteExecutionFailedError(String message) {
        super(message);
    }
    
    public RemoteExecutionFailedError(JSchException cause) {
        super(cause);
    }
    
    public RemoteExecutionFailedError(String message, JSchException cause) {
        super(message, cause);
    }

}
