package ssh.auth;

import ssh.crypto.RSAKeyGenerator;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.List;

/**
 * Handles public key authentication.
 */
public class PublicKeyAuth {
    private UserStore userStore;

    public PublicKeyAuth(UserStore userStore) {
        this.userStore = userStore;
    }

    /**
     * Authenticate a user using public key.
     */
    public boolean authenticate(String username, PublicKey clientPublicKey, byte[] signature, byte[] sessionData) {
        // Check if user exists
        if (!userStore.userExists(username)) {
            return false;
        }

        // Get user's authorized keys
        List<PublicKey> authorizedKeys = userStore.getAuthorizedKeys(username);
        
        // Check if the client's public key is in the authorized keys
        boolean keyAuthorized = false;
        for (PublicKey authorizedKey : authorizedKeys) {
            if (keysMatch(clientPublicKey, authorizedKey)) {
                keyAuthorized = true;
                break;
            }
        }

        if (!keyAuthorized) {
            return false;
        }

        // Verify the signature
        try {
            return RSAKeyGenerator.verify(sessionData, signature, clientPublicKey);
        } catch (Exception e) {
            System.err.println("Error verifying signature: " + e.getMessage());
            return false;
        }
    }

    /**
     * Check if two public keys match.
     */
    private boolean keysMatch(PublicKey key1, PublicKey key2) {
        try {
            String key1String = RSAKeyGenerator.getPublicKeyString(key1);
            String key2String = RSAKeyGenerator.getPublicKeyString(key2);
            return key1String.equals(key2String);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Add an authorized key for a user.
     */
    public void addAuthorizedKey(String username, PublicKey publicKey) throws Exception {
        userStore.addAuthorizedKey(username, publicKey);
    }

    /**
     * Remove an authorized key for a user.
     */
    public boolean removeAuthorizedKey(String username, String keyId) {
        return userStore.removeAuthorizedKey(username, keyId);
    }

    /**
     * Get authorized keys for a user.
     */
    public List<PublicKey> getAuthorizedKeys(String username) {
        return userStore.getAuthorizedKeys(username);
    }
} 