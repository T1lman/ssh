package ssh.shared_model.protocol.messages;

import ssh.shared_model.protocol.Message;
import ssh.shared_model.protocol.MessageType;

public class PortForwardAcceptMessage extends Message {
    private String connectionId;
    private boolean success;
    private String errorMessage;

    public PortForwardAcceptMessage() {
        super(MessageType.PORT_FORWARD_ACCEPT);
    }

    public PortForwardAcceptMessage(String connectionId, boolean success, String errorMessage) {
        super(MessageType.PORT_FORWARD_ACCEPT);
        this.connectionId = connectionId;
        this.success = success;
        this.errorMessage = errorMessage;
    }

    public String getConnectionId() { return connectionId; }
    public void setConnectionId(String connectionId) { this.connectionId = connectionId; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    @Override
    public byte[] serialize() {
        return super.serialize();
    }

    @Override
    public void deserialize(byte[] data) {
        super.deserialize(data);
    }
} 