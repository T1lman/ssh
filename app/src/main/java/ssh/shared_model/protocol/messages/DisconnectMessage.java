package ssh.shared_model.protocol.messages;

import ssh.shared_model.protocol.Message;
import ssh.shared_model.protocol.MessageType;

/**
 * Message for clean disconnects.
 */
public class DisconnectMessage extends Message {
    public DisconnectMessage() {
        super(MessageType.DISCONNECT);
    }

    @Override
    public byte[] serialize() {
        return new byte[0]; // No payload needed
    }

    @Override
    public void deserialize(byte[] data) {
        // No payload to deserialize
    }
} 