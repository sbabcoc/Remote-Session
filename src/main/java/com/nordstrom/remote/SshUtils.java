package com.nordstrom.remote;

import static java.lang.Thread.sleep;
import static org.apache.commons.io.FilenameUtils.getFullPath;
import static org.apache.commons.io.FilenameUtils.getName;
import static org.apache.commons.lang3.StringUtils.trim;

import com.google.common.collect.ImmutableMap;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UIKeyboardInteractive;
import com.jcraft.jsch.UserInfo;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Properties;

/**
 * This class includes several methods for interacting with remote hosts via SSH through the {@link JSch} library. 
 * The implementation was copied verbatim from a post on 
 * <a href='http://stackoverflow.com/questions/2405885/run-a-command-over-ssh-with-jsch'>Stack Overflow</a>. 
 * JavaDoc has been added for completeness and comprehensibility.
 * 
 * <p>Usage:</p>
 * <pre><code>    String remoteCommandOutput = exec("ssh://user:pass@host/work/dir/path", "ls -t | head -n1");
 *    String remoteShellOutput = shell("ssh://user:pass@host/work/dir/path", "ls");
 *    shell("ssh://user:pass@host/work/dir/path", "ls", System.out);
 *    shell("ssh://user:pass@host", System.in, System.out);
 *    sftp("file:/C:/home/file.txt", "ssh://user:pass@host/home");
 *    sftp("ssh://user:pass@host/home/file.txt", "file:/C:/home");</code></pre>
 * 
 * @author <a href='http://stackoverflow.com/users/448078/mykhaylo-adamovych'>Mykhaylo Adamovych</a>
 *
 */
public final class SshUtils {

    private static final Logger LOG = LoggerFactory.getLogger(SshUtils.class);
    private static final String SSH = "ssh";
    private static final String FILE = "file";
    
    public enum ChannelType {
        SESSION("session"),
        SHELL("shell"),
        EXEC("exec"),
        X11("x11"),
        AGENT_FORWARDING("auth-agent@openssh.com"),
        DIRECT_TCPIP("direct-tcpip"),
        FORWARDED_TCPIP("forwarded-tcpip"),
        SFTP("sftp"),
        SUBSYSTEM("subsystem");
        
        private String name;
        
        ChannelType(String name) {
            this.name = name;
        }
    }

    private SshUtils() {
        throw new AssertionError("SshUtils is a static utility class that cannot be instantiated");
    }

    /**
     * Perform the specified SSH file transfer.
     * 
     * <pre><code>    sftp("file:/C:/home/file.txt", "ssh://user:pass@host/home");
     *    sftp("ssh://user:pass@host/home/file.txt", "file:/C:/home");</code></pre>
     * 
     * <p><b>NOTE</b>: The transferred file retains its original name. If specified, the name component of {@code toUrl} 
     * will be ignored.<br>
     * <b>NOTE</b>: As indicated by the examples, source and target URIs must refer to opposing locations:
     * {@code file} for local file system and {@code ssh} for remote file system.</p>
     * 
     * <p>For upload: <b>fromUri</b> = {@code file}; <b>toUri</b> = {@code ssh}<br>
     * For download: <b>fromUri</b> = {@code ssh}; <b>toUri</b> = {@code file}</p>
     * 
     * @param fromUri source file {@link URI} as a string
     * @param toUri target folder {@link URI} as a string
     */
    public static void sftp(String fromUri, String toUri) {
        URI from = URI.create(fromUri);
        URI to = URI.create(toUri);

        if (SSH.equals(to.getScheme()) && FILE.equals(from.getScheme())) {
            upload(from, to);
        } else if (SSH.equals(from.getScheme()) && FILE.equals(to.getScheme())) {
            download(from, to);
        } else {
            throw new IllegalArgumentException("Source and target URIs must refer to opposing locations");
        }
    }

    /**
     * Perform the specified SSH file upload.
     * 
     * @param from source local file URI ({@code file} protocol)
     * @param to target remote folder URI ({@code ssh} protocol)
     */
    private static void upload(URI from, URI to) {
        try (SessionHolder<ChannelSftp> session = new SessionHolder<>(ChannelType.SFTP, to);
                FileInputStream fis = new FileInputStream(new File(from))) {

            LOG.info("Uploading {} --> {}", from, session.getMaskedUri());
            ChannelSftp channel = session.getChannel();
            channel.connect();
            channel.cd(to.getPath());
            channel.put(fis, getName(from.getPath()));

        } catch (Exception e) {
            throw new RuntimeException("Cannot upload file", e);
        }
    }

    /**
     * Perform the specified SSH file download.
     * 
     * @param from source remote file URI ({@code ssh} protocol)
     * @param to target local folder URI ({@code file} protocol)
     */
    private static void download(URI from, URI to) {
        File out = new File(new File(to), getName(from.getPath()));
        try (SessionHolder<ChannelSftp> session = new SessionHolder<>(ChannelType.SFTP, from);
                OutputStream os = new FileOutputStream(out);
                BufferedOutputStream bos = new BufferedOutputStream(os)) {

            LOG.info("Downloading {} --> {}", session.getMaskedUri(), to);
            ChannelSftp channel = session.getChannel();
            channel.connect();
            channel.cd(getFullPath(from.getPath()));
            channel.get(getName(from.getPath()), bos);

        } catch (Exception e) {
            throw new RuntimeException("Cannot download file", e);
        }
    }

    /**
     * Open an SSH shell session with the specified input and output streams.
     * 
     * <pre><code>    shell("ssh://user:pass@host", System.in, System.out);</code></pre>
     * 
     * @param connectUri SSH connection URI
     * @param is input stream object
     * @param os output stream object
     */
    public static void shell(String connectUri, InputStream is, OutputStream os) {
        try (SessionHolder<ChannelShell> session = new SessionHolder<>(ChannelType.SHELL, URI.create(connectUri))) {
            shell(session, is, os);
        }
    }

    /**
     * Open an SSH shell session and execute the specified command.
     * 
     * <pre><code>    String remoteOutput = shell("ssh://user:pass@host/work/dir/path", "ls");</code></pre>
     * 
     * @param connectUri SSH connection URI
     * @param command shell command string
     * @return shell command output
     */
    public static String shell(String connectUri, String command) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            shell(connectUri, command, baos);
            return baos.toString();
        } catch (RuntimeException e) {
            LOG.warn(baos.toString());
            throw e;
        }
    }

    /**
     * Open an SSH shell session and execute the specified script, using the provided stream for output.
     * 
     * <pre><code>    shell("ssh://user:pass@host/work/dir/path", "ls", System.out);</code></pre>
     * 
     * @param connectUri SSH connection URI
     * @param script shell command string
     * @param out output stream object
     */
    public static void shell(String connectUri, String script, OutputStream out) {
        try (SessionHolder<ChannelShell> session = new SessionHolder<>(ChannelType.SHELL, URI.create(connectUri));
                PipedOutputStream pipe = new PipedOutputStream();
                PipedInputStream in = new PipedInputStream(pipe);
                PrintWriter pw = new PrintWriter(pipe)) {

            if (session.getWorkDir() != null) {
                pw.println("cd " + session.getWorkDir());
            }
            pw.println(script);
            pw.println("exit");
            pw.flush();

            shell(session, in, out);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Private worker method for SSH shell interactions.
     * 
     * @param session wrapped {@link ChannelShell} session object
     * @param is {@link InputStream} object
     * @param os {@link OutputStream} object
     */
    private static void shell(SessionHolder<ChannelShell> session, InputStream is, OutputStream os) {
        try {
            ChannelShell channel = session.getChannel();
            channel.setInputStream(is, true);
            channel.setOutputStream(os, true);

            LOG.info("Starting shell for " + session.getMaskedUri());
            session.execute();
            session.assertExitStatus("Check shell output for error details.");
        } catch (InterruptedException e) {
            // set the 'interrupted' flag
            Thread.currentThread().interrupt();
        } catch (JSchException e) {
            throw new RuntimeException("Cannot execute script", e);
        }
    }

    /**
     * Open an SSH remote execution channel and execute the specified command.
     * 
     * <pre><code>    System.out.println(exec("ssh://user:pass@host/work/dir/path", "ls -t | head -n1"));</code></pre>
     * 
     * @param connectUri SSH connection URI
     * @param command command to be executed
     * @return output from executed command
     */
    public static String exec(String connectUri, String command) {
        try (SessionHolder<ChannelExec> session = new SessionHolder<>(ChannelType.EXEC, URI.create(connectUri))) {
            String workDir = session.getWorkDir();
            if (workDir != null) command = "cd " + workDir + " && " + command;
            return exec(session, command);
        }
    }

    /**
     * Execute the specified command via the specified remote channel.
     * 
     * @param session wrapped {@link ChannelExec} session object
     * @param command command to be executed
     * @return output from executed command
     */
    public static String exec(SessionHolder<ChannelExec> session, String command) {
        try (PipedOutputStream errPipe = new PipedOutputStream();
                PipedInputStream errIs = new PipedInputStream(errPipe);
                InputStream is = session.getChannel().getInputStream()) {

            ChannelExec channel = session.getChannel();
            channel.setInputStream(null);
            channel.setErrStream(errPipe);
            channel.setCommand(command);

            LOG.info("Starting exec for " + session.getMaskedUri());
            session.execute();
            String output = IOUtils.toString(is, Charset.defaultCharset());
            session.assertExitStatus(IOUtils.toString(errIs, Charset.defaultCharset()));

            return trim(output);
        } catch (InterruptedException | JSchException | IOException e) {
            throw new RuntimeException("Cannot execute command", e);
        }
    }

    /**
     * This is a wrapper class for objects that extend the {@link Channel} class. 
     * @author <a href='http://stackoverflow.com/users/448078/mykhaylo-adamovych'>Mykhaylo Adamovych</a>
     * 
     * @param <C> channel type wrapped by this session holder
     */
    public static class SessionHolder<C extends Channel> implements Closeable {

        private static final Logger LOG = LoggerFactory.getLogger(SessionHolder.class);
        
        private static final int DEFAULT_CONNECT_TIMEOUT = 5000;
        private static final int DEFAULT_PORT = 22;
        private static final int TERMINAL_HEIGHT = 1000;
        private static final int TERMINAL_WIDTH = 1000;
        private static final int TERMINAL_WIDTH_IN_PIXELS = 1000;
        private static final int TERMINAL_HEIGHT_IN_PIXELS = 1000;
        private static final int DEFAULT_WAIT_TIMEOUT = 100;

        private static final int MAX_ITER_DISCONNECT = 600;
        private static final int WAIT_DISCONNECT = 100;

        private ChannelType channelType;
        private URI uri;
        private Session session;
        private C channel;

        /**
         * Constructor #1 for wrapped SSH channel object
         * 
         * @param channelType desired channel type
         * @param uri SSH connection URI
         */
        public SessionHolder(ChannelType channelType, URI uri) {
            this(channelType, uri, ImmutableMap.of("StrictHostKeyChecking", "no"));
        }

        /**
         * Constructor #2 for wrapped SSH channel object
         * 
         * @param channelType desired channel type
         * @param uri SSH connection URI
         * @param props SSH session properties
         */
        public SessionHolder(ChannelType channelType, URI uri, Map<String, String> props) {
            this.channelType = channelType;
            this.uri = uri;
            this.session = newSession(props);
            this.channel = newChannel(session);
        }

        /**
         * Create a new SSH session with the specified properties.
         * 
         * @param props session configuration properties
         * @return new SSH session object
         */
        private Session newSession(Map<String, String> props) {
            try {
                Properties config = new Properties();
                config.putAll(props);

                JSch jsch = new JSch();
                Session newSession = jsch.getSession(getUser(), uri.getHost(), getPort());
                newSession.setPassword(getPass());
                newSession.setUserInfo(new User(getUser(), getPass()));
                newSession.setDaemonThread(true);
                newSession.setConfig(config);
                newSession.connect(DEFAULT_CONNECT_TIMEOUT);
                return newSession;
            } catch (JSchException e) {
                throw new RuntimeException("Cannot create session for " + getMaskedUri(), e);
            }
        }

        /**
         * Creates a new channel over the specified SSH session
         * 
         * @param session a connection to an SSH server 
         * @return a new channel of the type specified for this {@link SessionHolder}, initialized, but not connected
         */
        @SuppressWarnings("unchecked")
        private C newChannel(Session session) {
            try {
                Channel newChannel = session.openChannel(channelType.name);
                if (channelType == ChannelType.SHELL) {
                    ((ChannelShell) newChannel).setPtyType("ANSI", TERMINAL_WIDTH, TERMINAL_HEIGHT,
                            TERMINAL_WIDTH_IN_PIXELS, TERMINAL_HEIGHT_IN_PIXELS);
                }
                return (C) newChannel;
            } catch (JSchException e) {
                throw new RuntimeException("Cannot create " + channelType + " channel for " + getMaskedUri(), e);
            }
        }

        /**
         * Verify that the remote task completed normally
         * 
         * @param taskOutput output from the remote task
         * @throws RemoteExecutionFailedException if exit status is non-zero
         */
        public void assertExitStatus(String taskOutput) {
            int exitStatus = channel.getExitStatus();
            if (exitStatus != 0) {
                String maskedUri = getMaskedUri();
                String message = String.format("Exit status %s for %s => check task output for details", exitStatus, maskedUri);
                throw new RemoteExecutionFailedException(message, exitStatus, maskedUri, taskOutput);
            }
        }

        /**
         * Opens the channel to the remote session, starts the configured task, and waits for end-of-file to be received.
         * 
         * @throws JSchException a timeout or other connection issue was detected
         * @throws InterruptedException any thread has interrupted the current thread
         */
        public void execute() throws JSchException, InterruptedException {
            channel.connect();
            channel.start();
            while (!channel.isEOF()) {
                sleep(DEFAULT_WAIT_TIMEOUT);
            }
        }

        /**
         * Get the SSH session for this {@link SessionHolder}
         * 
         * @return SSH session object
         */
        public Session getSession() {
            return session;
        }

        /**
         * Get the channel to the remote session created for this {@link SessionHolder}
         * 
         * @return SSH session channel object
         */
        public C getChannel() {
            return channel;
        }
        
        /**
         * Get a channel stream object for this session.
         * 
         * @return stream object for performing channel I/O
         */
        public ChannelStreams<C> getChannelStream() {
               return new ChannelStreams<C>(channel);
        }

        /**
         * Disconnect the channel and session created for this {@link SessionHolder}
         */
        @Override
        public void close() {
            if (channel != null) {
                channel.disconnect();
            }
            if (session != null) {
                session.disconnect();
            }
        }

        /**
         * Get the URI for this {@link SessionHolder} with password redacted
         * 
         * @return SSH connection URI without password
         */
        public String getMaskedUri() {
            return uri.toString().replaceFirst(":[^:]*?@", "@");
        }

        /**
         * Get the port of the URI for this {@link SessionHolder}
         * 
         * @return port component of the SSH connection URI
         */
        public int getPort() {
            return uri.getPort() < 0 ? DEFAULT_PORT : uri.getPort();
        }

        /**
         * Get the user specified in the URI for this {@link SessionHolder}
         * 
         * @return user specified in the SSH connection URI
         */
        public String getUser() {
            return uri.getUserInfo().split(":")[0];
        }

        /**
         * Get the password specified in the URI for this {@link SessionHolder}
         * 
         * @return password specified in the SSH connection URI
         */
        private String getPass() {
            return uri.getUserInfo().split(":")[1];
        }

        /**
         * Get the target directory specified in the URI for this {@link SessionHolder}
         * 
         * @return path component of the SSH connection URI
         */
        public String getWorkDir() {
            return uri.getPath();
        }
        
        /**
         * Get the channel exit status
         * 
         * @return channel exit status
         */
        public int getExitStatus() {
            return channel.getExitStatus();
        }

        /**
         * Disconnect channel and session.
         * 
         * @param waitClose 'true' to delay disconnect until the channel is closed; 'false' to disconnect immediately
         */
        public void disconnect(boolean waitClose) {
               if (waitClose) waitChannel();
               close();
        }

        /**
         * Wait for channel to close.<br>
         * <b>NOTE</b>: This method polls the channel 'closed' state a maximum of {@link #MAX_ITER_DISCONNECT} times,
         * delaying {@link #WAIT_DISCONNECT} milliseconds between each check.
         */
        public void waitChannel() {
            // Wait until channel is finished (otherwise redirections will not work)
            int i = MAX_ITER_DISCONNECT;
            
            while (true) {
                // if channel is closed or tried max times
                if (channel.isClosed() || (--i <= 0)) break;
                
                try {
                    Thread.sleep(WAIT_DISCONNECT);
                } catch (InterruptedException e) {
                    // set the 'interrupted' flag
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }
    
    /**
     * This class encapsulates input/output operation for the channel attached to this session. 
     *
     * @param <C> channel type wrapped by this session holder
     */
    public static class ChannelStreams<C extends Channel> {
        
        private static final int INPUT_WAIT = 100;
        private static final int BUFFER_SIZE = 100 * 1024;
        
        private C channel;
        private InputStream in;
        private OutputStream out;
        
        private byte[] tmp = new byte[BUFFER_SIZE];
        
        private static final Logger LOG = LoggerFactory.getLogger(ChannelStreams.class);
        
        /**
         * Constructor for channel I/O object
         * 
         * @param channel the channel to which I/O operation will be directed
         */
        public ChannelStreams(C channel) {
            this.channel = channel;
            try {
                in = channel.getInputStream();
            } catch (IOException e) {
                throw new IllegalStateException("Failed to acquire channel input stream", e);
            }
            try {
                out = channel.getOutputStream();
            } catch (IOException e) {
                throw new IllegalStateException("Failed to acquire channel output stream", e);
            }
        }
        
        /**
         * Read the input from the channel.
         * 
         * @param waitClose 'true' to poll for input until the channel closes; 'false' to return available input
         * @return channel input (may be empty)
         * @throws IOException if an I/O error occurs
         */
        public String readChannel(boolean waitClose) throws IOException {
            StringBuilder stdout = new StringBuilder();
            try {
                while (true) {
                    String recv = readAvailable();
                    if (recv != null) stdout.append(recv);

                    if (!waitClose || channel.isClosed()) break;
                    
                    Thread.sleep(INPUT_WAIT);
                }
            } catch (InterruptedException e) {
                // set the 'interrupted' flag
                Thread.currentThread().interrupt();
            }

            return stdout.toString();
        }

        /**
         * Read available input from the specified stream.
         * 
         * @return available input (may be 'null')
         * @throws IOException if an I/O error occurs
         */
        private String readAvailable() throws IOException {
            if (in.available() > 0) {
                int i = in.read(tmp, 0, BUFFER_SIZE);
                if (i < 0) return null;

                String recv = new String(tmp, 0, i);
                return recv;
            }

            return null;
        }
        
        /**
         * Wait for input to be available.
         * 
         * @throws IOException if an I/O error occurs
         */
        public void waitForInput() throws IOException {
            try {
                while (in.available() == 0) {
                    Thread.sleep(INPUT_WAIT);
                }
            } catch (InterruptedException e) {
                // set the 'interrupt' flag
                Thread.currentThread().interrupt();
            }
        }
        
        /**
         * Wait for the specified prompt to be received from the remote host.
         * 
         * @param prompt prompt to wait for
         * @param maxWait maximum interval in milliseconds to wait for the specified prompt; -1 to wait indefinitely
         * @return all of the input that was received while waiting for the prompt
         * @throws InterruptedException if this thread was interrupted
         * @throws IOException if an I/O error occurs
         */
        public String waitForPrompt(String prompt, long maxWait) throws InterruptedException, IOException {
            StringBuilder input = new StringBuilder();
            long maxTime = System.currentTimeMillis() + maxWait;
            
            while (true) {
                String recv = readChannel(false);
                if (recv != null) {
                    input.append(recv);
                    if (input.toString().contains(prompt)) break;
                }
                
                if ((maxWait != -1) && (System.currentTimeMillis() > maxTime)) break;
                Thread.sleep(INPUT_WAIT);
            }
            
            return input.toString();
        }
        
        /**
         * Write the specified string to the remote host, followed by a carriage return
         * 
         * @param line the line of text to be written
         * @throws IOException if an I/O error occurs
         */
        public void writeln(String line) throws IOException {
            out.write((line + "\n").getBytes());
            out.flush();
        }
        
    }

    /**
     * This class provides stub implementations for the {@link UserInfo} and {@link UIKeyboardInteractive} interfaces.
     * Objects of this type hold user credentials and implement methods for user-interactive authentication. 
     * @author <a href='http://stackoverflow.com/users/448078/mykhaylo-adamovych'>Mykhaylo Adamovych</a>
     */
    private static class User implements UserInfo, UIKeyboardInteractive {

        private static final Logger LOG = LoggerFactory.getLogger(User.class);
        
        private String user;
        private String pass;

        /**
         * Constructor for user authentication objects
         * 
         * @param user user name
         * @param pass password
         */
        public User(String user, String pass) {
            this.user = user;
            this.pass = pass;
        }

        /**
         * Returns the password entered by the user.<br>
         * <b>NOTE</b>: Invoke this method only if {@link #promptPassword} succeeds.
         * 
         * @return password entered by the user
         */
        @Override
        public String getPassword() {
            return pass;
        }

        /**
         * Prompts the user to answer a {@code Yes/No} question.<br>
         * <b>NOTE</b>: These are currently used to decide whether to create non-existent files or directories, 
         * whether to replace an existing host key, and whether to connect despite a non-matching key.
         * 
         * @param message the prompt message to be shown to the user
         * @return 'true' if the user answered "Yes"; otherwise 'false'
         */
        @Override
        public boolean promptYesNo(String message) {
            LOG.debug("promptYesNo: {}", message);
            return false;
        }

        /**
         * Returns the passphrase entered by the user.<br>
         * <b>NOTE</b>: Invoke this method only if {@link #promptPassphrase} succeeds.
         * 
         * @return passphrase entered by the used
         */
        @Override
        public String getPassphrase() {
            return user;
        }

        /**
         * Prompts the user for a passphrase for a public key.
         * 
         * @param message the prompt message to be shown to the user
         * @return 'true' if the user entered a passphrase. This passphrase can be retrieved by {@link #getPassphrase}.
         */
        @Override
        public boolean promptPassphrase(String message) {
            LOG.debug("promptPassphrase: {}", message);
            return true;
        }

        /**
         * Prompts the user for a password used for authentication for the remote server.
         * 
         * @param message the prompt string to be shown to the user
         * @return 'true' if the user entered a password. This password can be retrieved by {@link #getPassword}.
         */
        @Override
        public boolean promptPassword(String message) {
            LOG.debug("promptPassword: {}", message);
            return true;
        }

        /**
         * Shows an informational message to the user.
         * 
         * @param message the message to show to the user
         */
        @Override
        public void showMessage(String message) {
            LOG.debug("showMessage: {}", message);
        }

        /**
         * Retrieves answers from the user to a number of questions.
         * 
         * @param destination
         *            identifies the user/host pair where we want to login.
         *            (This was not sent by the remote side).
         * @param name
         *            the name of the request (could be shown in the window
         *            title). This may be empty.
         * @param instruction
         *            an instruction string to be shown to the user. This may be
         *            empty, and may contain new-lines.
         * @param prompt
         *            a list of prompt strings.
         * @param echo
         *            for each prompt string, whether to show the texts typed in
         *            (true) or to mask them (false). This array will have the
         *            same length as prompt.
         * @return the answers as given by the user. This must be an array of
         *         same length as prompt, if the user confirmed. If the user
         *         cancels the input, the return value should be 'null'.
         */
        @Override
        public String[] promptKeyboardInteractive(String destination, String name, String instruction, String[] prompt, boolean[] echo) {
            StringBuilder builder = new StringBuilder("promptKeyboardInteractive: ");
            builder.append("\n    destination: ").append(destination);
            builder.append("\n    name: ").append(name);
            builder.append("\n    instruction: ");
            for (String line : instruction.split("\n")) {
                builder.append("\n        ").append(line);
            }
            for (int i = 0; i < prompt.length; i++) {
                builder.append("\n    prompt #").append(i + 1)
                        .append(" [").append((echo[i]) ? "echo" : "mask").append("]: ")
                        .append(prompt[i]);
            }
            LOG.debug(builder.toString());
            return null;
        }
    }
}
