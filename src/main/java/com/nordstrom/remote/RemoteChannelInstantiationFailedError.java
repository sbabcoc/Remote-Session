package com.nordstrom.remote;

import com.jcraft.jsch.JSchException;

public class RemoteChannelInstantiationFailedError extends Error {

    private static final long serialVersionUID = -3799742998512527137L;

    public RemoteChannelInstantiationFailedError(JSchException cause) {
        super(cause);
    }

    public RemoteChannelInstantiationFailedError(String message, JSchException cause) {
        super(message, cause);
    }

}
