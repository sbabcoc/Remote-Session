package com.nordstrom.remote;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.RSAPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;

import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.core.CoreModuleProperties;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.jcraft.jsch.Buffer;
import com.jcraft.jsch.JSch;
import com.nordstrom.remote.RemoteConfig.RemoteSettings;

public class SshUtilsTest extends TestNgBase {
    private final String USER = "tester";
    private final String PASS = "password123";

    @BeforeMethod
    public void startServer() throws Exception {
        setSshServer(SshServer.setUpDefaultServer());
        getSshServer().setPort(0);
        
        // 2. Setup Host Key (The server's identity)
        KeyPairGenerator hostGen = KeyPairGenerator.getInstance("RSA");
        hostGen.initialize(2048);
        KeyPair hostPair = hostGen.generateKeyPair();
        getSshServer().setKeyPairProvider(KeyPairProvider.wrap(hostPair));

        generateClientIdentity();
        
        // 3. Integrated Authenticators
        getSshServer().setPasswordAuthenticator((u, p, s) -> USER.equals(u) && PASS.equals(p));
        
        getSshServer().setPublickeyAuthenticator((u, key, s) -> {
            if (!USER.equals(u)) return false;
            
            // Compare encoded byte arrays to avoid object-type mismatches
            return Arrays.equals(key.getEncoded(), getPublicKey().getEncoded());
        });
        
        // Requires both public key AND password to succeed
        getSshServer().getProperties().put(CoreModuleProperties.AUTH_METHODS.getName(), "publickey,password");
        
        setRemoteRoot(Files.createTempDirectory("ssh_remote_root"));
        getSshServer().setFileSystemFactory(new VirtualFileSystemFactory(getRemoteRoot()));
        
        getSshServer().setCommandFactory((channel, command) -> new Command() {
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
        
        getSshServer().setSubsystemFactories(Collections.singletonList(new SftpSubsystemFactory()));
        
        getSshServer().start();
        setServerPort(getSshServer().getPort());
        updateKnownHostsWithActualPort(getServerPort(), hostPair);
    }

    @AfterMethod
    public void stopServer() throws IOException {
        try {
            if (getSshServer() != null && getSshServer().isStarted()) {
                getSshServer().close(true).await(1000);
                System.out.println("[INFO] Mock SSH Server stopped successfully.");
            }
        } catch (IOException e) {
            System.err.println("[WARN] SSH Server shutdown interrupted: " + e.getMessage());
        } finally {
            recursiveDelete(getPrivateKeyPath());
            recursiveDelete(getRemoteRoot());
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
        String remoteUri = String.format("ssh://%s:%s@localhost:%d", USER, PASS, getServerPort());
        System.setProperty(RemoteSettings.SSH_KEY_NAME.key(), getPrivateKeyPath().toString());
        String result = SshUtils.exec(remoteUri, "echo 'Remote-Session-Test'");
        
        assertEquals(result.trim(), "Remote-Session-Test", "Command result mismatch");
    }
    
    @Test
    public void testSftpUpload() throws IOException {
        Path localPath = Files.createTempFile("sftp-test", ".txt");
        Files.write(localPath, "Hello SFTP".getBytes());
        
        String sourceUri = localPath.toUri().toString();
        String remoteUri = String.format("ssh://%s:%s@localhost:%d", USER, PASS, getServerPort());
        System.setProperty(RemoteSettings.SSH_KEY_NAME.key(), getPrivateKeyPath().toString());
        SshUtils.sftp(sourceUri, remoteUri);
        
        Path expectedFileOnServer = getRemoteRoot().resolve(localPath.getFileName());
        assertTrue(Files.exists(expectedFileOnServer), "File should exist on the mock server");
        String uploadedContent = new String(Files.readAllBytes(expectedFileOnServer));
        assertEquals(uploadedContent, "Hello SFTP", "Content mismatch on remote server!");
        
        Files.deleteIfExists(localPath);
    }
    
    private void generateClientIdentity() throws Exception {
        Path tempDir = Files.createTempDirectory("ssh_identity");
        setPrivateKeyPath(tempDir.resolve("id_rsa_test"));

        JSch jsch = new JSch();
        // 1. Generate the pair using JSch's generator
        com.jcraft.jsch.KeyPair kpair = com.jcraft.jsch.KeyPair.genKeyPair(jsch, com.jcraft.jsch.KeyPair.RSA, 2048);

        // 2. Write the Private Key in PEM format (Crucial for JSch to read it later)
        try (OutputStream os = new FileOutputStream(getPrivateKeyPath().toFile())) {
            kpair.writePrivateKey(os);
        }

        // 3. Convert the Public Key blob to a Java PublicKey for the Mock Server
        // This allows sshd.setPublickeyAuthenticator to work
        byte[] pubBlob = kpair.getPublicKeyBlob();
        Buffer buf = new Buffer(pubBlob);
        buf.getString(); // skip "ssh-rsa"
        byte[] e = buf.getMPInt();
        byte[] n = buf.getMPInt();
        
        RSAPublicKeySpec spec = new RSAPublicKeySpec(new BigInteger(1, n), new BigInteger(1, e));
        setPublicKey(KeyFactory.getInstance("RSA").generatePublic(spec));

        kpair.dispose();
    }
    
    private void updateKnownHostsWithActualPort(int actualPort, KeyPair hostPair) throws IOException {
        Path knownHostsPath = getPrivateKeyPath().resolveSibling("known_hosts");
        String encodedKey = Base64.getEncoder().encodeToString(hostPair.getPublic().getEncoded());
        String entry = String.format("[localhost]:%d ssh-rsa %s%n", actualPort, encodedKey);
        Files.write(knownHostsPath, entry.getBytes(StandardCharsets.UTF_8));
        System.out.println("Updated known_hosts for port: " + actualPort);
    }
}
