package com.nordstrom.remote;

public class RemoteChannelClosedException extends InterruptedException {

    private static final long serialVersionUID = 2855054582776469083L;
    
    public RemoteChannelClosedException(String message) {
        super(message);
    }
}
