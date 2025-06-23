package ssh.model.protocol;

import ssh.model.utils.Logger;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.CRC32;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import ssh.model.protocol.MessageType;

/**
 * Abstract base class for all SSH protocol messages.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "typeName"
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = ssh.model.protocol.messages.ShellMessage.class, name = "ShellMessage"),
    @JsonSubTypes.Type(value = ssh.model.protocol.messages.ServiceMessage.class, name = "ServiceMessage"),
    @JsonSubTypes.Type(value = ssh.model.protocol.messages.ErrorMessage.class, name = "ErrorMessage"),
    @JsonSubTypes.Type(value = ssh.model.protocol.messages.KeyExchangeMessage.class, name = "KeyExchangeMessage"),
    @JsonSubTypes.Type(value = ssh.model.protocol.messages.FileTransferMessage.class, name = "FileTransferMessage"),
    @JsonSubTypes.Type(value = ssh.model.protocol.messages.AuthMessage.class, name = "AuthMessage")
})
public abstract class Message {
    private MessageType type;
    private byte[] payload;
    private int checksum;

    @JsonIgnore
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public Message(MessageType type) {
        this.type = type;
    }

    /**
     * Serialize the message to bytes for transmission (default: JSON).
     */
    public byte[] serialize() {
        try {
            return objectMapper.writeValueAsBytes(this);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize message to JSON", e);
        }
    }

    /**
     * Deserialize the message from bytes (default: JSON).
     */
    public void deserialize(byte[] data) {
        try {
            Message m = objectMapper.readValue(data, this.getClass());
            // Copy all fields from m to this
            for (java.lang.reflect.Field field : this.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                field.set(this, field.get(m));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize message from JSON", e);
        }
    }

    /**
     * Calculate the checksum of the payload.
     */
    public void calculateChecksum() {
        if (payload != null) {
            CRC32 crc = new CRC32();
            crc.update(payload);
            this.checksum = (int) crc.getValue();
        }
    }

    /**
     * Verify the checksum of the payload.
     */
    public boolean verifyChecksum() {
        if (payload == null) {
            return checksum == 0;
        }
        
        CRC32 crc = new CRC32();
        crc.update(payload);
        return checksum == (int) crc.getValue();
    }

    /**
     * Create a complete message packet with length, type, payload, and checksum.
     */
    public byte[] toPacket() {
        byte[] serializedPayload = serialize();
        this.payload = serializedPayload;
        calculateChecksum();

        // Packet structure: [4 bytes: length] [1 byte: type] [payload] [4 bytes: checksum]
        int restLength = 1 + serializedPayload.length + 4; // type + payload + checksum
        ByteBuffer buffer = ByteBuffer.allocate(4 + restLength);
        
        buffer.putInt(restLength); // Length of the rest of the message (not including these 4 bytes)
        buffer.put((byte) type.getValue());
        buffer.put(serializedPayload);
        buffer.putInt(checksum);
        
        return buffer.array();
    }

    /**
     * Parse a complete message packet.
     */
    public static Message fromPacket(byte[] packet) {
        ByteBuffer buffer = ByteBuffer.wrap(packet);
        
        int restLength = buffer.getInt(); // Length of type + payload + checksum
        Logger.info("fromPacket: restLength = " + restLength);
        
        MessageType type = MessageType.fromValue(buffer.get() & 0xFF);
        Logger.info("fromPacket: parsed message type = " + type);
        
        // Payload length = restLength - type(1) - checksum(4)
        int payloadLength = restLength - 5;
        Logger.info("fromPacket: payloadLength = " + payloadLength + " (restLength=" + restLength + " - 5)");
        
        byte[] payload = new byte[payloadLength];
        buffer.get(payload);
        
        int checksum = buffer.getInt();
        Logger.info("fromPacket: checksum = " + checksum);
        
        Message message = createMessage(type);
        message.payload = payload;
        message.checksum = checksum;
        
        if (!message.verifyChecksum()) {
            Logger.error("fromPacket: Checksum verification failed");
            throw new IllegalArgumentException("Checksum verification failed");
        }
        
        message.deserialize(payload);
        return message;
    }

    /**
     * Factory method to create appropriate message instances.
     */
    private static Message createMessage(MessageType type) {
        switch (type) {
            case KEY_EXCHANGE_INIT:
                return new ssh.model.protocol.messages.KeyExchangeMessage(MessageType.KEY_EXCHANGE_INIT);
            case KEY_EXCHANGE_REPLY:
                return new ssh.model.protocol.messages.KeyExchangeMessage(MessageType.KEY_EXCHANGE_REPLY);
            case AUTH_REQUEST:
                return new ssh.model.protocol.messages.AuthMessage(MessageType.AUTH_REQUEST);
            case AUTH_SUCCESS:
                return new ssh.model.protocol.messages.AuthMessage(MessageType.AUTH_SUCCESS);
            case AUTH_FAILURE:
                return new ssh.model.protocol.messages.AuthMessage(MessageType.AUTH_FAILURE);
            case SERVICE_REQUEST:
                return new ssh.model.protocol.messages.ServiceMessage(MessageType.SERVICE_REQUEST);
            case SERVICE_ACCEPT:
                return new ssh.model.protocol.messages.ServiceMessage(MessageType.SERVICE_ACCEPT);
            case SHELL_COMMAND:
                return new ssh.model.protocol.messages.ShellMessage(MessageType.SHELL_COMMAND);
            case SHELL_RESULT:
                return new ssh.model.protocol.messages.ShellMessage(MessageType.SHELL_RESULT);
            case FILE_UPLOAD_REQUEST:
                return new ssh.model.protocol.messages.FileTransferMessage(MessageType.FILE_UPLOAD_REQUEST);
            case FILE_DOWNLOAD_REQUEST:
                return new ssh.model.protocol.messages.FileTransferMessage(MessageType.FILE_DOWNLOAD_REQUEST);
            case FILE_DATA:
                return new ssh.model.protocol.messages.FileTransferMessage(MessageType.FILE_DATA);
            case FILE_ACK:
                return new ssh.model.protocol.messages.FileTransferMessage(MessageType.FILE_ACK);
            case ERROR:
                return new ssh.model.protocol.messages.ErrorMessage();
            case DISCONNECT:
                return new ssh.model.protocol.messages.DisconnectMessage();
            case RELOAD_USERS:
                return new ssh.model.protocol.messages.ReloadUsersMessage();
            default:
                throw new IllegalArgumentException("Unsupported message type: " + type);
        }
    }

    // Getters and setters
    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public byte[] getPayload() {
        return payload;
    }

    public void setPayload(byte[] payload) {
        this.payload = payload;
    }

    public int getChecksum() {
        return checksum;
    }

    public void setChecksum(int checksum) {
        this.checksum = checksum;
    }
} 