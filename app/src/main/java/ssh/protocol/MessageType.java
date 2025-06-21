package ssh.protocol;

/**
 * Enumeration of all message types in the SSH protocol.
 */
public enum MessageType {
    KEY_EXCHANGE_INIT(1),
    KEY_EXCHANGE_REPLY(2),
    AUTH_REQUEST(3),
    AUTH_SUCCESS(4),
    AUTH_FAILURE(5),
    SERVICE_REQUEST(6),
    SERVICE_ACCEPT(7),
    SHELL_DATA(8),
    SHELL_COMMAND(9),
    SHELL_RESULT(10),
    FILE_UPLOAD_REQUEST(11),
    FILE_DOWNLOAD_REQUEST(12),
    FILE_DATA(13),
    FILE_ACK(14),
    ERROR(15),
    DISCONNECT(16);

    private final int value;

    MessageType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static MessageType fromValue(int value) {
        for (MessageType type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown message type: " + value);
    }
} 