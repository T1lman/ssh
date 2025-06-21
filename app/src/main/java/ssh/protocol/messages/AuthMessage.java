package ssh.protocol.messages;

import ssh.protocol.Message;
import ssh.protocol.MessageType;

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
        StringBuilder sb = new StringBuilder();
        if (username != null) {
            sb.append("username:").append(username).append(";");
        }
        if (authType != null) {
            sb.append("authType:").append(authType).append(";");
        }
        if (publicKey != null) {
            sb.append("publicKey:").append(publicKey).append(";");
        }
        if (password != null) {
            sb.append("password:").append(password).append(";");
        }
        if (signature != null) {
            sb.append("signature:").append(signature).append(";");
        }
        if (getType() == MessageType.AUTH_SUCCESS || getType() == MessageType.AUTH_FAILURE) {
            sb.append("success:").append(success).append(";");
            if (message != null) {
                sb.append("message:").append(message).append(";");
            }
        }
        return sb.toString().getBytes();
    }

    @Override
    public void deserialize(byte[] data) {
        String dataStr = new String(data);
        String[] parts = dataStr.split(";");
        
        for (String part : parts) {
            if (part.startsWith("username:")) {
                this.username = part.substring(9);
            } else if (part.startsWith("authType:")) {
                this.authType = part.substring(9);
            } else if (part.startsWith("publicKey:")) {
                this.publicKey = part.substring(10);
            } else if (part.startsWith("password:")) {
                this.password = part.substring(9);
            } else if (part.startsWith("signature:")) {
                this.signature = part.substring(10);
            } else if (part.startsWith("success:")) {
                this.success = Boolean.parseBoolean(part.substring(8));
            } else if (part.startsWith("message:")) {
                this.message = part.substring(8);
            }
        }
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