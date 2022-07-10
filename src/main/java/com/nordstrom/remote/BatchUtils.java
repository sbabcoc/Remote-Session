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
