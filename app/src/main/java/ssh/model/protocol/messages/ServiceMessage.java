package ssh.model.protocol.messages;

import ssh.model.protocol.Message;
import ssh.model.protocol.MessageType;

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