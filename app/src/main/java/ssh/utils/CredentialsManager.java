package ssh.utils;

import ssh.client.ui.AuthCredentials;
import ssh.client.ui.ServerInfo;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.Map;
import java.util.HashMap;

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
        credentials.setProperty("default.user", "admin");
        credentials.setProperty("admin.username", "admin");
        credentials.setProperty("admin.password", "admin");
        credentials.setProperty("admin.auth.type", "password");
        Logger.info("Using default credentials - admin/admin");
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
    public AuthCredentials getAuthCredentials(String user) {
        String authType = credentials.getProperty(user + ".auth.type");
        if (authType == null) {
            if ("default".equals(user)) {
                String defaultUser = credentials.getProperty("default.user", "admin");
                return getAuthCredentials(defaultUser);
            }
            throw new IllegalArgumentException("Authentication type not configured for user: " + user);
        }

        AuthCredentials auth = new AuthCredentials(authType);
        auth.setUsername(credentials.getProperty(user + ".username"));

        if ("password".equals(authType)) {
            auth.setPassword(credentials.getProperty(user + ".password"));
        } else if ("publickey".equals(authType)) {
            auth.setPrivateKeyPath(credentials.getProperty(user + ".privateKey"));
            auth.setPublicKeyPath(credentials.getProperty(user + ".publicKey"));
        }

        return auth;
    }

    /**
     * Get a list of available users.
     */
    public String[] getAvailableUsers() {
        return credentials.stringPropertyNames().stream()
                .filter(key -> key.endsWith(".auth.type"))
                .map(key -> key.substring(0, key.indexOf(".")))
                .distinct()
                .toArray(String[]::new);
    }

    /**
     * Get the credentials file path.
     */
    public String getCredentialsFilePath() {
        return DEFAULT_CREDENTIALS_FILE;
    }
} 