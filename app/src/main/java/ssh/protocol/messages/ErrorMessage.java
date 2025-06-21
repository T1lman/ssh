package ssh.protocol.messages;

import ssh.protocol.Message;
import ssh.protocol.MessageType;

/**
 * Message for handling error conditions.
 */
public class ErrorMessage extends Message {
    private String errorCode;
    private String errorMessage;
    private String details;

    public ErrorMessage() {
        super(MessageType.ERROR);
    }

    public ErrorMessage(String errorCode, String errorMessage) {
        super(MessageType.ERROR);
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    @Override
    public byte[] serialize() {
        StringBuilder sb = new StringBuilder();
        if (errorCode != null) {
            sb.append("errorCode:").append(errorCode).append(";");
        }
        if (errorMessage != null) {
            sb.append("errorMessage:").append(errorMessage).append(";");
        }
        if (details != null) {
            sb.append("details:").append(details).append(";");
        }
        return sb.toString().getBytes();
    }

    @Override
    public void deserialize(byte[] data) {
        String dataStr = new String(data);
        String[] parts = dataStr.split(";");
        
        for (String part : parts) {
            if (part.startsWith("errorCode:")) {
                this.errorCode = part.substring(10);
            } else if (part.startsWith("errorMessage:")) {
                this.errorMessage = part.substring(13);
            } else if (part.startsWith("details:")) {
                this.details = part.substring(8);
            }
        }
    }

    // Getters and setters
    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }
} 