package com.nordstrom.remote;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import org.apache.commons.configuration2.ex.ConfigurationException;

import com.nordstrom.automation.settings.SettingsCore;

/**
 * This class declares settings and methods used to configure the <b>JSch</b> library.
 */
public class RemoteConfig extends SettingsCore<RemoteConfig.RemoteSettings> {
    
    private static final String SETTINGS_FILE = "remote.properties";
    
    private static final int ROWS = 24;
    private static final int COLS = 132;
    private static final int CELL_WIDTH = 7;
    private static final int CELL_HEIGHT = 9;
    private static final int KILO = 2 ^ 10;
    private static final int BUFFER_SIZE = 100 * KILO;
    
    /**
     * This enumeration declares the settings that enable you to control the parameters
     * that configure the <b>JSch</b> library.
     * <p>
     * Each setting is defined by a constant name and System property key. Many settings
     * also define default values. Note that all of these settings can be overridden via
     * the {@code remote.properties} file and System property declarations.
     */
    public enum RemoteSettings implements SettingsCore.SettingsAPI {
        /** name: <b>remote.account.username</b> <br> default: {@code null} */
        ACCOUNT_USERNAME("remote.account.username", null),
        /** name: <b>remote.account.password</b> <br> default: {@code null} */
        ACCOUNT_PASSWORD("remote.account.password", null),
        /** name: <b>remote.ssh.key.name</b> <br> default: <b>id_rsa</b> */
        SSH_KEY_NAME("remote.ssh.key.name", "id_rsa"),
        /** name: <b>remote.ssh.pub.name</b> <br> default: {@code null} */
        SSH_PUB_NAME("remote.ssh.pub.name", null),
        /** name: <b>remote.ssh.key.pass</b> <br> default: {@code null} */
        SSH_KEY_PASS("remote.ssh.key.pass", null),
        /** name: <b>remote.userinfo.class</b> <br> default: {@code null} */
        USERINFO_CLASS("remote.userinfo.class", null),
        /** name: <b>remote.known.hosts.path</b> <br> default: <b>known_hosts</b> */
        KNOWN_HOSTS_NAME("remote.known.hosts.path", "known_hosts"),
        /** name: <b>remote.trust.strategy</b> <br> default: <b>yes</b> */
        TRUST_STRATEGY("remote.trust.strategy", "yes"),
        /** name: <b>remote.session.connect.timeout</b> <br> default: <b>5000</b> */
        SESSION_CONNECT_TIMEOUT("remote.session.connect.timeout", "5000"),
        /** name: <b>remote.ssh.port.number</b> <br> default: <b>22</b> */
        SSH_PORT_NUMBER("remote.ssh.port.number", "22"),
        /** name: <b>remote.terminal.height</b> <br> default: <b>24</b> */
        TERMINAL_HEIGHT("remote.terminal.height", Integer.toString(ROWS)),
        /** name: <b>remote.terminal.width</b> <br> default: <b>132</b> */
        TERMINAL_WIDTH("remote.terminal.width", Integer.toString(COLS)),
        /** name: <b>remote.terminal.h.resolution</b> <br> default: <b>924</b> */
        TERMINAL_H_RESOLUTION("remote.terminal.h.resolution", Integer.toString(COLS * CELL_WIDTH)),
        /** name: <b>remote.terminal.v.resolution</b> <br> default: <b>216</b> */
        TERMINAL_V_RESOLUTION("remote.terminal.v.resolution", Integer.toString(ROWS * CELL_HEIGHT)),
        /** name: <b>remote.completion.check.interval</b> <br> default: <b>100</b> */
        COMPLETION_CHECK_INTERVAL("remote.completion.check.interval", "100"),
        /** name: <b>remote.disconnect.check.attempts</b> <br> default: <b>600</b> */
        DISCONNECT_CHECK_ATTEMPTS("remote.disconnect.check.attempts", "600"),
        /** name: <b>remote.disconnect.check.interval</b> <br> default: <b>100</b> */
        DISCONNECT_CHECK_INTERVAL("remote.disconnect.check.interval", "100"),
        /** name: <b>remote.channel.check.interval</b> <br> default: <b>100</b> */
        CHANNEL_CHECK_INTERVAL("remote.channel.check.interval", "100"),
        /** name: <b>remote.channel.buffer.size</b> <br> default: <b>102400</b> */
        CHANNEL_BUFFER_SIZE("remote.channel.buffer.size", Integer.toString(BUFFER_SIZE));
        
        private String propertyName;
        private String defaultValue;
        
        RemoteSettings(String propertyName, String defaultValue) {
            this.propertyName = propertyName;
            this.defaultValue = defaultValue;
        }
        
        @Override
        public String key() {
            return propertyName;
        }

        @Override
        public String val() {
            return defaultValue;
        }
    }
    
    /**
     * Defines the security strategies for verifying the identity of a remote host
     * during an SSH handshake. This maps directly to the 'StrictHostKeyChecking'
     * configuration in the JSch library.
     */
    public enum HostTrustStrategy {
        
        /**
         * Corresponds to JSch {@code yes}. 
         * <p>
         * The host's public key must exist in the {@code known_hosts} file. If the 
         * key is missing or does not match, the connection will be rejected. 
         * This is the most secure option and prevents Man-in-the-Middle (MITM) attacks.
         * </p>
         */
        STRICT("yes"),
        
        /**
         * Corresponds to JSch {@code ask}.
         * <p>
         * If a host is not found in the {@code known_hosts} file, the user is 
         * prompted via a {@code UserInfo} implementation to accept or reject the 
         * new key. Successfully accepted keys are appended to the {@code known_hosts} 
         * file (requires write permissions).
         * </p>
         */
        INTERACTIVE("ask"),
        
        /**
         * Corresponds to JSch {@code no}.
         * <p>
         * Blindly trusts any host key presented by the remote server. The 
         * {@code known_hosts} file is ignored for verification purposes.
         * <b>Warning:</b> This is insecure and should only be used in trusted, 
         * transient, or isolated test environments.
         * </p>
         */
        ANONYMOUS("no");

        private final String jschValue;

        /**
         * Constructs a strategy with its associated JSch protocol string.
         * 
         * @param jschValue The internal JSch configuration string ('yes', 'ask', or 'no').
         */
        HostTrustStrategy(String jschValue) {
            this.jschValue = jschValue;
        }

        /**
         * Gets the string value expected by the JSch {@code StrictHostKeyChecking} configuration.
         * 
         * @return The JSch-compatible string value.
         */
        public String getJschValue() {
            return jschValue;
        }

        /**
         * Converts a string to its corresponding {@link HostTrustStrategy} constant.
         * This method is case-insensitive and matches against both the constant names 
         * (e.g., "STRICT") and their associated JSch values (e.g., "yes").
         * 
         * @param value The string to convert.
         * @return The matching {@link HostTrustStrategy} constant.
         * @throws IllegalArgumentException if the provided value is {@code null} or 
         * does not match a valid strategy.
         */
        public static HostTrustStrategy fromString(String value) {
            if (value == null) {
                throw new IllegalArgumentException("HostTrustStrategy value cannot be null");
            }
            
            return Arrays.stream(HostTrustStrategy.values())
                    .filter(strategy -> strategy.name().equalsIgnoreCase(value) ||
                                        strategy.jschValue.equalsIgnoreCase(value))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown HostTrustStrategy: " + value
                            + ". Valid options are: yes (STRICT), ask (INTERACTIVE), no (ANONYMOUS)."));
        }
    }
    
    private static final RemoteConfig remoteConfig;
    
    static {
        try {
            remoteConfig = new RemoteConfig();
        } catch (ConfigurationException | IOException e) {
            throw new RuntimeException("Failed to instantiate settings", e);
        }
    }
    
    /**
     * Instantiate a <b>Remote Session</b> configuration object.
     * 
     * @throws ConfigurationException If a failure is encountered while initializing this configuration object.
     * @throws IOException If a failure is encountered while reading from a configuration input stream.
     */
    public RemoteConfig() throws ConfigurationException, IOException {
        super(RemoteSettings.class);
    }

    /**
     * Get the remote configuration object.
     * 
     * @return remote configuration object
     */
    public static RemoteConfig getConfig() {
        return remoteConfig;
    }
    
    /**
     * Get the path to the specified SSH private key file.<br>
     * <b>NOTE</b>: If the private key file is specified by full path, this is used as-is. Otherwise, the
     * key file must be located in the {@code .ssh} folder of the active user's {@code HOME} directory.
     * 
     * @return SSH public key file path; 'null' if indicated file doesn't exist
     */
    public Path getKeyPath() {
        // get SSH private key name
        String keyName = remoteConfig.getString(RemoteSettings.SSH_KEY_NAME.key());
        // if private key name is specified
        if ( ! ((keyName == null) || keyName.isEmpty())) {
            // evaluate name as key path
            Path keyPath = Paths.get(keyName);
            // if key path exists
            if (keyPath.toFile().exists()) {
                // return path
                return keyPath;
            }
            
            // evaluate name as child of ~/.ssh folder
            keyPath = getSshFolderPath().resolve(keyName);
            // if key path exists in ~/.ssh
            if (keyPath.toFile().exists()) {
                // return path
                return keyPath;
            }
        }
        
        return null;
    }
    
    /**
     * Get the path to the specified SSH public key file.<br>
     * <b>NOTE</b>: If the public key file is specified by full path, this is used as-is. Otherwise, the
     * key file must be located in the {@code .ssh} folder of the active user's {@code HOME} directory.
     * 
     * @return SSH key file path; 'null' if indicated file doesn't exist
     */
    public Path getPubPath() {
        Path keyPath = getKeyPath();
        if (keyPath == null) return null;
        
        // get SSH public key name
        String pubName = remoteConfig.getString(RemoteSettings.SSH_PUB_NAME.key());
        // if public key name is unspecified
        if ((pubName == null) || pubName.isEmpty()) {
            // assemble public key name
            pubName = keyPath.toString() + ".pub";
        }
        
        // if public key name is specified
        if ( ! ((pubName == null) || pubName.isEmpty())) {
            // evaluate name as key path
            Path pubPath = Paths.get(pubName);
            // if key path exists
            if (pubPath.toFile().exists()) {
                // return path
                return pubPath;
            }
            
            pubPath = keyPath.resolveSibling(pubName);
            // if public key is sibling
            if (pubPath.toFile().exists()) {
                // return path
                return pubPath;
            }
            
            // evaluate name as child of ~/.ssh folder
            pubPath = getSshFolderPath().resolve(pubName);
            // if key path exists in ~/.ssh
            if (pubPath.toFile().exists()) {
                // return path
                return pubPath;
            }
        }
        
        return null;
    }
    
    /**
     * Get the path to the specified SSH known hosts file.<br>
     * <b>NOTE</b>: If the known hosts file is specified by full path, this is used as-is. Otherwise, the
     * file must be located in the {@code .ssh} folder of the active user's {@code HOME} directory.
     * 
     * @return SSH known hosts path; 'null' if indicated file doesn't exist
     */
    public Path getKnownHosts() {
        HostTrustStrategy trustStrategy = getTrustStrategy();
        if (trustStrategy == HostTrustStrategy.ANONYMOUS) {
            return null;
        }
        
        Path path;
        Path knownHostsPath = null;
        Path keyPath = getKeyPath();
        String knownHostsName = remoteConfig.getString(RemoteSettings.KNOWN_HOSTS_NAME.key());
        
        // if known hosts name is specified
        if ( ! ((knownHostsName == null) || knownHostsName.isEmpty())) {
            // evaluate name as known hosts path
            path = Paths.get(knownHostsName);
            // if key path exists
            if (path.toFile().exists()) {
                // 
                knownHostsPath = path;
            }
            
            if ((knownHostsPath == null) && (keyPath != null)) {
                path = keyPath.resolveSibling(knownHostsName);
                // if public key is sibling
                if (path.toFile().exists()) {
                    // 
                    knownHostsPath = path;
                }
            }
            
            if (knownHostsPath == null) {
                // evaluate name as child of ~/.ssh folder
                path = getSshFolderPath().resolve(knownHostsName);
                // if key path exists in ~/.ssh
                if (path.toFile().exists()) {
                    // 
                    knownHostsPath = path;
                }
            }
            
            if (trustStrategy == HostTrustStrategy.STRICT) {
                if (knownHostsPath == null) {
                    throw new IllegalStateException(
                            "STRICT mode requires a known_hosts file, but none was found.");
                }
            } else if (trustStrategy == HostTrustStrategy.INTERACTIVE) {
                if ((knownHostsPath != null) && !knownHostsPath.toFile().canWrite()) {
                    throw new IllegalStateException(
                            "INTERACTIVE mode requires write access to: " + knownHostsPath.toAbsolutePath());
                }
            }
        }
        
        return knownHostsPath;
    }
    
    /**
     * Get host trust strategy.
     * 
     * @return {@link HostTrustStrategy} constant`
     */
    public HostTrustStrategy getTrustStrategy() {
        return HostTrustStrategy.fromString(remoteConfig.getString(RemoteSettings.TRUST_STRATEGY.key()));
    }
    
    /**
     * Get the path to the current user's SSH folder.
     * 
     * @return user SSH folder path (i.e. - "{@code ~/.ssh}")
     */
    public Path getSshFolderPath() {
        return Paths.get(System.getProperty("user.home"), ".ssh");
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public String getSettingsPath() {
        return SETTINGS_FILE;
    }
}
