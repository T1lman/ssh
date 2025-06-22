package ssh.utils;

import ssh.crypto.RSAKeyGenerator;
import ssh.utils.Logger;

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
    public static void createUser(String username, String password) throws Exception {
        createUser(username, password, null);
    }

    /**
     * Create a new verified user with all necessary data and optionally reload server.
     */
    public static void createUser(String username, String password, ssh.client.SSHClient client) throws Exception {
        System.out.println("Creating new verified user: " + username);
        
        // Create directories if they don't exist
        String clientKeysDir = "data/client/client_keys";
        String serverKeysDir = "data/server/authorized_keys";
        String serverUsersFile = "data/server/users.properties";
        String clientCredentialsFile = "config/credentials.properties";
        
        File clientDir = new File(clientKeysDir);
        File serverDir = new File(serverKeysDir);
        File serverUsersDir = new File(serverUsersFile).getParentFile();
        
        clientDir.mkdirs();
        serverDir.mkdirs();
        serverUsersDir.mkdirs();
        
        // 1. Generate key pair for the user
        System.out.println("  Generating key pair for user: " + username);
        String keyName = username + "_rsa";
        String privateKeyPath = clientKeysDir + File.separator + keyName;
        String publicKeyPath = clientKeysDir + File.separator + keyName + ".pub";
        
        KeyManager.generateKeyPair(keyName, clientKeysDir);
        
        // 2. Add user to server's user database
        System.out.println("  Adding user to server database: " + username);
        addUserToServer(username, password, serverUsersFile);
        
        // 3. Add public key to server's authorized keys
        System.out.println("  Adding public key to server for user: " + username);
        KeyManager.addAuthorizedKey(username, publicKeyPath, serverKeysDir);
        
        // 4. Add user to client credentials
        System.out.println("  Adding user to client credentials: " + username);
        addUserToClientCredentials(username, password, clientCredentialsFile);
        
        // 5. Reload server's user database if client is provided
        if (client != null) {
            System.out.println("  Reloading server's user database...");
            try {
                client.reloadServerUsers();
                System.out.println("  ✓ Server user database reloaded successfully");
            } catch (Exception e) {
                System.out.println("  ⚠ Warning: Failed to reload server user database: " + e.getMessage());
                System.out.println("  ⚠ You may need to restart the server for the new user to be available");
            }
        }
        
        // 6. Validate the setup
        System.out.println("  Validating user setup...");
        boolean isValid = validateUserSetup(username, privateKeyPath, publicKeyPath, serverUsersFile);
        
        if (isValid) {
            System.out.println("  ✓ User '" + username + "' created successfully!");
            System.out.println("  ✓ Key pair generated and validated");
            System.out.println("  ✓ User added to server database");
            System.out.println("  ✓ Public key authorized on server");
            System.out.println("  ✓ User added to client credentials");
            if (client != null) {
                System.out.println("  ✓ Server user database reloaded");
            }
        } else {
            throw new Exception("User setup validation failed for " + username);
        }
    }
    
    /**
     * Add a user to the server's user database.
     */
    private static void addUserToServer(String username, String password, String serverUsersFile) throws Exception {
        // Create a temporary UserStore to add the user
        ssh.auth.UserStore userStore = new ssh.auth.UserStore(serverUsersFile, "data/server/authorized_keys");
        userStore.addUser(username, password);
        userStore.saveUsers();
    }
    
    /**
     * Add a user to the client credentials file.
     */
    private static void addUserToClientCredentials(String username, String password, String credentialsFile) throws Exception {
        CredentialsManager credentialsManager = new CredentialsManager(credentialsFile);
        credentialsManager.addUser(username, password);
        credentialsManager.saveCredentials();
    }
    
    /**
     * Validate that the user setup is complete and working.
     */
    private static boolean validateUserSetup(String username, String privateKeyPath, String publicKeyPath, String serverUsersFile) {
        try {
            // 1. Validate key pair
            boolean keyPairValid = KeyManager.validateKeyPair(privateKeyPath, publicKeyPath);
            if (!keyPairValid) {
                System.out.println("  ✗ Key pair validation failed");
                return false;
            }
            
            // 2. Check if user exists in server database
            ssh.auth.UserStore userStore = new ssh.auth.UserStore(serverUsersFile, "data/server/authorized_keys");
            boolean userExists = userStore.userExists(username);
            if (!userExists) {
                System.out.println("  ✗ User not found in server database");
                return false;
            }
            
            // 3. Check if public key is authorized
            java.util.List<java.security.PublicKey> authorizedKeys = userStore.getAuthorizedKeys(username);
            if (authorizedKeys.isEmpty()) {
                System.out.println("  ✗ No authorized keys found for user");
                return false;
            }
            
            // 4. Check if user exists in client credentials
            CredentialsManager credentialsManager = new CredentialsManager("config/credentials.properties");
            String[] availableUsers = credentialsManager.getAvailableUsers();
            boolean foundInClient = false;
            for (String user : availableUsers) {
                if (username.equals(user)) {
                    foundInClient = true;
                    break;
                }
            }
            if (!foundInClient) {
                System.out.println("  ✗ User not found in client credentials");
                return false;
            }
            
            return true;
            
        } catch (Exception e) {
            System.out.println("  ✗ Validation error: " + e.getMessage());
            return false;
        }
    }
} 