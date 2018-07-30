package com.nordstrom.remote;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.configuration2.ex.ConfigurationException;

import com.nordstrom.automation.settings.SettingsCore;

public class RemoteConfig extends SettingsCore<RemoteConfig.RemoteSettings> {
    
    private static final String SETTINGS_FILE = "remote.properties";
    
    private static final int ROWS = 24;
    private static final int COLS = 132;
    private static final int CELL_WIDTH = 7;
    private static final int CELL_HEIGHT = 9;
    private static final int KILO = 2 ^ 10;
    private static final int BUFFER_SIZE = 100 * KILO;
    
    public enum RemoteSettings implements SettingsCore.SettingsAPI {
        /** name: <b>remote.ssh.key.name</b> <br> default: <b>id_rsa</b> */
        SSH_KEY_NAME("remote.ssh.key.name", "id_rsa"),
        /** name: <b>remote.ssh.key.pass</b> <br> default: {@code null} */
        SSH_KEY_PASS("remote.ssh.key.pass", null),
        /** name: <b>remote.ignore.known.hosts</b> <br> default: <b>false</b> */
        IGNORE_KNOWN_HOSTS("remote.ignore.known.hosts", "false"),
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
     * Get the path to the specified SSH key file.<br>
     * <b>NOTE</b>: If the key file is specified by full path, this is used as-is. Otherwise, the key file must be
     * located in the {@code .ssh} folder of the active user's {@code HOME} directory.
     * 
     * @return SSH key file path; 'null' if indicated file doesn't exist
     */
    public Path getKeyPath() {
        String keyName = RemoteConfig.getConfig().getString(RemoteSettings.SSH_KEY_NAME.key());
        if ( ! ((keyName == null) || keyName.isEmpty())) {
            Path keyPath = Paths.get(keyName);
            if (keyPath.toFile().exists()) {
                return keyPath;
            }
            keyPath = getSshFolderPath().resolve(keyName);
            if (keyPath.toFile().exists()) {
                return keyPath;
            }
        }
        return null;
    }
    
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
