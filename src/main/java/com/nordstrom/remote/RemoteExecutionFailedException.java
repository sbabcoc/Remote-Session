package com.nordstrom.remote;

import com.nordstrom.remote.SshUtils.SessionHolder;

public class RemoteExecutionFailedException extends RuntimeException {

	private static final long serialVersionUID = 4253410628869516498L;
	
	private final int exitStatus;
	private final String maskedUri;
	private final String taskOutput;
	
	public RemoteExecutionFailedException(SessionHolder<?> session, Throwable cause) {
	    this(session, null, cause);
	}
	
	public RemoteExecutionFailedException(SessionHolder<?> session, String output) {
	    this(session, output, null);
	}

	public RemoteExecutionFailedException(SessionHolder<?> session, String output, Throwable cause) {
		super(getMessage(session, output), cause);
		exitStatus = session.getExitStatus();
		maskedUri = session.getMaskedUri();
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
	
	private static String getMessage(SessionHolder<?> session, String output) {
	    String message = String.format("Exit status %s for %s", session.getExitStatus(), session.getMaskedUri());
	    if ((output == null) || output.isEmpty()) {
	        return message;
	    }
	    return message + " => check task output for details";
	}

}
