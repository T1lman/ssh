package ssh.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Configuration class for SSH server.
 */
public class ServerConfig {
    private int port;
    private String host;
    private String keyDirectory;
    private String usersFile;
    private String authorizedKeysDir;
    private int maxConnections;
    private long sessionTimeout;
    private String logLevel;

    public ServerConfig() {
        // Default values
        this.port = 0; // Will be set by user
        this.host = null; // Will be set by user
        this.keyDirectory = "data/server/server_keys";
        this.usersFile = "data/server/users.properties";
        this.authorizedKeysDir = "data/server/authorized_keys";
        this.maxConnections = 10;
        this.sessionTimeout = 1800000; // 30 minutes
        this.logLevel = "INFO";
    }

    // Getters and setters
    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getKeyDirectory() {
        return keyDirectory;
    }

    public void setKeyDirectory(String keyDirectory) {
        this.keyDirectory = keyDirectory;
    }

    public String getUsersFile() {
        return usersFile;
    }

    public void setUsersFile(String usersFile) {
        this.usersFile = usersFile;
    }

    public String getAuthorizedKeysDir() {
        return authorizedKeysDir;
    }

    public void setAuthorizedKeysDir(String authorizedKeysDir) {
        this.authorizedKeysDir = authorizedKeysDir;
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }

    public long getSessionTimeout() {
        return sessionTimeout;
    }

    public void setSessionTimeout(long sessionTimeout) {
        this.sessionTimeout = sessionTimeout;
    }

    public String getLogLevel() {
        return logLevel;
    }

    public void setLogLevel(String logLevel) {
        this.logLevel = logLevel;
    }

    /**
     * Load configuration from a properties file.
     *
     * @param filename Path to the properties file
     */
    public void loadFromFile(String filename) {
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(filename)) {
            props.load(fis);
            
            this.port = Integer.parseInt(props.getProperty("server.port", String.valueOf(this.port)));
            this.host = props.getProperty("server.host", this.host);
            this.keyDirectory = props.getProperty("server.key.directory", this.keyDirectory);
            this.usersFile = props.getProperty("server.users.file", this.usersFile);
            this.authorizedKeysDir = props.getProperty("server.authorized.keys.dir", this.authorizedKeysDir);
            this.maxConnections = Integer.parseInt(props.getProperty("server.max.connections", String.valueOf(this.maxConnections)));
            this.sessionTimeout = Long.parseLong(props.getProperty("server.session.timeout", String.valueOf(this.sessionTimeout)));
            this.logLevel = props.getProperty("server.log.level", this.logLevel);

        } catch (IOException e) {
            System.err.println("Warning: Could not load configuration file " + filename + ". Using default values.");
        } catch (NumberFormatException e) {
            System.err.println("Warning: Invalid number format in configuration file. " + e.getMessage());
        }
    }

    @Override
    public String toString() {
        return "ServerConfig{" +
                "port=" + port +
                ", host='" + host + '\'' +
                ", keyDirectory='" + keyDirectory + '\'' +
                ", usersFile='" + usersFile + '\'' +
                ", authorizedKeysDir='" + authorizedKeysDir + '\'' +
                ", maxConnections=" + maxConnections +
                ", sessionTimeout=" + sessionTimeout +
                ", logLevel='" + logLevel + '\'' +
                '}';
    }
} 