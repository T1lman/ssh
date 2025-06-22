package ssh.protocol.messages;

import ssh.protocol.Message;
import ssh.protocol.MessageType;

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
        StringBuilder sb = new StringBuilder();
        if (dhPublicKey != null) {
            sb.append("dhPublicKey:").append(dhPublicKey).append(";");
        }
        if (clientId != null) {
            sb.append("clientId:").append(clientId).append(";");
        }
        if (serverId != null) {
            sb.append("serverId:").append(serverId).append(";");
        }
        if (signature != null) {
            sb.append("signature:").append(signature).append(";");
        }
        if (sessionId != null) {
            sb.append("sessionId:").append(sessionId).append(";");
        }
        return sb.toString().getBytes();
    }

    @Override
    public void deserialize(byte[] data) {
        String dataStr = new String(data);
        String[] parts = dataStr.split(";");
        
        for (String part : parts) {
            if (part.startsWith("dhPublicKey:")) {
                this.dhPublicKey = part.substring(12);
            } else if (part.startsWith("clientId:")) {
                this.clientId = part.substring(9);
            } else if (part.startsWith("serverId:")) {
                this.serverId = part.substring(9);
            } else if (part.startsWith("signature:")) {
                this.signature = part.substring(10);
            } else if (part.startsWith("sessionId:")) {
                this.sessionId = part.substring(10);
            }
        }
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