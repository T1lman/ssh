package ssh.model.protocol.messages;

import ssh.model.protocol.Message;
import ssh.model.protocol.MessageType;

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
        return super.serialize();
    }

    @Override
    public void deserialize(byte[] data) {
        super.deserialize(data);
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