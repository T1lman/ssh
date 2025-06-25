package ssh.shared_model.protocol.messages;

import ssh.shared_model.protocol.Message;
import ssh.shared_model.protocol.MessageType;

public class PortForwardDataMessage extends Message {
    private String connectionId;
    private String data; // base64-encoded

    public PortForwardDataMessage() {
        super(MessageType.PORT_FORWARD_DATA);
    }

    public PortForwardDataMessage(String connectionId, String data) {
        super(MessageType.PORT_FORWARD_DATA);
        this.connectionId = connectionId;
        this.data = data;
    }

    public String getConnectionId() { return connectionId; }
    public void setConnectionId(String connectionId) { this.connectionId = connectionId; }

    public String getData() { return data; }
    public void setData(String data) { this.data = data; }

    @Override
    public byte[] serialize() {
        return super.serialize();
    }

    @Override
    public void deserialize(byte[] data) {
        super.deserialize(data);
    }
} 