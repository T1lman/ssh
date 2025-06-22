package ssh.utils;

import ssh.crypto.RSAKeyGenerator;

import java.io.File;
import java.security.KeyPair;

/**
 * Console utility for managing SSH keys.
 * This provides command-line tools for key management.
 */
public class SSHKeyManager {
    
    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            return;
        }
        
        String command = args[0];
        
        try {
            switch (command) {
                case "generate":
                    handleGenerate(args);
                    break;
                case "add-to-server":
                    handleAddToServer(args);
                    break;
                case "list-keys":
                    handleListKeys(args);
                    break;
                case "validate":
                    handleValidate(args);
                    break;
                case "help":
                    printUsage();
                    break;
                default:
                    System.err.println("Unknown command: " + command);
                    printUsage();
                    break;
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void handleGenerate(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("Usage: generate <key-name> <output-directory>");
            return;
        }
        
        String keyName = args[1];
        String outputDir = args[2];
        
        System.out.println("Generating RSA key pair: " + keyName);
        KeyManager.generateKeyPair(keyName, outputDir);
        System.out.println("Key pair generated successfully!");
    }
    
    private static void handleAddToServer(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("Usage: add-to-server <username> <public-key-path> <authorized-keys-dir>");
            return;
        }
        
        String username = args[1];
        String publicKeyPath = args[2];
        String authorizedKeysDir = args[3];
        
        System.out.println("Adding public key to server for user: " + username);
        KeyManager.addAuthorizedKey(username, publicKeyPath, authorizedKeysDir);
        System.out.println("Public key added successfully!");
    }
    
    private static void handleListKeys(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("Usage: list-keys <username> <authorized-keys-dir>");
            return;
        }
        
        String username = args[1];
        String authorizedKeysDir = args[2];
        
        System.out.println("Listing authorized keys for user: " + username);
        KeyManager.listAuthorizedKeys(username, authorizedKeysDir);
    }
    
    private static void handleValidate(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("Usage: validate <private-key-path> <public-key-path>");
            return;
        }
        
        String privateKeyPath = args[1];
        String publicKeyPath = args[2];
        
        System.out.println("Validating key pair...");
        boolean isValid = KeyManager.validateKeyPair(privateKeyPath, publicKeyPath);
        
        if (isValid) {
            System.out.println("✓ Key pair is valid and ready for use");
        } else {
            System.out.println("✗ Key pair validation failed");
        }
    }
    
    private static void printUsage() {
        System.out.println("SSH Key Manager - Console Utility");
        System.out.println("=================================");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  generate <key-name> <output-dir>     Generate new RSA key pair");
        System.out.println("  add-to-server <user> <pub-key> <dir> Add public key to server");
        System.out.println("  list-keys <user> <dir>              List user's authorized keys");
        System.out.println("  validate <priv-key> <pub-key>       Validate key pair");
        System.out.println("  help                                Show this help message");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java ssh.utils.SSHKeyManager generate mykey data/client/keys");
        System.out.println("  java ssh.utils.SSHKeyManager add-to-server admin data/client/keys/mykey.pub data/server/authorized_keys");
        System.out.println("  java ssh.utils.SSHKeyManager list-keys admin data/server/authorized_keys");
        System.out.println("  java ssh.utils.SSHKeyManager validate data/client/keys/mykey data/client/keys/mykey.pub");
    }
} 