package ssh.model.utils;

import ssh.model.crypto.RSAKeyGenerator;
import ssh.model.utils.Logger;
import ssh.model.auth.*;

import java.io.File;
import java.security.KeyPair;
import java.security.PublicKey;

/**
 * Utility to create new verified users with all necessary data.
 * This generates key pairs, adds users to server, and configures client credentials.
 */
public class CreateVerifiedUser {
    
    /**
     * Create a new verified user with all necessary data.
     */
    public static void createUser(String username, String password) {
        Logger.info("Creating new verified user: " + username);
        
        try {
            // Step 1: Generate key pair for the user
            Logger.info("  Generating key pair for user: " + username);
            String keyName = username + "_rsa";
            String clientKeysDir = "data/client/client_keys";
            KeyManager.generateKeyPair(keyName, clientKeysDir);
            
            // Step 2: Add user to server database
            Logger.info("  Adding user to server database: " + username);
            UserStore userStore = new UserStore("data/server/users.properties", "data/server/authorized_keys");
            userStore.addUser(username, password); // Use provided password
            userStore.saveUsers(); // Save to disk immediately
            
            // Step 3: Add public key to server for the user
            Logger.info("  Adding public key to server for user: " + username);
            String publicKeyPath = clientKeysDir + "/" + keyName + ".pub";
            String serverKeysDir = "data/server/authorized_keys";
            KeyManager.addAuthorizedKey(username, publicKeyPath, serverKeysDir);
            
            // Step 4: Add user to client credentials
            Logger.info("  Adding user to client credentials: " + username);
            CredentialsManager credentialsManager = new CredentialsManager("config/credentials.properties");
            credentialsManager.addUser(username, password); // Use provided password
            Logger.info("  User added to credentials manager, saving to disk...");
            try {
                credentialsManager.saveCredentials(); // Save to disk immediately
                Logger.info("  Credentials saved successfully");
            } catch (Exception e) {
                Logger.error("  Failed to save credentials: " + e.getMessage());
                throw e;
            }
            
            // Step 5: Validate the setup
            Logger.info("  Validating user setup...");
            // Small delay to ensure file system sync
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            boolean isValid = validateUserSetup(username);
            
            if (isValid) {
                Logger.info("  ✓ User '" + username + "' created successfully!");
                Logger.info("  ✓ Key pair generated and validated");
                Logger.info("  ✓ User added to server database");
                Logger.info("  ✓ Public key authorized on server");
                Logger.info("  ✓ User added to client credentials");
            } else {
                Logger.error("  ✗ User creation failed validation");
            }
            
        } catch (Exception e) {
            Logger.error("Failed to create user: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Validate that the user setup is complete and working.
     */
    private static boolean validateUserSetup(String username) {
        try {
            // Validate key pair
            String privateKeyPath = "data/client/client_keys/" + username + "_rsa";
            String publicKeyPath = "data/client/client_keys/" + username + "_rsa.pub";
            
            if (!KeyManager.validateKeyPair(privateKeyPath, publicKeyPath)) {
                Logger.error("  ✗ Key pair validation failed");
                return false;
            }
            
            // Validate user exists in server database
            UserStore userStore = new UserStore("data/server/users.properties", "data/server/authorized_keys");
            if (!userStore.userExists(username)) {
                Logger.error("  ✗ User not found in server database");
                return false;
            }
            
            // Validate authorized keys exist
            String authorizedKeysDir = "data/server/authorized_keys/" + username;
            java.io.File keysDir = new java.io.File(authorizedKeysDir);
            if (!keysDir.exists() || keysDir.listFiles() == null || keysDir.listFiles().length == 0) {
                Logger.error("  ✗ No authorized keys found for user");
                return false;
            }
            
            // Validate user exists in client credentials
            CredentialsManager credentialsManager = new CredentialsManager("config/credentials.properties");
            String[] availableUsers = credentialsManager.getAvailableUsers();
            Logger.info("  Available users in credentials: " + String.join(", ", availableUsers));
            boolean foundInClient = false;
            for (String user : availableUsers) {
                if (username.equals(user)) {
                    foundInClient = true;
                    break;
                }
            }
            if (!foundInClient) {
                Logger.error("  ✗ User not found in client credentials");
                return false;
            }
            
            return true;
            
        } catch (Exception e) {
            Logger.error("  ✗ Validation error: " + e.getMessage());
            return false;
        }
    }
} 