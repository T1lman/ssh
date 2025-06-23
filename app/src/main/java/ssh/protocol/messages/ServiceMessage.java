package ssh.protocol.messages;

import ssh.protocol.Message;
import ssh.protocol.MessageType;

import java.nio.ByteBuffer;

/**
 * Message for service requests and responses.
 */
public class ServiceMessage extends Message {
    private String service;

    public ServiceMessage(MessageType type) {
        super(type);
    }

    public ServiceMessage() {
        super(MessageType.SERVICE_REQUEST);
    }

    @Override
    public byte[] serialize() {
        return super.serialize();
    }

    @Override
    public void deserialize(byte[] data) {
        super.deserialize(data);
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }
} 