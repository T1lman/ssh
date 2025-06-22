package ssh.utils;

import ssh.crypto.RSAKeyGenerator;
import ssh.utils.Logger;

import java.io.File;
import java.security.KeyPair;
import java.security.PublicKey;

/**
 * Utility to set up dual authentication for all users.
 * This generates key pairs and configures the server for dual auth.
 */
public class SetupDualAuth {
    
    public static void main(String[] args) {
        System.out.println("Setting up dual authentication for SSH users...");
        
        try {
            // Create client keys directory
            String clientKeysDir = "data/client/client_keys";
            File clientDir = new File(clientKeysDir);
            clientDir.mkdirs();
            
            // Create server authorized keys directory
            String serverKeysDir = "data/server/authorized_keys";
            File serverDir = new File(serverKeysDir);
            serverDir.mkdirs();
            
            // Users to set up (from credentials.properties)
            String[] users = {"admin", "test", "user1"};
            
            for (String user : users) {
                System.out.println("\nSetting up dual authentication for user: " + user);
                
                // Generate key pair
                String keyName = user + "_rsa";
                String privateKeyPath = clientKeysDir + File.separator + keyName;
                String publicKeyPath = clientKeysDir + File.separator + keyName + ".pub";
                
                System.out.println("  Generating key pair: " + keyName);
                KeyManager.generateKeyPair(keyName, clientKeysDir);
                
                // Add public key to server
                System.out.println("  Adding public key to server for user: " + user);
                KeyManager.addAuthorizedKey(user, publicKeyPath, serverKeysDir);
                
                // Validate the key pair
                System.out.println("  Validating key pair...");
                boolean isValid = KeyManager.validateKeyPair(privateKeyPath, publicKeyPath);
                if (isValid) {
                    System.out.println("  ✓ Key pair validated successfully");
                } else {
                    System.out.println("  ✗ Key pair validation failed");
                }
            }
            
            System.out.println("\n✓ Dual authentication setup completed successfully!");
            System.out.println("\nNext steps:");
            System.out.println("1. Start the server: java -cp app/build/classes/java/main ssh.server.SSHServer");
            System.out.println("2. Start the client: ./gradlew run");
            System.out.println("3. Select a user and use dual authentication");
            
        } catch (Exception e) {
            System.err.println("Error setting up dual authentication: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Set up dual authentication for a specific user.
     */
    public static void setupUser(String username, String clientKeysDir, String serverKeysDir) throws Exception {
        System.out.println("Setting up dual authentication for user: " + username);
        
        // Generate key pair
        String keyName = username + "_rsa";
        KeyManager.generateKeyPair(keyName, clientKeysDir);
        
        // Add public key to server
        String publicKeyPath = clientKeysDir + File.separator + keyName + ".pub";
        KeyManager.addAuthorizedKey(username, publicKeyPath, serverKeysDir);
        
        // Validate
        String privateKeyPath = clientKeysDir + File.separator + keyName;
        boolean isValid = KeyManager.validateKeyPair(privateKeyPath, publicKeyPath);
        
        if (isValid) {
            System.out.println("✓ Dual authentication setup completed for " + username);
        } else {
            throw new Exception("Key pair validation failed for " + username);
        }
    }
} 