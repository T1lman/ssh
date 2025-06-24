package ssh.shared_model.protocol.messages;

import ssh.shared_model.protocol.Message;
import ssh.shared_model.protocol.MessageType;

/**
 * Message for requesting the server to reload its user database.
 */
public class ReloadUsersMessage extends Message {
    public ReloadUsersMessage() {
        super(MessageType.RELOAD_USERS);
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