package com.nordstrom.remote;

import com.nordstrom.remote.SshUtils.SessionHolder;

/**
 * This exception is thrown when execution of a remote operation fails.
 */
public class RemoteExecutionFailedException extends RuntimeException {

    private static final long serialVersionUID = 4253410628869516498L;
    
    /** exit status of the remote task */
    private final int exitStatus;
    /** remote task URI (password redacted) */
    private final String maskedUri;
    /** output of the remote task */
    private final String taskOutput;
    
    /**
     * Constructor for a new "remote execution failed" exception with
     * the specified session holder and cause.
     * 
     * @param  session remote channel session holder
     * @param  cause the cause (which is saved for later retrieval by the
     *         {@link #getCause()} method).  (A {@code null} value is
     *         permitted, and indicates that the cause is nonexistent or
     *         unknown.)
     */
    public RemoteExecutionFailedException(SessionHolder<?> session, Throwable cause) {
        this(session, null, cause);
    }
    
    /**
     * Constructor for a new "remote execution failed" exception with
     * the specified session holder and task output.
     * 
     * @param  session remote channel session holder.
     * @param  output output from the remote task (which is saved for later retrieval by the
     *         {@link #getTaskOutput()} method).
     */
    public RemoteExecutionFailedException(SessionHolder<?> session, String output) {
        this(session, output, null);
    }

    /**
     * Constructor for a new "remote execution failed" exception with
     * the specified session holder, task output, and cause.
     * 
     * @param  session remote channel session holder.
     * @param  output output from the remote task (which is saved for later retrieval by the
     *         {@link #getTaskOutput()} method).
     * @param  cause the cause (which is saved for later retrieval by the
     *         {@link #getCause()} method).  (A {@code null} value is
     *         permitted, and indicates that the cause is nonexistent or
     *         unknown.)
     */
    public RemoteExecutionFailedException(SessionHolder<?> session, String output, Throwable cause) {
        super(getMessage(session, output), cause);
        exitStatus = session.getExitStatus();
        maskedUri = session.getMaskedUri();
        taskOutput = output;
    }

    /**
     * Get the exit status of the failed remote task.
     * 
     * @return exit status of the remote task
     */
    public int getExitStatus() {
        return exitStatus;
    }
    
    /**
     * Get the masked URI of the failed remote task.
     * <p>
     * <b>NOTE</b>: The password is redacted from the remote task URI.
     * 
     * @return remote task URI (password redacted)
     */
    public String getMaskedUri() {
        return maskedUri;
    }
    
    /**
     * Get the output of the failed remote task.
     * 
     * @return output of the remote task
     */
    public String getTaskOutput() {
        return taskOutput;
    }
    
    /**
     * Build a message for this "remote execution failed" exception from
     * the specified session holder and task output.
     * 
     * @param  session remote channel session holder.
     * @param  output output from the remote task.
     * @return "remote execution failed" exception message
     */
    private static String getMessage(SessionHolder<?> session, String output) {
        String message = String.format("Exit status %s for %s", session.getExitStatus(), session.getMaskedUri());
        if ((output == null) || output.isEmpty()) {
            return message;
        }
        return message + " => check task output for details";
    }

}
