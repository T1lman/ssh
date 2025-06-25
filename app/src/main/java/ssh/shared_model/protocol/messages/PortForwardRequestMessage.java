package ssh.shared_model.protocol.messages;

import ssh.shared_model.protocol.Message;
import ssh.shared_model.protocol.MessageType;

public class PortForwardRequestMessage extends Message {
    public enum ForwardType { LOCAL, REMOTE }
    private ForwardType forwardType;
    private int sourcePort;
    private String destHost;
    private int destPort;
    private String connectionId;

    public PortForwardRequestMessage() {
        super(MessageType.PORT_FORWARD_REQUEST);
    }

    public PortForwardRequestMessage(ForwardType forwardType, int sourcePort, String destHost, int destPort) {
        super(MessageType.PORT_FORWARD_REQUEST);
        this.forwardType = forwardType;
        this.sourcePort = sourcePort;
        this.destHost = destHost;
        this.destPort = destPort;
    }

    public ForwardType getForwardType() { return forwardType; }
    public int getSourcePort() { return sourcePort; }
    public String getDestHost() { return destHost; }
    public int getDestPort() { return destPort; }
    public String getConnectionId() { return connectionId; }

    public void setForwardType(ForwardType forwardType) { this.forwardType = forwardType; }
    public void setSourcePort(int sourcePort) { this.sourcePort = sourcePort; }
    public void setDestHost(String destHost) { this.destHost = destHost; }
    public void setDestPort(int destPort) { this.destPort = destPort; }
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