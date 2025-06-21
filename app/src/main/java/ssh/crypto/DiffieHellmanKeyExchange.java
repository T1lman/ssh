package ssh.crypto;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Handles Diffie-Hellman key exchange for establishing shared secrets.
 */
public class DiffieHellmanKeyExchange {
    private KeyPair keyPair;
    private PublicKey otherPublicKey;
    private byte[] sharedSecret;
    
    // Standard DH parameters (2048-bit)
    private static final BigInteger P = new BigInteger(
        "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74" +
        "020BBEA63B139B22514A08798E3404DDEF9519B3CD3A431B302B0A6DF25F1437" +
        "4FE1356D6D51C245E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7ED" +
        "EE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3DC2007CB8A163BF05" +
        "98DA48361C55D39A69163FA8FD24CF5F83655D23DCA3AD961C62F356208552BB" +
        "9ED529077096966D670C354E4ABC9804F1746C08CA18217C32905E462E36CE3B" +
        "E39E772C180E86039B2783A2EC07A28FB5C55DF06F4C52C9DE2BCBF695581718" +
        "3995497CEA956AE515D2261898FA051015728E5A8AACAA68FFFFFFFFFFFFFFFF", 16);
    
    private static final BigInteger G = BigInteger.valueOf(2);

    public DiffieHellmanKeyExchange() {
    }

    /**
     * Generate a new DH key pair.
     */
    public KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("DH");
        keyGen.initialize(2048);
        this.keyPair = keyGen.generateKeyPair();
        return this.keyPair;
    }

    /**
     * Get the public key as bytes.
     */
    public byte[] getPublicKeyBytes() {
        if (keyPair == null) {
            throw new IllegalStateException("Key pair not generated");
        }
        return keyPair.getPublic().getEncoded();
    }

    /**
     * Get the public key as a Base64 string.
     */
    public String getPublicKeyString() {
        return Base64.getEncoder().encodeToString(getPublicKeyBytes());
    }

    /**
     * Set the other party's public key from bytes.
     */
    public void setOtherPublicKey(byte[] publicKeyBytes) throws Exception {
        java.security.spec.X509EncodedKeySpec keySpec = 
            new java.security.spec.X509EncodedKeySpec(publicKeyBytes);
        java.security.KeyFactory keyFactory = java.security.KeyFactory.getInstance("DH");
        this.otherPublicKey = keyFactory.generatePublic(keySpec);
    }

    /**
     * Set the other party's public key from a Base64 string.
     */
    public void setOtherPublicKey(String publicKeyString) throws Exception {
        byte[] publicKeyBytes = Base64.getDecoder().decode(publicKeyString);
        setOtherPublicKey(publicKeyBytes);
    }

    /**
     * Compute the shared secret.
     */
    public byte[] computeSharedSecret() throws Exception {
        if (keyPair == null) {
            throw new IllegalStateException("Key pair not generated");
        }
        if (otherPublicKey == null) {
            throw new IllegalStateException("Other public key not set");
        }

        javax.crypto.KeyAgreement keyAgreement = javax.crypto.KeyAgreement.getInstance("DH");
        keyAgreement.init(keyPair.getPrivate());
        keyAgreement.doPhase(otherPublicKey, true);
        
        this.sharedSecret = keyAgreement.generateSecret();
        return this.sharedSecret;
    }

    /**
     * Get the computed shared secret.
     */
    public byte[] getSharedSecret() {
        if (sharedSecret == null) {
            throw new IllegalStateException("Shared secret not computed");
        }
        return sharedSecret;
    }

    /**
     * Get the shared secret as a Base64 string.
     */
    public String getSharedSecretString() {
        return Base64.getEncoder().encodeToString(getSharedSecret());
    }

    /**
     * Get the current key pair.
     */
    public KeyPair getKeyPair() {
        return keyPair;
    }

    /**
     * Get the other party's public key.
     */
    public PublicKey getOtherPublicKey() {
        return otherPublicKey;
    }
} 