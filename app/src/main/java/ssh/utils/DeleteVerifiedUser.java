package ssh.utils;

import java.io.File;
import java.security.PublicKey;

import ssh.shared_model.auth.*;
import ssh.shared_model.crypto.RSAKeyGenerator;
import ssh.utils.Logger;

/**
 * Utility to delete verified users with all necessary data.
 * This removes users from server, client credentials, and deletes SSH keys.
 */
public class DeleteVerifiedUser {
    
    /**
     * Delete a verified user with all necessary data.
     */
    public static void deleteUser(String username) {
        Logger.info("Deleting verified user: " + username);
        
        try {
            // Step 1: Remove user from server database
            Logger.info("  Removing user from server database: " + username);
            UserStore userStore = new UserStore("data/server/users.properties", "data/server/authorized_keys");
            userStore.removeUser(username);
            userStore.saveUsers();
            
            // Step 2: Remove authorized keys from server for the user
            Logger.info("  Removing authorized keys from server for user: " + username);
            removeAuthorizedKeysFromServer(username, "data/server/authorized_keys");
            
            // Step 3: Remove user from client credentials
            Logger.info("  Removing user from client credentials: " + username);
            removeUserFromClientCredentials(username, "config/credentials.properties");
            
            // Step 4: Delete user's SSH keys
            Logger.info("  Deleting user's SSH keys: " + username);
            deleteUserKeys(username, "data/client/client_keys");
            
            // Step 5: Validate the deletion
            Logger.info("  Validating user deletion...");
            boolean isValid = validateUserDeletion(username);
            
            if (isValid) {
                Logger.info("  ✓ User '" + username + "' deleted successfully!");
                Logger.info("  ✓ User removed from server database");
                Logger.info("  ✓ Authorized keys removed from server");
                Logger.info("  ✓ User removed from client credentials");
                Logger.info("  ✓ SSH keys deleted");
            } else {
                Logger.error("  ✗ User deletion failed validation");
            }
            
        } catch (Exception e) {
            Logger.error("Failed to delete user: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Remove user from server's user database.
     */
    private static void removeUserFromServer(String username, String serverUsersFile) throws Exception {
        UserStore userStore = new UserStore(serverUsersFile, "data/server/authorized_keys");
        userStore.removeUser(username);
        userStore.saveUsers();
    }
    
    /**
     * Remove user's authorized keys from server.
     */
    private static void removeAuthorizedKeysFromServer(String username, String serverKeysDir) throws Exception {
        File userKeyDir = new File(serverKeysDir, username);
        if (userKeyDir.exists() && userKeyDir.isDirectory()) {
            deleteDirectory(userKeyDir);
            Logger.info("    Recursively deleted user key directory: " + username);
        } else {
            Logger.info("    No authorized keys found for user: " + username);
        }
    }
    
    private static void deleteDirectory(File dir) {
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
     * Remove user from client credentials file.
     */
    private static void removeUserFromClientCredentials(String username, String credentialsFile) throws Exception {
        CredentialsManager credentialsManager = new CredentialsManager(credentialsFile);
        credentialsManager.removeUser(username);
        credentialsManager.saveCredentials();
    }
    
    /**
     * Delete user's SSH keys.
     */
    private static void deleteUserKeys(String username, String clientKeysDir) throws Exception {
        // Delete private key
        File privateKeyFile = new File(clientKeysDir, username + "_rsa");
        if (privateKeyFile.exists()) {
            if (privateKeyFile.delete()) {
                Logger.info("    Deleted private key: " + username + "_rsa");
            } else {
                Logger.warn("    Warning: Could not delete private key: " + username + "_rsa");
            }
        } else {
            Logger.info("    Private key not found: " + username + "_rsa");
        }
        
        // Delete public key
        File publicKeyFile = new File(clientKeysDir, username + "_rsa.pub");
        if (publicKeyFile.exists()) {
            if (publicKeyFile.delete()) {
                Logger.info("    Deleted public key: " + username + "_rsa.pub");
            } else {
                Logger.warn("    Warning: Could not delete public key: " + username + "_rsa.pub");
            }
        } else {
            Logger.info("    Public key not found: " + username + "_rsa.pub");
        }
        
        // Check if any keys remain for this user
        File userKeyDir = new File(clientKeysDir);
        if (userKeyDir.exists() && userKeyDir.isDirectory()) {
            File[] remainingKeys = userKeyDir.listFiles((dir, name) -> name.startsWith(username + "_"));
            if (remainingKeys == null || remainingKeys.length == 0) {
                Logger.info("    No SSH keys found for user: " + username);
            }
        }
    }
    
    /**
     * Validate that the user deletion is complete.
     */
    private static boolean validateUserDeletion(String username) {
        try {
            // 1. Check if user still exists in server database
            UserStore userStore = new UserStore("data/server/users.properties", "data/server/authorized_keys");
            boolean userExists = userStore.userExists(username);
            if (userExists) {
                Logger.error("  ✗ User still exists in server database");
                return false;
            }
            
            // 2. Check if user still exists in client credentials
            CredentialsManager credentialsManager = new CredentialsManager("config/credentials.properties");
            String[] availableUsers = credentialsManager.getAvailableUsers();
            for (String user : availableUsers) {
                if (username.equals(user)) {
                    Logger.error("  ✗ User still exists in client credentials");
                    return false;
                }
            }
            
            // 3. Check if SSH keys still exist
            String privateKeyPath = "data/client/client_keys" + File.separator + username + "_rsa";
            String publicKeyPath = "data/client/client_keys" + File.separator + username + "_rsa.pub";
            File privateKeyFile = new File(privateKeyPath);
            File publicKeyFile = new File(publicKeyPath);
            
            if (privateKeyFile.exists() || publicKeyFile.exists()) {
                Logger.error("  ✗ SSH keys still exist");
                return false;
            }
            
            return true;
            
        } catch (Exception e) {
            Logger.error("  ✗ Validation error: " + e.getMessage());
            return false;
        }
    }
} 