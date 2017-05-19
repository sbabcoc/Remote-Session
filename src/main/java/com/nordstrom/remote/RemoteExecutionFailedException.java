package com.nordstrom.remote;

public class RemoteExecutionFailedException extends IllegalStateException {

	private static final long serialVersionUID = 4253410628869516498L;
	
	private int exitStatus;
	private String maskedUri;
	private String taskOutput;

	public RemoteExecutionFailedException(String message, int status, String uri, String output) {
		super(message);
		exitStatus = status;
		maskedUri = uri;
		taskOutput = output;
	}

	public int getExitStatus() {
		return exitStatus;
	}
	
	public String getMaskedUri() {
		return maskedUri;
	}
	
	public String getTaskOutput() {
		return taskOutput;
	}

}
