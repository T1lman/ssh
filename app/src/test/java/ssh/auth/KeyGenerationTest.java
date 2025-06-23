package ssh.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ssh.model.crypto.RSAKeyGenerator;
import ssh.model.utils.KeyManager;
import ssh.model.auth.UserStore;
import ssh.model.auth.AuthenticationManager;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for key generation and authentication flow.
 */
public class KeyGenerationTest {

    @TempDir
    Path tempDir;
    
    private String clientKeysDir;
    private String serverKeysDir;
    private String testUsername = "testuser";

    @BeforeEach
    void setUp() {
        clientKeysDir = tempDir.resolve("client_keys").toString();
        serverKeysDir = tempDir.resolve("server_keys").toString();
        
        // Create directories
        new File(clientKeysDir).mkdirs();
        new File(serverKeysDir).mkdirs();
    }

    @Test
    void testKeyGenerationAndStorage() throws Exception {
        System.out.println("=== Testing Key Generation and Storage ===");
        
        // Generate key pair
        String keyName = testUsername + "_rsa";
        String privateKeyPath = clientKeysDir + File.separator + keyName;
        String publicKeyPath = clientKeysDir + File.separator + keyName + ".pub";
        
        System.out.println("Generating key pair: " + keyName);
        KeyManager.generateKeyPair(keyName, clientKeysDir);
        
        // Verify files exist
        File privateKeyFile = new File(privateKeyPath);
        File publicKeyFile = new File(publicKeyPath);
        
        assertTrue(privateKeyFile.exists(), "Private key file should exist");
        assertTrue(publicKeyFile.exists(), "Public key file should exist");
        
        System.out.println("✓ Key files created successfully");
        
        // Read and verify public key content
        String publicKeyContent = Files.readString(publicKeyFile.toPath());
        System.out.println("Public key content length: " + publicKeyContent.length());
        System.out.println("Public key content (first 100 chars): " + publicKeyContent.substring(0, Math.min(100, publicKeyContent.length())));
        
        // Verify it's a single line (no newlines in the middle)
        String[] lines = publicKeyContent.split("\n");
        System.out.println("Number of lines in public key file: " + lines.length);
        
        if (lines.length > 1) {
            System.out.println("WARNING: Public key file contains multiple lines!");
            for (int i = 0; i < lines.length; i++) {
                System.out.println("Line " + i + " length: " + lines[i].length());
            }
        }
        
        // Should be exactly one line (or two if there's a trailing newline)
        assertTrue(lines.length <= 2, "Public key should be on a single line");
        
        // Verify we can load the key pair
        KeyPair loadedKeyPair = RSAKeyGenerator.loadKeyPair(privateKeyPath, publicKeyPath);
        assertNotNull(loadedKeyPair, "Should be able to load generated key pair");
        
        System.out.println("✓ Key pair can be loaded successfully");
        
        // Get public key string
        String publicKeyString = RSAKeyGenerator.getPublicKeyString(loadedKeyPair.getPublic());
        System.out.println("Public key string length: " + publicKeyString.length());
        System.out.println("Public key string: " + publicKeyString);
        
        // Verify it matches the file content (trimmed)
        String trimmedFileContent = publicKeyContent.trim();
        assertEquals(publicKeyString, trimmedFileContent, "File content should match public key string");
        
        System.out.println("✓ Public key string matches file content");
    }

    @Test
    void testKeyAuthorization() throws Exception {
        System.out.println("=== Testing Key Authorization ===");
        
        // Generate key pair
        String keyName = testUsername + "_rsa";
        KeyManager.generateKeyPair(keyName, clientKeysDir);
        
        String publicKeyPath = clientKeysDir + File.separator + keyName + ".pub";
        
        // Add to server authorized keys
        System.out.println("Adding public key to server for user: " + testUsername);
        KeyManager.addAuthorizedKey(testUsername, publicKeyPath, serverKeysDir);
        
        // Verify authorized key was created
        File userKeyDir = new File(serverKeysDir, testUsername);
        assertTrue(userKeyDir.exists(), "User key directory should exist");
        
        File[] keyFiles = userKeyDir.listFiles((dir, name) -> name.endsWith(".pub"));
        assertNotNull(keyFiles, "Should find key files");
        assertTrue(keyFiles.length > 0, "Should have at least one authorized key");
        
        System.out.println("✓ Found " + keyFiles.length + " authorized key(s)");
        
        // Read and verify each authorized key
        for (File keyFile : keyFiles) {
            System.out.println("Checking authorized key: " + keyFile.getName());
            String keyContent = Files.readString(keyFile.toPath());
            
            System.out.println("Authorized key content length: " + keyContent.length());
            System.out.println("Authorized key content: " + keyContent);
            
            // Verify it's a single line
            String[] lines = keyContent.split("\n");
            assertTrue(lines.length <= 2, "Authorized key should be on a single line");
            
            // Verify we can load it as a public key
            PublicKey loadedKey = RSAKeyGenerator.loadPublicKey(keyFile.getAbsolutePath());
            assertNotNull(loadedKey, "Should be able to load authorized key");
            
            System.out.println("✓ Authorized key can be loaded successfully");
        }
    }

    @Test
    void testUserStoreIntegration() throws Exception {
        System.out.println("=== Testing UserStore Integration ===");
        
        // Generate key pair
        String keyName = testUsername + "_rsa";
        KeyManager.generateKeyPair(keyName, clientKeysDir);
        
        String publicKeyPath = clientKeysDir + File.separator + keyName + ".pub";
        
        // Add to server authorized keys
        KeyManager.addAuthorizedKey(testUsername, publicKeyPath, serverKeysDir);
        
        // Create UserStore
        String userPropertiesPath = tempDir.resolve("users.properties").toString();
        UserStore userStore = new UserStore(userPropertiesPath, serverKeysDir);
        
        // Add user to store
        userStore.addUser(testUsername, "password123");
        
        // Get authorized keys for user
        List<PublicKey> authorizedKeys = userStore.getAuthorizedKeys(testUsername);
        System.out.println("UserStore found " + authorizedKeys.size() + " authorized keys for user: " + testUsername);
        
        assertTrue(authorizedKeys.size() > 0, "Should find authorized keys for user");
        
        // Load the original key pair
        String privateKeyPath = clientKeysDir + File.separator + keyName;
        KeyPair originalKeyPair = RSAKeyGenerator.loadKeyPair(privateKeyPath, publicKeyPath);
        
        // Verify the original public key is in the authorized keys
        String originalPublicKeyString = RSAKeyGenerator.getPublicKeyString(originalKeyPair.getPublic());
        boolean found = false;
        
        for (PublicKey authorizedKey : authorizedKeys) {
            String authorizedKeyString = RSAKeyGenerator.getPublicKeyString(authorizedKey);
            System.out.println("Comparing:");
            System.out.println("  Original: " + originalPublicKeyString);
            System.out.println("  Authorized: " + authorizedKeyString);
            
            if (originalPublicKeyString.equals(authorizedKeyString)) {
                found = true;
                System.out.println("✓ Found matching authorized key!");
                break;
            }
        }
        
        assertTrue(found, "Original public key should be found in authorized keys");
        System.out.println("✓ UserStore integration test passed");
    }

    @Test
    void testAuthenticationFlow() throws Exception {
        System.out.println("=== Testing Complete Authentication Flow ===");
        
        // Generate key pair
        String keyName = testUsername + "_rsa";
        KeyManager.generateKeyPair(keyName, clientKeysDir);
        
        String publicKeyPath = clientKeysDir + File.separator + keyName + ".pub";
        
        // Add to server authorized keys
        KeyManager.addAuthorizedKey(testUsername, publicKeyPath, serverKeysDir);
        
        // Create UserStore and AuthenticationManager
        String userPropertiesPath = tempDir.resolve("users.properties").toString();
        UserStore userStore = new UserStore(userPropertiesPath, serverKeysDir);
        userStore.addUser(testUsername, "password123");
        
        AuthenticationManager authManager = new AuthenticationManager(userStore);
        
        // Load the client key pair
        String privateKeyPath = clientKeysDir + File.separator + keyName;
        KeyPair clientKeyPair = RSAKeyGenerator.loadKeyPair(privateKeyPath, publicKeyPath);
        
        // Simulate authentication data
        String sessionId = "test-session-123";
        byte[] sessionData = sessionId.getBytes();
        byte[] signature = RSAKeyGenerator.sign(sessionData, clientKeyPair.getPrivate());
        
        // Create credentials map
        java.util.Map<String, String> credentials = new java.util.HashMap<>();
        credentials.put("password", "password123");
        credentials.put("publicKey", RSAKeyGenerator.getPublicKeyString(clientKeyPair.getPublic()));
        credentials.put("signature", java.util.Base64.getEncoder().encodeToString(signature));
        credentials.put("sessionData", java.util.Base64.getEncoder().encodeToString(sessionData));
        
        // Test dual authentication
        boolean authResult = authManager.authenticate(testUsername, "dual", credentials);
        
        System.out.println("Authentication result: " + authResult);
        assertTrue(authResult, "Dual authentication should succeed");
        
        System.out.println("✓ Complete authentication flow test passed");
    }

    @Test
    void testKeyValidation() throws Exception {
        System.out.println("=== Testing Key Validation ===");
        
        // Generate key pair
        String keyName = testUsername + "_rsa";
        String privateKeyPath = clientKeysDir + File.separator + keyName;
        String publicKeyPath = clientKeysDir + File.separator + keyName + ".pub";
        
        KeyManager.generateKeyPair(keyName, clientKeysDir);
        
        // Test validation
        boolean isValid = KeyManager.validateKeyPair(privateKeyPath, publicKeyPath);
        System.out.println("Key pair validation result: " + isValid);
        
        assertTrue(isValid, "Generated key pair should be valid");
        
        System.out.println("✓ Key validation test passed");
    }
} 