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

    @Override
    public byte[] serialize() {
        if (service == null) {
            service = "";
        }
        
        byte[] serviceBytes = service.getBytes();
        ByteBuffer buffer = ByteBuffer.allocate(serviceBytes.length);
        buffer.put(serviceBytes);
        
        return buffer.array();
    }

    @Override
    public void deserialize(byte[] data) {
        if (data != null && data.length > 0) {
            this.service = new String(data);
        } else {
            this.service = "";
        }
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }
} 