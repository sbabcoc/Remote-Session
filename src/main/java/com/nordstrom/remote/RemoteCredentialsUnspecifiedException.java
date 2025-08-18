package com.nordstrom.remote;

/**
 * This exception is thrown when no credentials are specified for a 'new channel' request.
 */
public class RemoteCredentialsUnspecifiedException extends RuntimeException {

    private static final long serialVersionUID = 2233125767260432607L;
    
    /**
     * Constructor for a new "remote credentials unspecified" exception.
     */
    public RemoteCredentialsUnspecifiedException() {
        super("Neither password nor private key were specified");
    }

}
