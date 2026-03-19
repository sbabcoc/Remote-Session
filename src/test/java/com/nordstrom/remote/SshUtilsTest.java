package com.nordstrom.remote;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Comparator;

import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class SshUtilsTest {
	private SshServer sshd;
    private int port;
    private Path mockRemoteRoot;
    private final String USER = "tester";
    private final String PASS = "password123";

    @BeforeClass
    public void startServer() throws IOException {
		sshd = SshServer.setUpDefaultServer();
		
		sshd.setPort(0);
		sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider());
        sshd.setPasswordAuthenticator((u, p, s) -> u.equals(USER) && p.equals(PASS));
        
        mockRemoteRoot = Files.createTempDirectory("ssh_remote_root");
        sshd.setFileSystemFactory(new VirtualFileSystemFactory(mockRemoteRoot));
        
        try {
            KeyPairGenerator exporter = KeyPairGenerator.getInstance("RSA");
            exporter.initialize(2048);
            KeyPair kp = exporter.generateKeyPair();
            sshd.setKeyPairProvider(KeyPairProvider.wrap(kp));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to generate in-memory host key", e);
        }
		
        sshd.setCommandFactory((channel, command) -> new Command() {
            private OutputStream out;
            private ExitCallback callback;

            @Override public void setOutputStream(OutputStream out) { this.out = out; }
            @Override public void setExitCallback(ExitCallback callback) { this.callback = callback; }

            @Override
            public void start(ChannelSession channel, Environment env) throws IOException {
            	new Thread(() -> {
                    try {
                        if (command.contains("Remote-Session-Test")) {
                            out.write("Remote-Session-Test\n".getBytes());
                            out.flush();
                            callback.onExit(0);
                        } else {
                            callback.onExit(1, "Unexpected command: " + command);
                        }
                    } catch (IOException e) {
                        callback.onExit(2, e.getMessage());
                    }
                }).start();
            }

            @Override public void destroy(ChannelSession channel) {}
            @Override public void setInputStream(InputStream in) {}
            @Override public void setErrorStream(OutputStream err) {}
        });
        
        sshd.setSubsystemFactories(Collections.singletonList(new SftpSubsystemFactory()));
        
        sshd.start();
        port = sshd.getPort();
    }

    @AfterClass(alwaysRun = true)
    public void stopServer() throws IOException {
    	try {
            if (sshd != null && sshd.isStarted()) {
                sshd.close(true).await(1000);
                System.out.println("[INFO] Mock SSH Server stopped successfully.");
            }
        } catch (IOException e) {
            System.err.println("[WARN] SSH Server shutdown interrupted: " + e.getMessage());
        } finally {
            recursiveDelete(mockRemoteRoot);
        }
    }
    
    private void recursiveDelete(final Path path) {
        if (path == null || !Files.exists(path)) return;
        try {
            Files.walk(path)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        } catch (IOException e) {
            System.err.println("Failed to clean up mock directory: " + path);
        }
    }
    
    @Test
    public void testExecCommand() {
        String remoteUri = String.format("ssh://%s:%s@localhost:%d", USER, PASS, port);
        String result = SshUtils.exec(remoteUri, "echo 'Remote-Session-Test'");
        
        assertEquals(result.trim(), "Remote-Session-Test", "Command result mismatch");
    }
    
    @Test
    public void testSftpUpload() throws IOException {
        Path localPath = Files.createTempFile("sftp-test", ".txt");
        Files.write(localPath, "Hello SFTP".getBytes());
        
        String sourceUri = localPath.toUri().toString();
        String remoteUri = String.format("ssh://%s:%s@localhost:%d", USER, PASS, port);
        SshUtils.sftp(sourceUri, remoteUri);
        
        Path expectedFileOnServer = mockRemoteRoot.resolve(localPath.getFileName());
        assertTrue(Files.exists(expectedFileOnServer), "File should exist on the mock server");
        String uploadedContent = new String(Files.readAllBytes(expectedFileOnServer));
        assertEquals(uploadedContent, "Hello SFTP", "Content mismatch on remote server!");
        
        Files.deleteIfExists(localPath);
    }
}
