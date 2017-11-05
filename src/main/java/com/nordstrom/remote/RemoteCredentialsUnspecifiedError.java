package com.nordstrom.remote;

public class RemoteCredentialsUnspecifiedError extends Error {

    private static final long serialVersionUID = 2233125767260432607L;
    
    public RemoteCredentialsUnspecifiedError() {
        super("Neither password nor private key were specified");
    }

}
