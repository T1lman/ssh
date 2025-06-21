package ssh.server.ui;

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
        this.port = 2222;
        this.host = "localhost";
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