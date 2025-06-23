package ssh.model.utils;

import ssh.client.model.AuthCredentials;
import ssh.client.model.ServerInfo;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.Map;
import java.util.HashMap;
import java.io.File;

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
        String defaultUsername = credentials.getProperty("default.username", "admin");
        return getAuthCredentials(defaultUsername);
    }

    /**
     * Get authentication credentials for a specific user.
     */
    public AuthCredentials getAuthCredentials(String username) {
        // First, try to find the user key that corresponds to this username
        String userKey = findUserKeyByUsername(username);
        if (userKey == null) {
            throw new IllegalArgumentException("User not found: " + username);
        }

        String authType = credentials.getProperty(userKey + ".auth.type");
        if (authType == null) {
            throw new IllegalArgumentException("Authentication type not configured for user: " + username);
        }

        AuthCredentials auth = new AuthCredentials(authType);
        auth.setUsername(username); // Use the provided username, not the one from config

        if ("password".equals(authType)) {
            auth.setPassword(credentials.getProperty(userKey + ".password"));
        } else if ("publickey".equals(authType)) {
            String privateKeyPath = resolvePath(credentials.getProperty(userKey + ".privateKey"));
            String publicKeyPath = resolvePath(credentials.getProperty(userKey + ".publicKey"));
            auth.setPrivateKeyPath(privateKeyPath);
            auth.setPublicKeyPath(publicKeyPath);
        } else if ("dual".equals(authType)) {
            // For dual authentication, load both password and keys
            auth.setPassword(credentials.getProperty(userKey + ".password"));
            String privateKeyPath = resolvePath(credentials.getProperty(userKey + ".privateKey"));
            String publicKeyPath = resolvePath(credentials.getProperty(userKey + ".publicKey"));
            auth.setPrivateKeyPath(privateKeyPath);
            auth.setPublicKeyPath(publicKeyPath);
        }

        return auth;
    }

    /**
     * Find the user key that corresponds to a given username.
     */
    private String findUserKeyByUsername(String username) {
        // Check if the username is directly a user key
        if (credentials.containsKey(username + ".auth.type")) {
            return username;
        }
        
        // Search through all user keys to find one that matches the username
        for (String key : credentials.stringPropertyNames()) {
            if (key.endsWith(".username")) {
                String userKey = key.substring(0, key.indexOf("."));
                String configUsername = credentials.getProperty(key);
                if (username.equals(configUsername)) {
                    return userKey;
                }
            }
        }
        
        return null;
    }

    /**
     * Resolve a path relative to the project root directory.
     */
    private String resolvePath(String relativePath) {
        if (relativePath == null || relativePath.trim().isEmpty()) {
            Logger.error("resolvePath: null or empty path");
            return null;
        }
        
        // Try to find the project root by looking for the config directory
        String currentDir = System.getProperty("user.dir");
        String projectRoot = findProjectRoot(currentDir);
        
        String resolvedPath;
        if (projectRoot != null) {
            resolvedPath = projectRoot + File.separator + relativePath;
        } else {
            // Fallback to current directory
            resolvedPath = currentDir + File.separator + relativePath;
        }
        
        Logger.info("resolvePath: " + relativePath + " -> " + resolvedPath);
        Logger.info("  currentDir: " + currentDir);
        Logger.info("  projectRoot: " + projectRoot);
        
        // Check if file exists
        File file = new File(resolvedPath);
        if (!file.exists()) {
            Logger.error("resolvePath: File does not exist: " + resolvedPath);
        } else {
            Logger.info("resolvePath: File exists: " + resolvedPath);
        }
        
        return resolvedPath;
    }

    /**
     * Find the project root directory by looking for the config directory.
     */
    private String findProjectRoot(String startDir) {
        File current = new File(startDir);
        while (current != null) {
            File configDir = new File(current, "config");
            if (configDir.exists() && configDir.isDirectory()) {
                return current.getAbsolutePath();
            }
            current = current.getParentFile();
        }
        return null;
    }

    /**
     * Get a list of available users.
     */
    public String[] getAvailableUsers() {
        return credentials.stringPropertyNames().stream()
                .filter(key -> key.endsWith(".auth.type"))
                .map(key -> {
                    String userKey = key.substring(0, key.indexOf("."));
                    // Get the actual username for this user key
                    String username = credentials.getProperty(userKey + ".username");
                    return username != null ? username : userKey;
                })
                .distinct()
                .toArray(String[]::new);
    }

    /**
     * Get the credentials file path.
     */
    public String getCredentialsFilePath() {
        return DEFAULT_CREDENTIALS_FILE;
    }

    /**
     * Add a new user to the credentials file.
     */
    public void addUser(String username, String password) {
        // Create a unique user key
        String userKey = username;
        
        // Set the user properties
        credentials.setProperty(userKey + ".username", username);
        credentials.setProperty(userKey + ".password", password);
        credentials.setProperty(userKey + ".auth.type", "dual"); // Use dual auth by default
        
        // Set up key paths
        String privateKeyPath = "data/client/client_keys/" + username + "_rsa";
        String publicKeyPath = "data/client/client_keys/" + username + "_rsa.pub";
        credentials.setProperty(userKey + ".privateKey", privateKeyPath);
        credentials.setProperty(userKey + ".publicKey", publicKeyPath);
        
        Logger.info("Added user to credentials: " + username);
    }

    /**
     * Save credentials to the file.
     */
    public void saveCredentials() throws IOException {
        File credentialsFile = new File(DEFAULT_CREDENTIALS_FILE);
        credentialsFile.getParentFile().mkdirs(); // Create parent directories if they don't exist
        
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(credentialsFile)) {
            credentials.store(fos, "SSH Client Credentials");
            Logger.info("Credentials saved to: " + credentialsFile.getAbsolutePath());
        }
    }

    /**
     * Remove a user from the credentials file.
     */
    public void removeUser(String username) {
        // Find the user key that corresponds to this username
        String userKey = findUserKeyByUsername(username);
        if (userKey == null) {
            Logger.warn("User not found in credentials: " + username);
            return;
        }
        
        // Remove all properties for this user
        credentials.remove(userKey + ".username");
        credentials.remove(userKey + ".password");
        credentials.remove(userKey + ".auth.type");
        credentials.remove(userKey + ".privateKey");
        credentials.remove(userKey + ".publicKey");
        
        Logger.info("Removed user from credentials: " + username);
    }
} 