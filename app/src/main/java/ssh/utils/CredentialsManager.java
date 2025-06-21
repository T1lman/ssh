package ssh.utils;

import ssh.client.ui.AuthCredentials;
import ssh.client.ui.ServerInfo;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Manages user credentials by reading from a standard credentials file.
 */
public class CredentialsManager {
    private static final String DEFAULT_CREDENTIALS_FILE = "config/credentials.properties";
    private Properties credentials;

    public CredentialsManager() {
        this(DEFAULT_CREDENTIALS_FILE);
    }

    public CredentialsManager(String credentialsFile) {
        this.credentials = new Properties();
        loadCredentials(credentialsFile);
    }

    /**
     * Load credentials from the specified file.
     */
    private void loadCredentials(String credentialsFile) {
        try (FileInputStream fis = new FileInputStream(credentialsFile)) {
            credentials.load(fis);
            Logger.info("Credentials loaded from: " + credentialsFile);
        } catch (IOException e) {
            Logger.error("Failed to load credentials from " + credentialsFile + ": " + e.getMessage());
            Logger.info("Current working directory: " + System.getProperty("user.dir"));
            Logger.info("Attempting to create default credentials...");
            // Create default credentials if file doesn't exist
            createDefaultCredentials();
        }
    }

    /**
     * Create default credentials if the file doesn't exist.
     */
    private void createDefaultCredentials() {
        credentials.setProperty("default.username", "admin");
        credentials.setProperty("default.password", "admin");
        credentials.setProperty("default.auth.type", "password");
        credentials.setProperty("server.host", "localhost");
        credentials.setProperty("server.port", "2222");
        Logger.info("Using default credentials - admin/admin");
    }

    /**
     * Get server information from credentials file.
     */
    public ServerInfo getServerInfo() {
        ServerInfo serverInfo = new ServerInfo();
        
        String host = credentials.getProperty("server.host", "localhost");
        String portStr = credentials.getProperty("server.port", "2222");
        String username = credentials.getProperty("default.username", "admin");
        
        serverInfo.setHost(host);
        try {
            serverInfo.setPort(Integer.parseInt(portStr));
        } catch (NumberFormatException e) {
            Logger.error("Invalid port in credentials file: " + portStr);
            serverInfo.setPort(2222);
        }
        serverInfo.setUsername(username);
        
        Logger.info("Server info - Host: " + host + ", Port: " + serverInfo.getPort() + ", Username: " + username);
        
        return serverInfo;
    }

    /**
     * Get authentication credentials for the default user.
     */
    public AuthCredentials getAuthCredentials() {
        return getAuthCredentials("default");
    }

    /**
     * Get authentication credentials for a specific user.
     */
    public AuthCredentials getAuthCredentials(String userKey) {
        AuthCredentials authCredentials = new AuthCredentials();
        
        String username = credentials.getProperty(userKey + ".username", "admin");
        String authType = credentials.getProperty(userKey + ".auth.type", "password");
        String password = credentials.getProperty(userKey + ".password", "");
        
        authCredentials.setUsername(username);
        authCredentials.setAuthType(authType);
        authCredentials.setPassword(password);
        
        Logger.info("Auth credentials for " + userKey + " - Username: " + username + ", Type: " + authType);
        
        // For public key authentication
        if ("publickey".equals(authType)) {
            String privateKeyPath = credentials.getProperty(userKey + ".private.key.path");
            String publicKeyPath = credentials.getProperty(userKey + ".public.key.path");
            
            if (privateKeyPath != null) {
                authCredentials.setPrivateKeyPath(privateKeyPath);
            }
            if (publicKeyPath != null) {
                authCredentials.setPublicKeyPath(publicKeyPath);
            }
        }
        
        return authCredentials;
    }

    /**
     * Get a list of available users.
     */
    public String[] getAvailableUsers() {
        return credentials.stringPropertyNames().stream()
                .filter(key -> key.endsWith(".username"))
                .map(key -> key.substring(0, key.length() - 9)) // Remove ".username"
                .toArray(String[]::new);
    }

    /**
     * Check if a user exists in the credentials file.
     */
    public boolean userExists(String userKey) {
        return credentials.containsKey(userKey + ".username");
    }

    /**
     * Get server info with custom user.
     */
    public ServerInfo getServerInfo(String userKey) {
        ServerInfo serverInfo = getServerInfo();
        
        if (userExists(userKey)) {
            String username = credentials.getProperty(userKey + ".username");
            serverInfo.setUsername(username);
        }
        
        return serverInfo;
    }

    /**
     * Get the credentials file path.
     */
    public String getCredentialsFilePath() {
        return DEFAULT_CREDENTIALS_FILE;
    }
} 