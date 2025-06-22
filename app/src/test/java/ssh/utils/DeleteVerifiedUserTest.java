package ssh.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import java.io.File;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for DeleteVerifiedUser utility.
 */
public class DeleteVerifiedUserTest {
    
    @TempDir
    Path tempDir;
    
    private String testClientKeysDir;
    private String testServerKeysDir;
    private String testServerUsersFile;
    private String testClientCredentialsFile;
    
    @BeforeEach
    void setUp() throws Exception {
        // Create test directories
        testClientKeysDir = tempDir.resolve("client_keys").toString();
        testServerKeysDir = tempDir.resolve("server_keys").toString();
        testServerUsersFile = tempDir.resolve("users.properties").toString();
        testClientCredentialsFile = tempDir.resolve("credentials.properties").toString();
        
        // Create directories
        new File(testClientKeysDir).mkdirs();
        new File(testServerKeysDir).mkdirs();
    }
    
    @AfterEach
    void tearDown() {
        // Clean up test files
        deleteDirectory(new File(testClientKeysDir));
        deleteDirectory(new File(testServerKeysDir));
        new File(testServerUsersFile).delete();
        new File(testClientCredentialsFile).delete();
    }
    
    @Test
    void testDeleteUser() throws Exception {
        String username = "testuser";
        String password = "testpass";
        
        // First create a user
        createTestUser(username, password);
        
        // Verify user was created
        assertTrue(new File(testClientKeysDir, username + "_rsa").exists());
        assertTrue(new File(testServerKeysDir, username).exists());
        
        // Now delete the user using test-specific paths
        deleteTestUser(username);
        
        // Verify user was deleted
        assertFalse(new File(testClientKeysDir, username + "_rsa").exists());
        assertFalse(new File(testClientKeysDir, username + "_rsa.pub").exists());
        assertFalse(new File(testServerKeysDir, username).exists());
        
        // Verify server user database
        Properties serverUsers = new Properties();
        if (new File(testServerUsersFile).exists()) {
            serverUsers.load(Files.newInputStream(new File(testServerUsersFile).toPath()));
        }
        assertFalse(serverUsers.containsKey(username));
        
        // Verify client credentials
        Properties clientCredentials = new Properties();
        if (new File(testClientCredentialsFile).exists()) {
            clientCredentials.load(Files.newInputStream(new File(testClientCredentialsFile).toPath()));
        }
        assertFalse(clientCredentials.containsKey(username + ".username"));
    }
    
    private void createTestUser(String username, String password) throws Exception {
        // Create directories
        new File(testClientKeysDir).mkdirs();
        new File(testServerKeysDir).mkdirs();
        new File(testServerUsersFile).getParentFile().mkdirs();
        
        // 1. Generate key pair for the user
        String keyName = username + "_rsa";
        KeyManager.generateKeyPair(keyName, testClientKeysDir);
        
        // 2. Add user to server's user database
        ssh.auth.UserStore userStore = new ssh.auth.UserStore(testServerUsersFile, testServerKeysDir);
        userStore.addUser(username, password);
        userStore.saveUsers();
        
        // 3. Add public key to server's authorized keys
        String publicKeyPath = testClientKeysDir + File.separator + keyName + ".pub";
        KeyManager.addAuthorizedKey(username, publicKeyPath, testServerKeysDir);
        
        // 4. Add user to client credentials
        CredentialsManager credentialsManager = new CredentialsManager(testClientCredentialsFile);
        credentialsManager.addUser(username, password);
        credentialsManager.saveCredentials();
    }
    
    private void deleteTestUser(String username) throws Exception {
        // Define paths
        String clientKeysDir = testClientKeysDir;
        String serverKeysDir = testServerKeysDir;
        String serverUsersFile = testServerUsersFile;
        String clientCredentialsFile = testClientCredentialsFile;
        
        // 1. Remove user from server's user database
        ssh.auth.UserStore userStore = new ssh.auth.UserStore(serverUsersFile, serverKeysDir);
        userStore.removeUser(username);
        userStore.saveUsers();
        
        // 2. Remove user's authorized keys from server
        File userKeyDir = new File(serverKeysDir, username);
        if (userKeyDir.exists() && userKeyDir.isDirectory()) {
            File[] keyFiles = userKeyDir.listFiles();
            if (keyFiles != null) {
                for (File keyFile : keyFiles) {
                    keyFile.delete();
                }
            }
            userKeyDir.delete();
        }
        
        // 3. Remove user from client credentials
        CredentialsManager credentialsManager = new CredentialsManager(clientCredentialsFile);
        credentialsManager.removeUser(username);
        credentialsManager.saveCredentials();
        
        // 4. Delete user's SSH keys
        String privateKeyPath = clientKeysDir + File.separator + username + "_rsa";
        String publicKeyPath = clientKeysDir + File.separator + username + "_rsa.pub";
        
        new File(privateKeyPath).delete();
        new File(publicKeyPath).delete();
    }
    
    private void deleteDirectory(File dir) {
        if (dir.exists()) {
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
    }
} 