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

            if (publicKeyString == null || signatureString == null || sessionDataString == null) {
                return false;
            }

            PublicKey clientPublicKey = RSAKeyGenerator.createPublicKeyFromString(publicKeyString);
            byte[] signature = java.util.Base64.getDecoder().decode(signatureString);
            byte[] sessionData = java.util.Base64.getDecoder().decode(sessionDataString);

            return publicKeyAuth.authenticate(username, clientPublicKey, signature, sessionData);
        } catch (Exception e) {
            System.err.println("Public key authentication error: " + e.getMessage());
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
} 