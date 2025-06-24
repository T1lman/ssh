package ssh.shared_model.protocol.messages;

import java.nio.ByteBuffer;

import ssh.shared_model.protocol.Message;
import ssh.shared_model.protocol.MessageType;

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