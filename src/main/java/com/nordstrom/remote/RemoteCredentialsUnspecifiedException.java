package com.nordstrom.remote;

public class RemoteCredentialsUnspecifiedException extends RuntimeException {

    private static final long serialVersionUID = 2233125767260432607L;
    
    public RemoteCredentialsUnspecifiedException() {
        super("Neither password nor private key were specified");
    }

}
