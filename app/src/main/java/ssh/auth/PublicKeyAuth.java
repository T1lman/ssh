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
            System.err.println("PublicKeyAuth: User does not exist: " + username);
            return false;
        }

        // Get user's authorized keys
        List<PublicKey> authorizedKeys = userStore.getAuthorizedKeys(username);
        System.err.println("PublicKeyAuth: Found " + authorizedKeys.size() + " authorized keys for user: " + username);
        
        // Check if the client's public key is in the authorized keys
        boolean keyAuthorized = false;
        String clientKeyString = null;
        try {
            clientKeyString = RSAKeyGenerator.getPublicKeyString(clientPublicKey);
            System.err.println("PublicKeyAuth: Client public key: " + clientKeyString);
        } catch (Exception e) {
            System.err.println("PublicKeyAuth: Error getting client key string: " + e.getMessage());
        }
        
        for (PublicKey authorizedKey : authorizedKeys) {
            try {
                String authorizedKeyString = RSAKeyGenerator.getPublicKeyString(authorizedKey);
                System.err.println("PublicKeyAuth: Comparing with authorized key: " + authorizedKeyString);
                if (keysMatch(clientPublicKey, authorizedKey)) {
                    keyAuthorized = true;
                    System.err.println("PublicKeyAuth: Key match found!");
                    break;
                }
            } catch (Exception e) {
                System.err.println("PublicKeyAuth: Error comparing keys: " + e.getMessage());
            }
        }

        if (!keyAuthorized) {
            System.err.println("PublicKeyAuth: No matching authorized key found for user: " + username);
            return false;
        }

        // Verify the signature
        try {
            boolean signatureValid = RSAKeyGenerator.verify(sessionData, signature, clientPublicKey);
            System.err.println("PublicKeyAuth: Signature verification result: " + signatureValid);
            return signatureValid;
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