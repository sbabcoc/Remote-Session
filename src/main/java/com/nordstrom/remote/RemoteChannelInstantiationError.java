package com.nordstrom.remote;

import com.jcraft.jsch.JSchException;

public class RemoteChannelInstantiationError extends Error {

    private static final long serialVersionUID = -3799742998512527137L;

    public RemoteChannelInstantiationError(JSchException cause) {
        super(cause);
    }

    public RemoteChannelInstantiationError(String message, JSchException cause) {
        super(message, cause);
    }

}
