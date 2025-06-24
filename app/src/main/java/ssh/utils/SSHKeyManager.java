package ssh.utils;

import java.io.File;
import java.security.KeyPair;

import ssh.shared_model.crypto.RSAKeyGenerator;
import ssh.utils.Logger;

/**
 * Console utility for managing SSH keys.
 * This provides command-line tools for key management.
 */
public class SSHKeyManager {
    
    public static void main(String[] args) {
        if (args.length == 0) {
            showHelp();
            return;
        }
        
        String command = args[0];
        
        try {
            switch (command) {
                case "generate":
                    if (args.length != 3) {
                        Logger.error("Usage: generate <key-name> <output-directory>");
                        return;
                    }
                    String keyName = args[1];
                    String outputDir = args[2];
                    Logger.info("Generating RSA key pair: " + keyName);
                    KeyManager.generateKeyPair(keyName, outputDir);
                    Logger.info("Key pair generated successfully!");
                    break;
                case "add-to-server":
                    if (args.length != 4) {
                        Logger.error("Usage: add-to-server <username> <public-key-path> <authorized-keys-dir>");
                        return;
                    }
                    String username = args[1];
                    String publicKeyPath = args[2];
                    String authorizedKeysDir = args[3];
                    Logger.info("Adding public key to server for user: " + username);
                    KeyManager.addAuthorizedKey(username, publicKeyPath, authorizedKeysDir);
                    Logger.info("Public key added successfully!");
                    break;
                case "list-keys":
                    if (args.length != 3) {
                        Logger.error("Usage: list-keys <username> <authorized-keys-dir>");
                        return;
                    }
                    String listUsername = args[1];
                    String listKeysDir = args[2];
                    Logger.info("Listing authorized keys for user: " + listUsername);
                    KeyManager.listAuthorizedKeys(listUsername, listKeysDir);
                    break;
                case "validate":
                    if (args.length != 3) {
                        Logger.error("Usage: validate <private-key-path> <public-key-path>");
                        return;
                    }
                    String privateKeyPath = args[1];
                    String validatePublicKeyPath = args[2];
                    Logger.info("Validating key pair...");
                    boolean isValid = KeyManager.validateKeyPair(privateKeyPath, validatePublicKeyPath);
                    if (isValid) {
                        Logger.info("✓ Key pair is valid and ready for use");
                    } else {
                        Logger.error("✗ Key pair validation failed");
                    }
                    break;
                case "help":
                    showHelp();
                    break;
                default:
                    Logger.error("Unknown command: " + command);
                    showHelp();
                    break;
            }
        } catch (Exception e) {
            Logger.error("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void showHelp() {
        Logger.info("SSH Key Manager - Console Utility");
        Logger.info("=================================");
        Logger.info("");
        Logger.info("Commands:");
        Logger.info("  generate <key-name> <output-dir>     Generate new RSA key pair");
        Logger.info("  add-to-server <user> <pub-key> <dir> Add public key to server");
        Logger.info("  list-keys <user> <dir>              List user's authorized keys");
        Logger.info("  validate <priv-key> <pub-key>       Validate key pair");
        Logger.info("  help                                Show this help message");
        Logger.info("");
        Logger.info("Examples:");
        Logger.info("  java ssh.utils.SSHKeyManager generate mykey data/client/keys");
        Logger.info("  java ssh.utils.SSHKeyManager add-to-server admin data/client/keys/mykey.pub data/server/authorized_keys");
        Logger.info("  java ssh.utils.SSHKeyManager list-keys admin data/server/authorized_keys");
        Logger.info("  java ssh.utils.SSHKeyManager validate data/client/keys/mykey data/client/keys/mykey.pub");
    }
} 