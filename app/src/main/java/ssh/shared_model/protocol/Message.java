package ssh.shared_model.protocol;

import ssh.shared_model.crypto.HMACUtil;
import ssh.shared_model.protocol.messages.ShellMessage;
import ssh.shared_model.protocol.messages.ServiceMessage;
import ssh.shared_model.protocol.messages.ErrorMessage;
import ssh.shared_model.protocol.messages.KeyExchangeMessage;
import ssh.shared_model.protocol.messages.FileTransferMessage;
import ssh.shared_model.protocol.messages.AuthMessage;
import ssh.shared_model.protocol.messages.DisconnectMessage;
import ssh.shared_model.protocol.messages.ReloadUsersMessage;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.CRC32;
import com.fasterxml.jackson.databind.ObjectMapper;

import ssh.shared_model.protocol.MessageType;
import ssh.utils.Logger;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonSubTypes;

/**
 * Abstract base class for all SSH protocol messages.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "typeName"
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = ssh.shared_model.protocol.messages.ShellMessage.class, name = "ShellMessage"),
    @JsonSubTypes.Type(value = ssh.shared_model.protocol.messages.ServiceMessage.class, name = "ServiceMessage"),
    @JsonSubTypes.Type(value = ssh.shared_model.protocol.messages.ErrorMessage.class, name = "ErrorMessage"),
    @JsonSubTypes.Type(value = ssh.shared_model.protocol.messages.KeyExchangeMessage.class, name = "KeyExchangeMessage"),
    @JsonSubTypes.Type(value = ssh.shared_model.protocol.messages.FileTransferMessage.class, name = "FileTransferMessage"),
    @JsonSubTypes.Type(value = ssh.shared_model.protocol.messages.AuthMessage.class, name = "AuthMessage"),
    @JsonSubTypes.Type(value = ssh.shared_model.protocol.messages.PortForwardRequestMessage.class, name = "PortForwardRequestMessage"),
    @JsonSubTypes.Type(value = ssh.shared_model.protocol.messages.PortForwardAcceptMessage.class, name = "PortForwardAcceptMessage"),
    @JsonSubTypes.Type(value = ssh.shared_model.protocol.messages.PortForwardDataMessage.class, name = "PortForwardDataMessage"),
    @JsonSubTypes.Type(value = ssh.shared_model.protocol.messages.PortForwardCloseMessage.class, name = "PortForwardCloseMessage")
})
public abstract class Message {
    private MessageType type;
    private byte[] payload;
    private int sequenceNumber;
    private byte[] mac;

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
     * Create a complete message packet with length, type, payload, and MAC.
     */
    public byte[] toPacket(byte[] hmacKey) {
        byte[] serializedPayload = serialize();
        this.payload = serializedPayload;
        int restLength = 1 + 4 + serializedPayload.length + 32;
        ByteBuffer buffer = ByteBuffer.allocate(4 + restLength);
        buffer.putInt(restLength);
        buffer.put((byte) type.getValue());
        buffer.putInt(sequenceNumber);
        buffer.put(serializedPayload);
        byte[] mac = new byte[32];
        if (hmacKey != null && type != MessageType.KEY_EXCHANGE_INIT && type != MessageType.KEY_EXCHANGE_REPLY) {
            mac = ssh.shared_model.crypto.HMACUtil.hmacSha256(hmacKey, buffer.array(), 4, restLength - 32);
        }
        buffer.put(mac);
        return buffer.array();
    }

    /**
     * Parse a complete message packet.
     */
    public static Message fromPacket(byte[] packet, byte[] hmacKey) {
        ByteBuffer buffer = ByteBuffer.wrap(packet);
        int restLength = buffer.getInt();
        MessageType type = MessageType.fromValue(buffer.get() & 0xFF);
        int seq = buffer.getInt();
        int payloadLength = restLength - 4 - 1 - 32;
        byte[] payload = new byte[payloadLength];
        buffer.get(payload);
        byte[] mac = new byte[32];
        buffer.get(mac);
        if (hmacKey != null && type != MessageType.KEY_EXCHANGE_INIT && type != MessageType.KEY_EXCHANGE_REPLY) {
            byte[] macCheck = ssh.shared_model.crypto.HMACUtil.hmacSha256(hmacKey, packet, 4, restLength - 32);
            if (!java.util.Arrays.equals(mac, macCheck)) throw new SecurityException("MAC check failed!");
        }
        Message message = createMessage(type);
        message.sequenceNumber = seq;
        message.payload = payload;
        message.mac = mac;
        message.deserialize(payload);
        return message;
    }

    /**
     * Factory method to create appropriate message instances.
     */
    private static Message createMessage(MessageType type) {
        switch (type) {
            case KEY_EXCHANGE_INIT:
                return new ssh.shared_model.protocol.messages.KeyExchangeMessage(MessageType.KEY_EXCHANGE_INIT);
            case KEY_EXCHANGE_REPLY:
                return new ssh.shared_model.protocol.messages.KeyExchangeMessage(MessageType.KEY_EXCHANGE_REPLY);
            case AUTH_REQUEST:
                return new ssh.shared_model.protocol.messages.AuthMessage(MessageType.AUTH_REQUEST);
            case AUTH_SUCCESS:
                return new ssh.shared_model.protocol.messages.AuthMessage(MessageType.AUTH_SUCCESS);
            case AUTH_FAILURE:
                return new ssh.shared_model.protocol.messages.AuthMessage(MessageType.AUTH_FAILURE);
            case SERVICE_REQUEST:
                return new ssh.shared_model.protocol.messages.ServiceMessage(MessageType.SERVICE_REQUEST);
            case SERVICE_ACCEPT:
                return new ssh.shared_model.protocol.messages.ServiceMessage(MessageType.SERVICE_ACCEPT);
            case SHELL_COMMAND:
                return new ssh.shared_model.protocol.messages.ShellMessage(MessageType.SHELL_COMMAND);
            case SHELL_RESULT:
                return new ssh.shared_model.protocol.messages.ShellMessage(MessageType.SHELL_RESULT);
            case FILE_UPLOAD_REQUEST:
                return new ssh.shared_model.protocol.messages.FileTransferMessage(MessageType.FILE_UPLOAD_REQUEST);
            case FILE_DOWNLOAD_REQUEST:
                return new ssh.shared_model.protocol.messages.FileTransferMessage(MessageType.FILE_DOWNLOAD_REQUEST);
            case FILE_DATA:
                return new ssh.shared_model.protocol.messages.FileTransferMessage(MessageType.FILE_DATA);
            case FILE_ACK:
                return new ssh.shared_model.protocol.messages.FileTransferMessage(MessageType.FILE_ACK);
            case ERROR:
                return new ssh.shared_model.protocol.messages.ErrorMessage();
            case DISCONNECT:
                return new ssh.shared_model.protocol.messages.DisconnectMessage();
            case RELOAD_USERS:
                return new ssh.shared_model.protocol.messages.ReloadUsersMessage();
            case PORT_FORWARD_REQUEST:
                return new ssh.shared_model.protocol.messages.PortForwardRequestMessage();
            case PORT_FORWARD_ACCEPT:
                return new ssh.shared_model.protocol.messages.PortForwardAcceptMessage();
            case PORT_FORWARD_DATA:
                return new ssh.shared_model.protocol.messages.PortForwardDataMessage();
            case PORT_FORWARD_CLOSE:
                return new ssh.shared_model.protocol.messages.PortForwardCloseMessage();
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

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public byte[] getMac() {
        return mac;
    }

    public void setMac(byte[] mac) {
        this.mac = mac;
    }
} 