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
import com.nordstrom.remote.RemoteConfig.RemoteSettings;

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
import java.nio.file.Path;
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
            throw new RemoteFileUploadFailedError("Cannot upload file", e);
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
            throw new RemoteFileDownloadFailedError("Cannot download file", e);
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
            throw new RemoteInputStreamInstantiationError(e);
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
        } catch (JSchException e) {
            throw new RemoteExecutionFailedError(session, e);
        } catch (InterruptedException e) {
            // set the 'interrupted' flag
            Thread.currentThread().interrupt();
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
            String changeDir = "";
            String workDir = session.getWorkDir();
            
            if (workDir != null) {
                changeDir = "cd " + workDir + " && ";
            }
            
            return exec(session, changeDir + command);
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
        String output = null;
        try (PipedOutputStream errPipe = new PipedOutputStream();
                PipedInputStream errIs = new PipedInputStream(errPipe);
                InputStream is = session.getChannel().getInputStream()) {

            ChannelExec channel = session.getChannel();
            channel.setInputStream(null);
            channel.setErrStream(errPipe);
            channel.setCommand(command);

            LOG.info("Starting exec for " + session.getMaskedUri());
            session.execute();
            output = IOUtils.toString(is, Charset.defaultCharset());
            session.assertExitStatus(IOUtils.toString(errIs, Charset.defaultCharset()));
        } catch (IOException e) {
            throw new RemoteInputStreamInstantiationError(e);
        } catch (JSchException e) {
            throw new RemoteExecutionFailedError(session, output, e);
        } catch (InterruptedException e) {
            // set the 'interrupted' flag
            Thread.currentThread().interrupt();
        }
        return trim(output);
    }

    /**
     * This is a wrapper class for objects that extend the {@link Channel} class. 
     * @author <a href='http://stackoverflow.com/users/448078/mykhaylo-adamovych'>Mykhaylo Adamovych</a>
     * 
     * @param <C> channel type wrapped by this session holder
     */
    public static class SessionHolder<C extends Channel> implements Closeable {

        private static final int SESSION_CONNECT_TIMEOUT;
        private static final int SSH_PORT_NUMBER;
        private static final int TERMINAL_HEIGHT;
        private static final int TERMINAL_WIDTH;
        private static final int TERMINAL_H_RESOLUTION;
        private static final int TERMINAL_V_RESOLUTION;
        private static final int COMPLETION_CHECK_INTERVAL;

        private static final int DISCONNECT_CHECK_ATTEMPTS;
        private static final int DISCONNECT_CHECK_INTERVAL;

        private ChannelType channelType;
        private URI uri;
        private Session session;
        private C channel;
        
        static {
            RemoteConfig config = RemoteConfig.getConfig();
            SESSION_CONNECT_TIMEOUT = config.getInt(RemoteSettings.SESSION_CONNECT_TIMEOUT.key());
            SSH_PORT_NUMBER = config.getInt(RemoteSettings.SSH_PORT_NUMBER.key());
            TERMINAL_HEIGHT = config.getInt(RemoteSettings.TERMINAL_HEIGHT.key());
            TERMINAL_WIDTH = config.getInt(RemoteSettings.TERMINAL_WIDTH.key());
            TERMINAL_H_RESOLUTION = config.getInt(RemoteSettings.TERMINAL_H_RESOLUTION.key());
            TERMINAL_V_RESOLUTION = config.getInt(RemoteSettings.TERMINAL_V_RESOLUTION.key());
            COMPLETION_CHECK_INTERVAL = config.getInt(RemoteSettings.COMPLETION_CHECK_INTERVAL.key());

            DISCONNECT_CHECK_ATTEMPTS = config.getInt(RemoteSettings.DISCONNECT_CHECK_ATTEMPTS.key());
            DISCONNECT_CHECK_INTERVAL = config.getInt(RemoteSettings.DISCONNECT_CHECK_INTERVAL.key());
        }

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
                
                String pass = getPass();
                if (pass == null) {
                    RemoteConfig remoteConfig = RemoteConfig.getConfig();
                    Path keyPath = remoteConfig.getKeyPath();
                    
                    if (keyPath == null) {
                        throw new RemoteCredentialsUnspecifiedError();
                    }
                    
                    Path pubPath = keyPath.resolveSibling(keyPath.getFileName() + ".pub");
                    
                    String keyPass = remoteConfig.getString(RemoteSettings.SSH_KEY_PASS.key());
                    if (keyPass != null) {
                        jsch.addIdentity(keyPath.toString(), pubPath.toString(), keyPass.getBytes());
                    } else {
                        jsch.addIdentity(keyPath.toString());
                    }
                    
                    Path knownHosts = keyPath.resolveSibling("known_hosts");
                    if (knownHosts.toFile().exists()) {
                        jsch.setKnownHosts(knownHosts.toString());
                    }
                }

                Session newSession = jsch.getSession(getUser(), getHost(), getPort());
                
                if (pass != null) {
                    newSession.setPassword(pass);
                }
                
                newSession.setDaemonThread(true);
                newSession.setConfig(config);
                newSession.connect(SESSION_CONNECT_TIMEOUT);
                
                return newSession;
            } catch (JSchException e) {
                throw new RemoteSessionInstantiationError("Cannot create session for " + getMaskedUri(), e);
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
                    ((ChannelShell) newChannel).setPtyType("ANSI",
                                    TERMINAL_WIDTH, TERMINAL_HEIGHT, TERMINAL_H_RESOLUTION, TERMINAL_V_RESOLUTION);
                }
                return (C) newChannel;
            } catch (JSchException e) {
                throw new RemoteChannelInstantiationError("Cannot create " + channelType + " channel for " + getMaskedUri(), e);
            }
        }

        /**
         * Verify that the remote task completed normally
         * 
         * @param taskOutput output from the remote task
         * @throws RemoteExecutionFailedError if exit status is non-zero
         */
        public void assertExitStatus(String taskOutput) {
            if (getExitStatus() != 0) {
                throw new RemoteExecutionFailedError(this, taskOutput);
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
                sleep(COMPLETION_CHECK_INTERVAL);
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
               return new ChannelStreams<>(channel);
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
            if (getPass() != null) {
                return uri.toString().replaceFirst(":[^:]*?@", "@");
            }
            return uri.toString();
        }
        
        /**
         * Get the host of the URI for this {@link SessionHolder}
         * 
         * @return host component of the SSH connection URI
         */
        public String getHost() {
            return uri.getHost();
        }

        /**
         * Get the port of the URI for this {@link SessionHolder}
         * 
         * @return port component of the SSH connection URI
         */
        public int getPort() {
            if (uri.getPort() >= 0) {
                return uri.getPort();
            }
            return SSH_PORT_NUMBER;
        }

        /**
         * Get the user specified in the URI for this {@link SessionHolder}
         * 
         * @return user specified in the SSH connection URI
         */
        public String getUser() {
            String userInfo = uri.getUserInfo();
            if (userInfo != null) {
                return userInfo.split(":")[0];
            }
            return null;
        }

        /**
         * Get the password specified in the URI for this {@link SessionHolder}
         * 
         * @return password specified in the SSH connection URI
         */
        private String getPass() {
            String userInfo = uri.getUserInfo();
            if (userInfo != null) {
                String[] userBits = userInfo.split(":");
                if (userBits.length > 1) {
                    return userBits[1];
                }
            }
            return null;
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
               if (waitClose) {
                   waitChannel();
               }
               close();
        }

        /**
         * Wait for channel to close.<br>
         * <b>NOTE</b>: This method polls the channel 'closed' state a maximum of {@link #DISCONNECT_CHECK_ATTEMPTS} times,
         * delaying {@link #DISCONNECT_CHECK_INTERVAL} milliseconds between each check.
         */
        public void waitChannel() {
            try {
                // Wait until channel is finished (otherwise redirections will not work)
                for (int i = DISCONNECT_CHECK_ATTEMPTS; !channel.isClosed() && i > 0; i--) {
                    Thread.sleep(DISCONNECT_CHECK_INTERVAL);
                }
            } catch (InterruptedException e) {
                // set the 'interrupted' flag
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * This class encapsulates input/output operation for the channel attached to this session. 
     *
     * @param <C> channel type wrapped by this session holder
     */
    public static class ChannelStreams<C extends Channel> {
        
        private static final int CHECK_INTERVAL;
        private static final int BUFFER_SIZE;
        
        static {
            RemoteConfig config = RemoteConfig.getConfig();
            CHECK_INTERVAL = config.getInt(RemoteSettings.CHANNEL_CHECK_INTERVAL.key());
            BUFFER_SIZE = config.getInt(RemoteSettings.CHANNEL_BUFFER_SIZE.key());
        }
        
        private C channel;
        private InputStream in;
        private OutputStream out;
        
        private byte[] tmp = new byte[BUFFER_SIZE];
        
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
         * @throws InterruptedException if this thread was interrupted
         * @throws IOException if an I/O error occurs
         */
        public String readChannel(boolean waitClose) throws InterruptedException, IOException {
            StringBuilder stdout = new StringBuilder();
            while (appendAvailable(stdout) && waitClose && !channel.isClosed()) {   //NOSONAR
                Thread.sleep(CHECK_INTERVAL);
            }
            return stdout.toString();
        }
        
        /**
         * Append available input to the specified string builder.
         * 
         * @param stdout {@link StringBuilder} object
         * @return this method always returns 'true'
         * @throws IOException if an I/O error occurs
         */
        private boolean appendAvailable(StringBuilder stdout) throws IOException {
            String recv = readAvailable();
            if (recv != null) {
                stdout.append(recv);
            }
            return true;
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
                if (i != -1) {
                    return new String(tmp, 0, i);
                }
            }
            return null;
        }
        
        /**
         * Wait for input to be available.
         * 
         * @throws InterruptedException if this thread was interrupted
         * @throws IOException if an I/O error occurs
         */
        public void waitForInput() throws InterruptedException, IOException {
            while (in.available() == 0) {
                Thread.sleep(CHECK_INTERVAL);
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
            return waitForPrompt(prompt, maxWait, null);
        }
        
        /**
         * Wait for the specified prompt to be received from the remote host.
         * 
         * @param prompt prompt to wait for
         * @param maxWait maximum interval in milliseconds to wait for the specified prompt; -1 to wait indefinitely
         * @param logger SLF4J {@link Logger} object for output (may be 'null')
         * @return all of the input that was received while waiting for the prompt
         * @throws InterruptedException if this thread was interrupted
         * @throws IOException if an I/O error occurs
         */
        public String waitForPrompt(String prompt, long maxWait, Logger logger) throws InterruptedException, IOException {
            StringBuilder input = new StringBuilder();
            long maxTime = System.currentTimeMillis() + maxWait;
            
            while (appendAndCheckFor(prompt, input, logger) && ((maxWait == -1) || (System.currentTimeMillis() <= maxTime))) {
                Thread.sleep(CHECK_INTERVAL);
            }
            
            return input.toString();
        }
        
        /**
         * Append available channel input to the supplied string builder and check for the specified prompt.
         * 
         * @param prompt prompt to check for
         * @param input {@link StringBuilder} object
         * @param logger SLF4J {@link Logger} object for output (may be 'null')
         * @return 'false' is prompt is found or channel is closed; otherwise 'true'
         * @throws InterruptedException if this thread was interrupted
         * @throws IOException if an I/O error occurs
         */
        private boolean appendAndCheckFor(String prompt, StringBuilder input, Logger logger) throws InterruptedException, IOException {
            String recv = readChannel(false);
            if ( ! ((recv == null) || recv.isEmpty())) {
                input.append(recv);
                if (logger != null) {
                    logger.debug(recv);
                }
                if (input.toString().contains(prompt)) {
                    return false;
                }
            }
            return !channel.isClosed();
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
}
