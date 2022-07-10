package com.nordstrom.remote;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSchException;
import com.nordstrom.common.base.UncheckedThrow;
import com.nordstrom.remote.SshUtils.ChannelStreams;
import com.nordstrom.remote.SshUtils.ChannelType;
import com.nordstrom.remote.SshUtils.SessionHolder;

/**
 * <b>BatchUtils</b> is a reference implementation of a <b>JSch</b> client. It enables you to execute the specified
 * command, optionally executing an initial command to switch to an alternate user first.
 * 
 * <pre>
 * String userName = "user";
 * String password = "password";
 * String hostName = "host";
 * String sudoCmd = "sudo su - admin";
 * String batchDir = "/work/dir/path";
 * String batchCmd = "./script.ksh parm1 parm2";
 * String output = SshUtils.executeBatch(userName, password, hostName, sudoCmd, batchDir, batchCmd);
 * System.out.println(output);</pre>
 *
 * The implementation of `BatchUtils` demonstrates how to use a couple of important **Remote Session** classes:
 * <p><ul>
 * <li>{@link SessionHolder} - This is a wrapper class for objects that extend the {@link Channel} class. The wrapper
 *     implements the {@link Closeable} interface, and {@link SshUtils} uses a "try-with-resources" block to ensure
 *     that the channel is always closed regardless of the outcome of command execution. <b>SessionHolder</b> includes
 *     these methods (among others): <ul>
 *     <li>{@link SessionHolder#getChannel getChannel} - Get the channel to the remote session created for this <b>SessionHolder</b>.</li>
 *     <li>{@link SessionHolder#getChannelStream getChannelStream} - Get a new channel stream object for this session.</li>
 *     <li>{@link SessionHolder#disconnect disconnect} - Disconnect channel and session.</li>
 *     <li>{@link SessionHolder#assertExitStatus assertExitStatus} - Verifies that the remote task completed normally.</li></ul></li>
 * <li>{@link ChannelStreams} - This class encapsulates input/output operation for the channel attached to this session.
 *     It includes these methods: <ul>
 *     <li>{@link ChannelStreams#waitForInput waitForInput} - Wait for input to be available.</li>
 *     <li>{@link ChannelStreams#writeln writeln} - Write the specified string to the remote host, followed by a carriage return.</li>
 *     <li>{@link ChannelStreams#waitForPrompt waitForPrompt} - Wait for the specified prompt to be received from the remote host.</li>
 *     <li>{@link ChannelStreams#readChannel readChannel} - Read the input from the channel.</li></ul></li></ul>
 */
public class BatchUtils {

    private BatchUtils() {
        throw new AssertionError("BatchUtils is a static utility class that cannot be instantiated");
    }

    private static final Logger LOG = LoggerFactory.getLogger(BatchUtils.class);
    
    /**
     * Execute a batch command on using the specified credential on the indicated remote host.
     * 
     * @param userName user name for remote host connection
     * @param password password for remote host connection
     * @param hostname name of remote host
     * @param sudoCmd {@code sudo su} command to open batch execution shell session
     * @param batchDir directory that contains the specified batch command
     * @param batchCmd batch command to be executed
     * @return output from the specified batch command
     */
    public static String executeBatch(String userName, String password, String hostname, String sudoCmd, String batchDir, String batchCmd) {
        String output = null;
        String connectUri = null;
        
        try {
            connectUri = "ssh://" + userName + ":" + URLEncoder.encode(password, "UTF-8") + "@" + hostname + batchDir;
        } catch (UnsupportedEncodingException e) {
            // This exception will never be thrown
        }
        
        if (sudoCmd != null) {
            int idx = sudoCmd.lastIndexOf(' ') + 1;
            
            String userPrompt = userName.toLowerCase() + "@" + hostname + ":";
            String sudoPrompt = sudoCmd.substring(idx) + "@" + hostname + ":";
            
            try (SessionHolder<ChannelExec> session = new SessionHolder<>(ChannelType.EXEC, URI.create(connectUri))) {
                ChannelExec channel = session.getChannel();
                
                channel.setErrStream(System.err);
                channel.setPty(true);
                channel.setCommand(sudoCmd);
                channel.connect();
                
                ChannelStreams<?> channelStream = session.getChannelStream();
                
                // wait for password prompt
                channelStream.waitForInput();
                
                // submit password
                channelStream.writeln(password);
                LOG.info(channelStream.waitForPrompt(sudoPrompt, 1000));
                
                // change to the batch directory
                channelStream.writeln("cd " + batchDir);
                LOG.info(channelStream.waitForPrompt(sudoPrompt, 1000));
                
                // execute the batch command
                channelStream.writeln(batchCmd);
                output = channelStream.waitForPrompt(sudoPrompt, -1);
                
                // exit from batch user shell
                channelStream.writeln("exit");
                LOG.info(channelStream.waitForPrompt(userPrompt, 1000));
                
                // exit from pseudo-terminal
                channelStream.writeln("exit");
                session.disconnect(true);
                
                session.assertExitStatus(output);
            } catch (JSchException e) {
                throw UncheckedThrow.throwUnchecked(e);
            } catch (IOException e) {
                throw UncheckedThrow.throwUnchecked(e);
            } catch (InterruptedException e) {
                // set the 'interrupt' flag
                Thread.currentThread().interrupt();
            }
        } else {
            output = SshUtils.exec(connectUri, batchCmd);
        }
        
        LOG.info(output);
        return output;
    }
    
}
