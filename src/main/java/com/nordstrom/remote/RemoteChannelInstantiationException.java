package com.nordstrom.remote;

import com.jcraft.jsch.JSchException;

public class RemoteChannelInstantiationException extends RuntimeException {

    private static final long serialVersionUID = -3799742998512527137L;

    public RemoteChannelInstantiationException(JSchException cause) {
        super(cause);
    }

    public RemoteChannelInstantiationException(String message, JSchException cause) {
        super(message, cause);
    }

}
