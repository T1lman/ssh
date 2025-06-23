package ssh.model.utils;

import ssh.model.crypto.RSAKeyGenerator;
import ssh.model.utils.Logger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.security.KeyPair;
import java.security.PublicKey;

/**
 * Utility class for managing SSH keys for public key authentication.
 */
public class KeyManager {
    
    /**
     * Generate a new RSA key pair and save it to the specified directory.
     */
    public static void generateKeyPair(String keyName, String outputDirectory) throws Exception {
        Logger.info("Generating new RSA key pair: " + keyName);
        
        // Create output directory if it doesn't exist
        File dir = new File(outputDirectory);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        
        // Generate key pair
        KeyPair keyPair = RSAKeyGenerator.generateKeyPair();
        
        // Save keys
        String privateKeyPath = outputDirectory + File.separator + keyName;
        String publicKeyPath = outputDirectory + File.separator + keyName + ".pub";
        
        RSAKeyGenerator.saveKeyPair(keyPair, privateKeyPath, publicKeyPath);
        
        Logger.info("Key pair generated successfully:");
        Logger.info("  Private key: " + privateKeyPath);
        Logger.info("  Public key: " + publicKeyPath);
    }
    
    /**
     * Add a public key to a user's authorized keys on the server.
     */
    public static void addAuthorizedKey(String username, String publicKeyPath, String authorizedKeysDir) throws Exception {
        Logger.info("Adding authorized key for user: " + username);
        
        // Load the public key
        PublicKey publicKey = RSAKeyGenerator.loadPublicKey(publicKeyPath);
        
        // Create user's authorized keys directory
        File userKeyDir = new File(authorizedKeysDir, username);
        userKeyDir.mkdirs();
        
        // Save the public key
        String keyId = "key_" + System.currentTimeMillis();
        File keyFile = new File(userKeyDir, keyId + ".pub");
        
        try (FileWriter writer = new FileWriter(keyFile)) {
            writer.write(RSAKeyGenerator.getPublicKeyString(publicKey));
        }
        
        Logger.info("Authorized key added successfully: " + keyFile.getAbsolutePath());
    }
    
    /**
     * List all authorized keys for a user.
     */
    public static void listAuthorizedKeys(String username, String authorizedKeysDir) {
        Logger.info("Listing authorized keys for user: " + username);
        
        File userKeyDir = new File(authorizedKeysDir, username);
        if (!userKeyDir.exists() || !userKeyDir.isDirectory()) {
            Logger.info("No authorized keys directory found for user: " + username);
            return;
        }
        
        File[] keyFiles = userKeyDir.listFiles((dir, name) -> name.endsWith(".pub"));
        if (keyFiles == null || keyFiles.length == 0) {
            Logger.info("No authorized keys found for user: " + username);
            return;
        }
        
        Logger.info("Authorized keys for " + username + ":");
        for (File keyFile : keyFiles) {
            Logger.info("  " + keyFile.getName());
        }
    }
    
    /**
     * Remove an authorized key for a user.
     */
    public static boolean removeAuthorizedKey(String username, String keyId, String authorizedKeysDir) {
        Logger.info("Removing authorized key for user: " + username + ", key: " + keyId);
        
        File userKeyDir = new File(authorizedKeysDir, username);
        File keyFile = new File(userKeyDir, keyId + ".pub");
        
        if (keyFile.exists()) {
            boolean deleted = keyFile.delete();
            if (deleted) {
                Logger.info("Authorized key removed successfully: " + keyFile.getAbsolutePath());
            } else {
                Logger.error("Failed to remove authorized key: " + keyFile.getAbsolutePath());
            }
            return deleted;
        } else {
            Logger.error("Authorized key not found: " + keyFile.getAbsolutePath());
            return false;
        }
    }
    
    /**
     * Get the public key fingerprint (for display purposes).
     */
    public static String getKeyFingerprint(String publicKeyPath) throws Exception {
        PublicKey publicKey = RSAKeyGenerator.loadPublicKey(publicKeyPath);
        String keyString = RSAKeyGenerator.getPublicKeyString(publicKey);
        
        // Simple fingerprint - in a real implementation, you'd use MD5 or SHA256
        return keyString.substring(0, Math.min(16, keyString.length())) + "...";
    }
    
    /**
     * Validate that a key pair is valid and can be used for authentication.
     */
    public static boolean validateKeyPair(String privateKeyPath, String publicKeyPath) {
        try {
            KeyPair keyPair = RSAKeyGenerator.loadKeyPair(privateKeyPath, publicKeyPath);
            
            // Test signing and verification
            String testData = "test";
            byte[] signature = RSAKeyGenerator.sign(testData.getBytes(), keyPair.getPrivate());
            boolean valid = RSAKeyGenerator.verify(testData.getBytes(), signature, keyPair.getPublic());
            
            Logger.info("Key pair validation: " + (valid ? "PASSED" : "FAILED"));
            return valid;
            
        } catch (Exception e) {
            Logger.error("Key pair validation failed: " + e.getMessage());
            return false;
        }
    }
} 