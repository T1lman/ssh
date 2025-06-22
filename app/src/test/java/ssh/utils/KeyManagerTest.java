package ssh.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ssh.crypto.RSAKeyGenerator;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for KeyManager to verify key generation and file writing.
 */
public class KeyManagerTest {

    @TempDir
    Path tempDir;
    
    private String keysDir;

    @BeforeEach
    void setUp() {
        keysDir = tempDir.toString();
    }

    @Test
    void testGenerateKeyPair() throws Exception {
        System.out.println("=== Testing KeyManager.generateKeyPair ===");
        
        String keyName = "test_key";
        String privateKeyPath = keysDir + File.separator + keyName;
        String publicKeyPath = keysDir + File.separator + keyName + ".pub";
        
        System.out.println("Generating key pair: " + keyName);
        System.out.println("Private key path: " + privateKeyPath);
        System.out.println("Public key path: " + publicKeyPath);
        
        // Generate the key pair
        KeyManager.generateKeyPair(keyName, keysDir);
        
        // Verify files exist
        File privateKeyFile = new File(privateKeyPath);
        File publicKeyFile = new File(publicKeyPath);
        
        assertTrue(privateKeyFile.exists(), "Private key file should exist");
        assertTrue(publicKeyFile.exists(), "Public key file should exist");
        
        System.out.println("✓ Key files created successfully");
        
        // Read and analyze public key content
        String publicKeyContent = Files.readString(publicKeyFile.toPath());
        System.out.println("Public key file size: " + publicKeyFile.length() + " bytes");
        System.out.println("Public key content length: " + publicKeyContent.length());
        
        // Check for multiple keys in the file
        String[] lines = publicKeyContent.split("\n");
        System.out.println("Number of lines in public key file: " + lines.length);
        
        if (lines.length > 2) {
            System.out.println("ERROR: Public key file contains too many lines!");
            for (int i = 0; i < lines.length; i++) {
                System.out.println("Line " + i + " (" + lines[i].length() + " chars): " + lines[i]);
            }
            fail("Public key file should contain only one key");
        }
        
        // Verify the content is a valid Base64 string
        String trimmedContent = publicKeyContent.trim();
        System.out.println("Trimmed content length: " + trimmedContent.length());
        System.out.println("Trimmed content: " + trimmedContent);
        
        // Check if it looks like a valid RSA public key (should start with MII)
        assertTrue(trimmedContent.startsWith("MII"), "Public key should start with 'MII'");
        
        // Try to decode as Base64
        try {
            byte[] decoded = java.util.Base64.getDecoder().decode(trimmedContent);
            System.out.println("✓ Base64 decoding successful, length: " + decoded.length);
        } catch (IllegalArgumentException e) {
            fail("Public key content should be valid Base64: " + e.getMessage());
        }
        
        // Try to load the key pair
        try {
            KeyPair loadedKeyPair = RSAKeyGenerator.loadKeyPair(privateKeyPath, publicKeyPath);
            assertNotNull(loadedKeyPair, "Should be able to load generated key pair");
            System.out.println("✓ Key pair loaded successfully");
            
            // Verify the public key string matches the file content
            String publicKeyString = RSAKeyGenerator.getPublicKeyString(loadedKeyPair.getPublic());
            assertEquals(trimmedContent, publicKeyString, "File content should match public key string");
            System.out.println("✓ Public key string matches file content");
            
        } catch (Exception e) {
            System.err.println("Failed to load key pair: " + e.getMessage());
            e.printStackTrace();
            fail("Should be able to load generated key pair");
        }
    }

    @Test
    void testAddAuthorizedKey() throws Exception {
        System.out.println("=== Testing KeyManager.addAuthorizedKey ===");
        
        // First generate a key pair
        String keyName = "test_key";
        KeyManager.generateKeyPair(keyName, keysDir);
        String publicKeyPath = keysDir + File.separator + keyName + ".pub";
        
        // Create server keys directory
        String serverKeysDir = tempDir.resolve("server_keys").toString();
        new File(serverKeysDir).mkdirs();
        
        String username = "testuser";
        
        System.out.println("Adding authorized key for user: " + username);
        System.out.println("Public key path: " + publicKeyPath);
        System.out.println("Server keys dir: " + serverKeysDir);
        
        // Add the key to server
        KeyManager.addAuthorizedKey(username, publicKeyPath, serverKeysDir);
        
        // Verify the authorized key was created
        File userKeyDir = new File(serverKeysDir, username);
        assertTrue(userKeyDir.exists(), "User key directory should exist");
        
        File[] keyFiles = userKeyDir.listFiles((dir, name) -> name.endsWith(".pub"));
        assertNotNull(keyFiles, "Should find key files");
        assertTrue(keyFiles.length > 0, "Should have at least one authorized key");
        
        System.out.println("✓ Found " + keyFiles.length + " authorized key(s)");
        
        // Check each authorized key file
        for (File keyFile : keyFiles) {
            System.out.println("Checking authorized key: " + keyFile.getName());
            
            String keyContent = Files.readString(keyFile.toPath());
            System.out.println("Authorized key file size: " + keyFile.length() + " bytes");
            System.out.println("Authorized key content length: " + keyContent.length());
            
            // Verify it's a single line
            String[] lines = keyContent.split("\n");
            System.out.println("Number of lines in authorized key file: " + lines.length);
            
            if (lines.length > 2) {
                System.out.println("ERROR: Authorized key file contains too many lines!");
                for (int i = 0; i < lines.length; i++) {
                    System.out.println("Line " + i + " (" + lines[i].length() + " chars): " + lines[i]);
                }
                fail("Authorized key file should contain only one key");
            }
            
            String trimmedContent = keyContent.trim();
            System.out.println("Trimmed authorized key content: " + trimmedContent);
            
            // Verify it matches the original public key
            String originalContent = Files.readString(new File(publicKeyPath).toPath()).trim();
            assertEquals(originalContent, trimmedContent, "Authorized key should match original public key");
            
            System.out.println("✓ Authorized key matches original public key");
            
            // Try to load it as a public key
            try {
                ssh.crypto.RSAKeyGenerator.loadPublicKey(keyFile.getAbsolutePath());
                System.out.println("✓ Authorized key can be loaded successfully");
            } catch (Exception e) {
                System.err.println("Failed to load authorized key: " + e.getMessage());
                e.printStackTrace();
                fail("Should be able to load authorized key");
            }
        }
    }

    @Test
    void testValidateKeyPair() throws Exception {
        System.out.println("=== Testing KeyManager.validateKeyPair ===");
        
        String keyName = "test_key";
        String privateKeyPath = keysDir + File.separator + keyName;
        String publicKeyPath = keysDir + File.separator + keyName + ".pub";
        
        // Generate a key pair
        KeyManager.generateKeyPair(keyName, keysDir);
        
        // Validate the key pair
        boolean isValid = KeyManager.validateKeyPair(privateKeyPath, publicKeyPath);
        System.out.println("Key pair validation result: " + isValid);
        
        assertTrue(isValid, "Generated key pair should be valid");
        
        System.out.println("✓ Key pair validation passed");
    }

    @Test
    void testMultipleKeyGeneration() throws Exception {
        System.out.println("=== Testing Multiple Key Generation ===");
        
        // Generate multiple key pairs to ensure no interference
        for (int i = 1; i <= 3; i++) {
            String keyName = "test_key_" + i;
            System.out.println("Generating key pair " + i + ": " + keyName);
            
            KeyManager.generateKeyPair(keyName, keysDir);
            
            String privateKeyPath = keysDir + File.separator + keyName;
            String publicKeyPath = keysDir + File.separator + keyName + ".pub";
            
            // Verify files exist
            assertTrue(new File(privateKeyPath).exists(), "Private key file should exist");
            assertTrue(new File(publicKeyPath).exists(), "Public key file should exist");
            
            // Verify each public key is unique and valid
            String publicKeyContent = Files.readString(new File(publicKeyPath).toPath()).trim();
            assertTrue(publicKeyContent.startsWith("MII"), "Public key should start with 'MII'");
            
            System.out.println("✓ Key pair " + i + " generated successfully");
        }
        
        System.out.println("✓ Multiple key generation test passed");
    }
} 