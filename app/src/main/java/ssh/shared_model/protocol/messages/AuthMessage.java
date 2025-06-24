package ssh.shared_model.protocol.messages;

import com.fasterxml.jackson.annotation.JsonIgnore;

import ssh.shared_model.protocol.Message;
import ssh.shared_model.protocol.MessageType;

import java.util.Base64;

/**
 * Message for handling authentication.
 */
public class AuthMessage extends Message {
    private String username;
    private String authType;
    private String publicKey;
    private String password;
    private String signature;
    private boolean success;
    private String message;

    public AuthMessage() {
        super(MessageType.AUTH_REQUEST);
    }

    public AuthMessage(MessageType type) {
        super(type);
    }

    @Override
    public byte[] serialize() {
        return super.serialize();
    }

    @Override
    public void deserialize(byte[] data) {
        super.deserialize(data);
    }

    // Getters and setters
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getAuthType() {
        return authType;
    }

    public void setAuthType(String authType) {
        this.authType = authType;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public void setPublicKey(byte[] publicKeyBytes) {
        this.publicKey = Base64.getEncoder().encodeToString(publicKeyBytes);
    }

    @JsonIgnore
    public byte[] getPublicKeyBytes() {
        return Base64.getDecoder().decode(publicKey);
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public void setSignature(byte[] signatureBytes) {
        this.signature = Base64.getEncoder().encodeToString(signatureBytes);
    }

    @JsonIgnore
    public byte[] getSignatureBytes() {
        return Base64.getDecoder().decode(signature);
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
} 