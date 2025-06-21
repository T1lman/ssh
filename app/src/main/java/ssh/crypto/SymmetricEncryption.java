package ssh.crypto;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Handles symmetric encryption using AES-GCM.
 */
public class SymmetricEncryption {
    private SecretKey secretKey;
    private Cipher cipher;
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 16;

    public SymmetricEncryption() {
    }

    /**
     * Initialize the encryption with a shared secret.
     */
    public void initializeKey(byte[] sharedSecret) throws Exception {
        // Derive a key from the shared secret using SHA-256
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        byte[] keyBytes = digest.digest(sharedSecret);
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
        this.cipher = Cipher.getInstance(ALGORITHM);
    }

    /**
     * Encrypt data using AES-GCM.
     */
    public byte[] encrypt(byte[] data) throws Exception {
        if (secretKey == null) {
            throw new IllegalStateException("Key not initialized");
        }

        // Generate random IV
        byte[] iv = new byte[GCM_IV_LENGTH];
        SecureRandom random = new SecureRandom();
        random.nextBytes(iv);

        // Initialize cipher for encryption
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);

        // Encrypt the data
        byte[] encryptedData = cipher.doFinal(data);

        // Combine IV and encrypted data
        byte[] result = new byte[iv.length + encryptedData.length];
        System.arraycopy(iv, 0, result, 0, iv.length);
        System.arraycopy(encryptedData, 0, result, iv.length, encryptedData.length);

        return result;
    }

    /**
     * Decrypt data using AES-GCM.
     */
    public byte[] decrypt(byte[] encryptedData) throws Exception {
        if (secretKey == null) {
            throw new IllegalStateException("Key not initialized");
        }

        if (encryptedData.length < GCM_IV_LENGTH + GCM_TAG_LENGTH) {
            throw new IllegalArgumentException("Encrypted data too short");
        }

        // Extract IV and encrypted data
        byte[] iv = new byte[GCM_IV_LENGTH];
        byte[] cipherText = new byte[encryptedData.length - GCM_IV_LENGTH];
        
        System.arraycopy(encryptedData, 0, iv, 0, GCM_IV_LENGTH);
        System.arraycopy(encryptedData, GCM_IV_LENGTH, cipherText, 0, cipherText.length);

        // Initialize cipher for decryption
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

        // Decrypt the data
        return cipher.doFinal(cipherText);
    }

    /**
     * Get the secret key as a Base64 string.
     */
    public String getSecretKeyString() {
        if (secretKey == null) {
            return null;
        }
        return Base64.getEncoder().encodeToString(secretKey.getEncoded());
    }

    /**
     * Set the secret key from a Base64 string.
     */
    public void setSecretKeyFromString(String keyString) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(keyString);
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
        this.cipher = Cipher.getInstance(ALGORITHM);
    }

    /**
     * Generate a new random key.
     */
    public void generateNewKey() throws Exception {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256); // Use 256-bit keys
        this.secretKey = keyGen.generateKey();
        this.cipher = Cipher.getInstance(ALGORITHM);
    }
} 