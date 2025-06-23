package ssh.model.utils;

import ssh.model.crypto.RSAKeyGenerator;
import ssh.model.utils.Logger;
import ssh.model.auth.*;

import java.io.File;
import java.security.PublicKey;

/**
 * Utility to delete verified users with all necessary data.
 * This removes users from server, client credentials, and deletes SSH keys.
 */
public class DeleteVerifiedUser {
    
    /**
     * Delete a verified user with all necessary data.
     */
    public static void deleteUser(String username) throws Exception {
        // Define paths
        String clientKeysDir = "data/client/client_keys";
        String serverKeysDir = "data/server/authorized_keys";
        String serverUsersFile = "data/server/users.properties";
        String clientCredentialsFile = "config/credentials.properties";
        
        // 1. Remove user from server's user database
        System.out.println("  Removing user from server database: " + username);
        removeUserFromServer(username, serverUsersFile);
        
        // 2. Remove user's authorized keys from server
        System.out.println("  Removing authorized keys from server for user: " + username);
        removeAuthorizedKeysFromServer(username, serverKeysDir);
        
        // 3. Remove user from client credentials
        System.out.println("  Removing user from client credentials: " + username);
        removeUserFromClientCredentials(username, clientCredentialsFile);
        
        // 4. Delete user's SSH keys
        System.out.println("  Deleting user's SSH keys: " + username);
        deleteUserKeys(username, clientKeysDir);
        
        // 5. Validate the deletion
        System.out.println("  Validating user deletion...");
        boolean isValid = validateUserDeletion(username, serverUsersFile, clientCredentialsFile, clientKeysDir);
        
        if (isValid) {
            System.out.println("  ✓ User '" + username + "' deleted successfully!");
            System.out.println("  ✓ User removed from server database");
            System.out.println("  ✓ Authorized keys removed from server");
            System.out.println("  ✓ User removed from client credentials");
            System.out.println("  ✓ SSH keys deleted");
        } else {
            throw new Exception("User deletion validation failed for " + username);
        }
    }
    
    /**
     * Remove a user from the server's user database.
     */
    private static void removeUserFromServer(String username, String serverUsersFile) throws Exception {
        // Create a temporary UserStore to remove the user
        ssh.model.auth.UserStore userStore = new ssh.model.auth.UserStore(serverUsersFile, "data/server/authorized_keys");
        userStore.removeUser(username);
        userStore.saveUsers();
    }
    
    /**
     * Remove authorized keys for a user from the server.
     */
    private static void removeAuthorizedKeysFromServer(String username, String serverKeysDir) throws Exception {
        File userKeyDir = new File(serverKeysDir, username);
        if (userKeyDir.exists() && userKeyDir.isDirectory()) {
            File[] keyFiles = userKeyDir.listFiles();
            if (keyFiles != null) {
                for (File keyFile : keyFiles) {
                    if (keyFile.delete()) {
                        System.out.println("    Deleted key file: " + keyFile.getName());
                    } else {
                        System.out.println("    Warning: Could not delete key file: " + keyFile.getName());
                    }
                }
            }
            // Remove the user directory
            if (userKeyDir.delete()) {
                System.out.println("    Deleted user key directory: " + username);
            } else {
                System.out.println("    Warning: Could not delete user key directory: " + username);
            }
        } else {
            System.out.println("    No authorized keys found for user: " + username);
        }
    }
    
    /**
     * Remove a user from the client credentials file.
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
        String privateKeyPath = clientKeysDir + File.separator + username + "_rsa";
        String publicKeyPath = clientKeysDir + File.separator + username + "_rsa.pub";
        
        File privateKeyFile = new File(privateKeyPath);
        File publicKeyFile = new File(publicKeyPath);
        
        boolean privateDeleted = false;
        boolean publicDeleted = false;
        
        if (privateKeyFile.exists()) {
            privateDeleted = privateKeyFile.delete();
            if (privateDeleted) {
                System.out.println("    Deleted private key: " + username + "_rsa");
            } else {
                System.out.println("    Warning: Could not delete private key: " + username + "_rsa");
            }
        } else {
            System.out.println("    Private key not found: " + username + "_rsa");
        }
        
        if (publicKeyFile.exists()) {
            publicDeleted = publicKeyFile.delete();
            if (publicDeleted) {
                System.out.println("    Deleted public key: " + username + "_rsa.pub");
            } else {
                System.out.println("    Warning: Could not delete public key: " + username + "_rsa.pub");
            }
        } else {
            System.out.println("    Public key not found: " + username + "_rsa.pub");
        }
        
        if (!privateDeleted && !publicDeleted) {
            System.out.println("    No SSH keys found for user: " + username);
        }
    }
    
    /**
     * Validate that the user deletion is complete.
     */
    private static boolean validateUserDeletion(String username, String serverUsersFile, String clientCredentialsFile, String clientKeysDir) {
        try {
            // 1. Check if user still exists in server database
            ssh.model.auth.UserStore userStore = new ssh.model.auth.UserStore(serverUsersFile, "data/server/authorized_keys");
            boolean userExists = userStore.userExists(username);
            if (userExists) {
                System.out.println("  ✗ User still exists in server database");
                return false;
            }
            
            // 2. Check if user still exists in client credentials
            CredentialsManager credentialsManager = new CredentialsManager(clientCredentialsFile);
            String[] availableUsers = credentialsManager.getAvailableUsers();
            for (String user : availableUsers) {
                if (username.equals(user)) {
                    System.out.println("  ✗ User still exists in client credentials");
                    return false;
                }
            }
            
            // 3. Check if SSH keys still exist
            String privateKeyPath = clientKeysDir + File.separator + username + "_rsa";
            String publicKeyPath = clientKeysDir + File.separator + username + "_rsa.pub";
            File privateKeyFile = new File(privateKeyPath);
            File publicKeyFile = new File(publicKeyPath);
            
            if (privateKeyFile.exists() || publicKeyFile.exists()) {
                System.out.println("  ✗ SSH keys still exist");
                return false;
            }
            
            return true;
            
        } catch (Exception e) {
            System.out.println("  ✗ Validation error: " + e.getMessage());
            return false;
        }
    }
} 