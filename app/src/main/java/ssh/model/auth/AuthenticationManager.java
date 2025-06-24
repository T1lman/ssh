package ssh.model.auth;

import ssh.model.crypto.RSAKeyGenerator;
import ssh.model.utils.Logger;

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

            Logger.info("PublicKeyAuth: publicKey present: " + (publicKeyString != null));
            Logger.info("PublicKeyAuth: signature present: " + (signatureString != null));
            Logger.info("PublicKeyAuth: sessionData present: " + (sessionDataString != null));

            if (publicKeyString == null || signatureString == null || sessionDataString == null) {
                Logger.error("PublicKeyAuth: Missing required credentials for public key auth");
                return false;
            }

            PublicKey clientPublicKey = RSAKeyGenerator.createPublicKeyFromString(publicKeyString);
            Logger.info("PublicKeyAuth: Created public key from string");
            
            byte[] signature = java.util.Base64.getDecoder().decode(signatureString);
            Logger.info("PublicKeyAuth: Decoded signature, length: " + signature.length);
            
            byte[] sessionData = java.util.Base64.getDecoder().decode(sessionDataString);
            Logger.info("PublicKeyAuth: Decoded session data, length: " + sessionData.length);

            boolean result = publicKeyAuth.authenticate(username, clientPublicKey, signature, sessionData);
            Logger.info("PublicKeyAuth: public key auth result: " + result);
            return result;
        } catch (Exception e) {
            Logger.error("Public key authentication error: " + e.getMessage());
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
            Logger.error("PasswordAuth: Password is null for user: " + username);
            return false;
        }

        Logger.info("PasswordAuth: Verifying password for user: " + username);
        boolean result = passwordAuth.authenticate(username, password);
        Logger.info("PasswordAuth: Password authentication result for " + username + ": " + result);
        return result;
    }

    /**
     * Authenticate using dual authentication (both password and public key).
     */
    private boolean authenticateDual(String username, Map<String, String> credentials) {
        try {
            String password = credentials.get("password");
            String publicKeyString = credentials.get("publicKey");
            String signatureString = credentials.get("signature");
            String sessionDataString = credentials.get("sessionData");

            Logger.info("DualAuth: password present: " + (password != null));
            Logger.info("DualAuth: publicKey present: " + (publicKeyString != null));
            Logger.info("DualAuth: signature present: " + (signatureString != null));
            Logger.info("DualAuth: sessionData present: " + (sessionDataString != null));

            if (password == null || publicKeyString == null || signatureString == null || sessionDataString == null) {
                Logger.error("DualAuth: Missing credentials for dual authentication");
                return false;
            }

            boolean passwordAuthResult = authenticatePassword(username, credentials);
            if (!passwordAuthResult) {
                Logger.error("DualAuth: Password authentication failed for user: " + username);
                return false;
            }
            Logger.info("DualAuth: Password authentication succeeded for user: " + username);

            boolean publicKeyAuthResult = authenticatePublicKey(username, credentials);
            if (!publicKeyAuthResult) {
                Logger.error("DualAuth: Public key authentication failed for user: " + username);
                return false;
            }
            Logger.info("DualAuth: Public key authentication succeeded for user: " + username);

            Logger.info("DualAuth: Dual authentication successful for user: " + username);
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