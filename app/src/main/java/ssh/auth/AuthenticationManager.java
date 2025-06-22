package ssh.auth;

import ssh.crypto.RSAKeyGenerator;
import ssh.utils.Logger;

import java.security.PublicKey;
import java.util.Map;

/**
 * Manages authentication using different methods.
 */
public class AuthenticationManager {
    private UserStore userStore;
    private PublicKeyAuth publicKeyAuth;
    private PasswordAuth passwordAuth;

    public AuthenticationManager(UserStore userStore) {
        this.userStore = userStore;
        this.publicKeyAuth = new PublicKeyAuth(userStore);
        this.passwordAuth = new PasswordAuth(userStore);
    }

    /**
     * Authenticate a user using the specified authentication type.
     */
    public boolean authenticate(String username, String authType, Map<String, String> credentials) {
        Logger.info("Authentication attempt for user: " + username + ", type: " + authType);
        
        if (!isValidUser(username)) {
            Logger.error("User validation failed for: " + username);
            return false;
        }
        
        Logger.info("User validation passed for: " + username);

        switch (authType.toLowerCase()) {
            case "publickey":
                Logger.info("Attempting public key authentication for: " + username);
                return authenticatePublicKey(username, credentials);
            case "password":
                Logger.info("Attempting password authentication for: " + username);
                return authenticatePassword(username, credentials);
            case "dual":
                Logger.info("Attempting dual authentication (password + public key) for: " + username);
                return authenticateDual(username, credentials);
            default:
                Logger.error("Unknown authentication type: " + authType);
                return false;
        }
    }

    /**
     * Authenticate using public key.
     */
    private boolean authenticatePublicKey(String username, Map<String, String> credentials) {
        try {
            String publicKeyString = credentials.get("publicKey");
            String signatureString = credentials.get("signature");
            String sessionDataString = credentials.get("sessionData");

            System.err.println("AuthenticationManager: Public key auth - publicKey: " + (publicKeyString != null ? "present" : "null"));
            System.err.println("AuthenticationManager: Public key auth - signature: " + (signatureString != null ? "present" : "null"));
            System.err.println("AuthenticationManager: Public key auth - sessionData: " + (sessionDataString != null ? "present" : "null"));

            if (publicKeyString == null || signatureString == null || sessionDataString == null) {
                System.err.println("AuthenticationManager: Missing required credentials for public key auth");
                return false;
            }

            PublicKey clientPublicKey = RSAKeyGenerator.createPublicKeyFromString(publicKeyString);
            System.err.println("AuthenticationManager: Created public key from string");
            
            byte[] signature = java.util.Base64.getDecoder().decode(signatureString);
            System.err.println("AuthenticationManager: Decoded signature, length: " + signature.length);
            
            byte[] sessionData = java.util.Base64.getDecoder().decode(sessionDataString);
            System.err.println("AuthenticationManager: Decoded session data, length: " + sessionData.length);

            boolean result = publicKeyAuth.authenticate(username, clientPublicKey, signature, sessionData);
            System.err.println("AuthenticationManager: Public key auth result: " + result);
            return result;
        } catch (Exception e) {
            System.err.println("Public key authentication error: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Authenticate using password.
     */
    private boolean authenticatePassword(String username, Map<String, String> credentials) {
        String password = credentials.get("password");
        if (password == null) {
            Logger.error("Password is null for user: " + username);
            return false;
        }

        Logger.info("Verifying password for user: " + username);
        boolean result = passwordAuth.authenticate(username, password);
        Logger.info("Password authentication result for " + username + ": " + result);
        return result;
    }

    /**
     * Authenticate using dual authentication (both password and public key).
     */
    private boolean authenticateDual(String username, Map<String, String> credentials) {
        try {
            // Check if both password and public key credentials are provided
            String password = credentials.get("password");
            String publicKeyString = credentials.get("publicKey");
            String signatureString = credentials.get("signature");
            String sessionDataString = credentials.get("sessionData");

            if (password == null || publicKeyString == null || signatureString == null || sessionDataString == null) {
                Logger.error("Dual authentication requires both password and public key credentials");
                return false;
            }

            // First, authenticate with password
            boolean passwordAuth = authenticatePassword(username, credentials);
            if (!passwordAuth) {
                Logger.error("Password authentication failed for dual auth");
                return false;
            }

            // Then, authenticate with public key
            boolean publicKeyAuth = authenticatePublicKey(username, credentials);
            if (!publicKeyAuth) {
                Logger.error("Public key authentication failed for dual auth");
                return false;
            }

            // Both must succeed for dual authentication
            Logger.info("Dual authentication successful for user: " + username);
            return true;

        } catch (Exception e) {
            Logger.error("Dual authentication error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Check if a user is valid.
     */
    private boolean isValidUser(String username) {
        boolean exists = userStore.userExists(username);
        Logger.info("User existence check for '" + username + "': " + exists);
        return exists;
    }

    /**
     * Add a new user with password.
     */
    public void addUser(String username, String password) {
        passwordAuth.addUser(username, password);
    }

    /**
     * Add an authorized key for a user.
     */
    public void addAuthorizedKey(String username, PublicKey publicKey) throws Exception {
        publicKeyAuth.addAuthorizedKey(username, publicKey);
    }

    /**
     * Remove an authorized key for a user.
     */
    public boolean removeAuthorizedKey(String username, String keyId) {
        return publicKeyAuth.removeAuthorizedKey(username, keyId);
    }

    /**
     * Change a user's password.
     */
    public void changePassword(String username, String newPassword) {
        passwordAuth.changePassword(username, newPassword);
    }

    /**
     * Remove a user.
     */
    public void removeUser(String username) {
        passwordAuth.removeUser(username);
    }

    /**
     * Get the user store.
     */
    public UserStore getUserStore() {
        return userStore;
    }

    /**
     * Get the public key authenticator.
     */
    public PublicKeyAuth getPublicKeyAuth() {
        return publicKeyAuth;
    }

    /**
     * Get the password authenticator.
     */
    public PasswordAuth getPasswordAuth() {
        return passwordAuth;
    }

    /**
     * Reload the user database from disk.
     */
    public void reloadUsers() {
        try {
            Logger.info("Reloading user database...");
            userStore.loadUsers();
            Logger.info("User database reloaded successfully");
        } catch (Exception e) {
            Logger.error("Failed to reload user database: " + e.getMessage());
            throw new RuntimeException("Failed to reload user database", e);
        }
    }
} 