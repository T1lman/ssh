package ssh.protocol.messages;

import ssh.protocol.Message;
import ssh.protocol.MessageType;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Base64;

/**
 * Message for handling Diffie-Hellman key exchange.
 */
public class KeyExchangeMessage extends Message {
    private String dhPublicKey;
    private String clientId;
    private String serverId;
    private String signature;
    private String sessionId;

    public KeyExchangeMessage() {
        super(MessageType.KEY_EXCHANGE_INIT);
    }

    public KeyExchangeMessage(MessageType type) {
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
    public String getDhPublicKey() {
        return dhPublicKey;
    }

    public void setDhPublicKey(String dhPublicKey) {
        this.dhPublicKey = dhPublicKey;
    }

    public void setDhPublicKey(byte[] dhPublicKeyBytes) {
        this.dhPublicKey = Base64.getEncoder().encodeToString(dhPublicKeyBytes);
    }

    @JsonIgnore
    public byte[] getDhPublicKeyBytes() {
        return Base64.getDecoder().decode(dhPublicKey);
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getServerId() {
        return serverId;
    }

    public void setServerId(String serverId) {
        this.serverId = serverId;
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

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
} 