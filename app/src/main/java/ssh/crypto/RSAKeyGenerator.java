package ssh.crypto;

import java.io.*;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Handles RSA key pair generation, storage, and loading.
 */
public class RSAKeyGenerator {
    private static final int DEFAULT_KEY_SIZE = 2048;

    /**
     * Generate a new RSA key pair.
     */
    public static KeyPair generateKeyPair(int keySize) throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(keySize);
        return keyGen.generateKeyPair();
    }

    /**
     * Generate a new RSA key pair with default key size.
     */
    public static KeyPair generateKeyPair() throws Exception {
        return generateKeyPair(DEFAULT_KEY_SIZE);
    }

    /**
     * Save a key pair to files.
     */
    public static void saveKeyPair(KeyPair keyPair, String privateKeyPath, String publicKeyPath) throws Exception {
        // Save private key
        try (FileOutputStream fos = new FileOutputStream(privateKeyPath)) {
            byte[] privateKeyBytes = keyPair.getPrivate().getEncoded();
            fos.write(privateKeyBytes);
        }

        // Save public key
        try (FileOutputStream fos = new FileOutputStream(publicKeyPath)) {
            byte[] publicKeyBytes = keyPair.getPublic().getEncoded();
            fos.write(publicKeyBytes);
        }
    }

    /**
     * Load a key pair from files.
     */
    public static KeyPair loadKeyPair(String privateKeyPath, String publicKeyPath) throws Exception {
        // Load private key
        byte[] privateKeyBytes;
        try (FileInputStream fis = new FileInputStream(privateKeyPath)) {
            privateKeyBytes = fis.readAllBytes();
        }

        // Load public key
        byte[] publicKeyBytes;
        try (FileInputStream fis = new FileInputStream(publicKeyPath)) {
            publicKeyBytes = fis.readAllBytes();
        }

        // Create key specs
        PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
        X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(publicKeyBytes);

        // Generate key pair
        java.security.KeyFactory keyFactory = java.security.KeyFactory.getInstance("RSA");
        PrivateKey privateKey = keyFactory.generatePrivate(privateKeySpec);
        PublicKey publicKey = keyFactory.generatePublic(publicKeySpec);

        return new KeyPair(publicKey, privateKey);
    }

    /**
     * Load a public key from file.
     */
    public static PublicKey loadPublicKey(String publicKeyPath) throws Exception {
        byte[] publicKeyBytes;
        try (FileInputStream fis = new FileInputStream(publicKeyPath)) {
            publicKeyBytes = fis.readAllBytes();
        }

        X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(publicKeyBytes);
        java.security.KeyFactory keyFactory = java.security.KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(publicKeySpec);
    }

    /**
     * Load a private key from file.
     */
    public static PrivateKey loadPrivateKey(String privateKeyPath) throws Exception {
        byte[] privateKeyBytes;
        try (FileInputStream fis = new FileInputStream(privateKeyPath)) {
            privateKeyBytes = fis.readAllBytes();
        }

        PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
        java.security.KeyFactory keyFactory = java.security.KeyFactory.getInstance("RSA");
        return keyFactory.generatePrivate(privateKeySpec);
    }

    /**
     * Get public key as Base64 string.
     */
    public static String getPublicKeyString(PublicKey publicKey) {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    /**
     * Get private key as Base64 string.
     */
    public static String getPrivateKeyString(PrivateKey privateKey) {
        return Base64.getEncoder().encodeToString(privateKey.getEncoded());
    }

    /**
     * Create public key from Base64 string.
     */
    public static PublicKey createPublicKeyFromString(String publicKeyString) throws Exception {
        byte[] publicKeyBytes = Base64.getDecoder().decode(publicKeyString);
        X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(publicKeyBytes);
        java.security.KeyFactory keyFactory = java.security.KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(publicKeySpec);
    }

    /**
     * Create private key from Base64 string.
     */
    public static PrivateKey createPrivateKeyFromString(String privateKeyString) throws Exception {
        byte[] privateKeyBytes = Base64.getDecoder().decode(privateKeyString);
        PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
        java.security.KeyFactory keyFactory = java.security.KeyFactory.getInstance("RSA");
        return keyFactory.generatePrivate(privateKeySpec);
    }

    /**
     * Sign data with a private key.
     */
    public static byte[] sign(byte[] data, PrivateKey privateKey) throws Exception {
        java.security.Signature signature = java.security.Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(data);
        return signature.sign();
    }

    /**
     * Verify signature with a public key.
     */
    public static boolean verify(byte[] data, byte[] signature, PublicKey publicKey) throws Exception {
        java.security.Signature sig = java.security.Signature.getInstance("SHA256withRSA");
        sig.initVerify(publicKey);
        sig.update(data);
        return sig.verify(signature);
    }

    /**
     * Get signature as Base64 string.
     */
    public static String getSignatureString(byte[] signature) {
        return Base64.getEncoder().encodeToString(signature);
    }

    /**
     * Get signature from Base64 string.
     */
    public static byte[] getSignatureBytes(String signatureString) {
        return Base64.getDecoder().decode(signatureString);
    }
} 