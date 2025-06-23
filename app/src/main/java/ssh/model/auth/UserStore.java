package ssh.model.auth;

import ssh.model.crypto.RSAKeyGenerator;
import ssh.model.utils.Logger;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Manages user credentials and authorized keys.
 */
public class UserStore {
    private Properties userProperties;
    private String userFilePath;
    private String authorizedKeysDir;

    public UserStore(String userFilePath, String authorizedKeysDir) {
        this.userFilePath = userFilePath;
        this.authorizedKeysDir = authorizedKeysDir;
        this.userProperties = new Properties();
        loadUsers();
    }

    /**
     * Load users from the properties file.
     */
    public void loadUsers() {
        try {
            File file = new File(userFilePath);
            if (file.exists()) {
                try (FileInputStream fis = new FileInputStream(file)) {
                    userProperties.load(fis);
                }
            } else {
                // Create default users if file doesn't exist
                createDefaultUsers();
            }
        } catch (IOException e) {
            System.err.println("Error loading users: " + e.getMessage());
        }
    }

    /**
     * Create default users for testing.
     */
    private void createDefaultUsers() throws IOException {
        // Create admin user with password "admin"
        addUser("admin", "admin");
        
        // Create test user with password "test"
        addUser("test", "test");
        
        // Create user1 with password "password"
        addUser("user1", "password");
        
        saveUsers();
    }

    /**
     * Save users to the properties file.
     */
    public void saveUsers() throws IOException {
        File file = new File(userFilePath);
        file.getParentFile().mkdirs(); // Create parent directories if they don't exist
        
        try (FileOutputStream fos = new FileOutputStream(file)) {
            userProperties.store(fos, "SSH User Database");
        }
    }

    /**
     * Check if a user exists.
     */
    public boolean userExists(String username) {
        return userProperties.containsKey(username);
    }

    /**
     * Get the password hash for a user.
     */
    public String getUserPasswordHash(String username) {
        return userProperties.getProperty(username);
    }

    /**
     * Hash a password using SHA-256.
     */
    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Add a new user with a password.
     */
    public void addUser(String username, String password) {
        String hashedPassword = hashPassword(password);
        userProperties.setProperty(username, hashedPassword);
    }

    /**
     * Verify a user's password.
     */
    public boolean verifyPassword(String username, String password) {
        String storedHash = getUserPasswordHash(username);
        if (storedHash == null) {
            Logger.error("UserStore: No stored hash found for user: " + username);
            return false;
        }
        String inputHash = hashPassword(password);
        boolean matches = storedHash.equals(inputHash);
        Logger.info("UserStore: Password verification for " + username + 
                   " - Stored hash: " + storedHash + 
                   ", Input hash: " + inputHash + 
                   ", Matches: " + matches);
        return matches;
    }

    /**
     * Get authorized public keys for a user.
     */
    public List<PublicKey> getAuthorizedKeys(String username) {
        List<PublicKey> keys = new ArrayList<>();
        File userKeyDir = new File(authorizedKeysDir, username);
        
        if (!userKeyDir.exists() || !userKeyDir.isDirectory()) {
            return keys;
        }

        File[] keyFiles = userKeyDir.listFiles((dir, name) -> name.endsWith(".pub"));
        if (keyFiles == null) {
            return keys;
        }

        for (File keyFile : keyFiles) {
            try {
                PublicKey key = RSAKeyGenerator.loadPublicKey(keyFile.getAbsolutePath());
                keys.add(key);
            } catch (Exception e) {
                System.err.println("Error loading key " + keyFile.getName() + ": " + e.getMessage());
            }
        }

        return keys;
    }

    /**
     * Add an authorized key for a user.
     */
    public void addAuthorizedKey(String username, PublicKey publicKey) throws Exception {
        File userKeyDir = new File(authorizedKeysDir, username);
        userKeyDir.mkdirs();

        String keyString = RSAKeyGenerator.getPublicKeyString(publicKey);
        String keyId = "key_" + System.currentTimeMillis();
        File keyFile = new File(userKeyDir, keyId + ".pub");

        try (FileWriter writer = new FileWriter(keyFile)) {
            writer.write(keyString);
        }
    }

    /**
     * Remove an authorized key for a user.
     */
    public boolean removeAuthorizedKey(String username, String keyId) {
        File userKeyDir = new File(authorizedKeysDir, username);
        File keyFile = new File(userKeyDir, keyId + ".pub");
        
        if (keyFile.exists()) {
            return keyFile.delete();
        }
        return false;
    }

    /**
     * List all users.
     */
    public List<String> getAllUsers() {
        return new ArrayList<>(userProperties.stringPropertyNames());
    }

    /**
     * Remove a user.
     */
    public void removeUser(String username) {
        userProperties.remove(username);
        
        // Also remove user's authorized keys directory
        File userKeyDir = new File(authorizedKeysDir, username);
        if (userKeyDir.exists()) {
            deleteDirectory(userKeyDir);
        }
    }

    /**
     * Change a user's password.
     */
    public void changePassword(String username, String newPassword) {
        if (userExists(username)) {
            addUser(username, newPassword); // This will overwrite the existing password
        }
    }

    /**
     * Delete a directory and all its contents.
     */
    private void deleteDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        dir.delete();
    }

    /**
     * Get the user file path.
     */
    public String getUserFilePath() {
        return userFilePath;
    }

    /**
     * Get the authorized keys directory.
     */
    public String getAuthorizedKeysDir() {
        return authorizedKeysDir;
    }
} 