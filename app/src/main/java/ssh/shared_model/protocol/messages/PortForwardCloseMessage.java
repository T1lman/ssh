package ssh.shared_model.protocol.messages;

import ssh.shared_model.protocol.Message;
import ssh.shared_model.protocol.MessageType;

public class PortForwardCloseMessage extends Message {
    private String connectionId;

    public PortForwardCloseMessage() {
        super(MessageType.PORT_FORWARD_CLOSE);
    }

    public PortForwardCloseMessage(String connectionId) {
        super(MessageType.PORT_FORWARD_CLOSE);
        this.connectionId = connectionId;
    }

    public String getConnectionId() { return connectionId; }
    public void setConnectionId(String connectionId) { this.connectionId = connectionId; }

    @Override
    public byte[] serialize() {
        return super.serialize();
    }

    @Override
    public void deserialize(byte[] data) {
        super.deserialize(data);
    }
} 