package com.nordstrom.remote;

public class RemoteExecutionFailedException extends IllegalStateException {

	private static final long serialVersionUID = 4253410628869516498L;
	
	private int exitStatus;
	private String maskedUri;
	private String commandOutput;

	public RemoteExecutionFailedException(String message, int status, String uri, String output) {
		super(message);
		exitStatus = status;
		maskedUri = uri;
		commandOutput = output;
	}

	public int getExitStatus() {
		return exitStatus;
	}
	
	public String getMaskedUri() {
		return maskedUri;
	}
	
	public String getCommandOutput() {
		return commandOutput;
	}

}
