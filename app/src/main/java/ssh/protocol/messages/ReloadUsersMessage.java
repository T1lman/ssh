package ssh.protocol.messages;

import ssh.protocol.Message;
import ssh.protocol.MessageType;

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