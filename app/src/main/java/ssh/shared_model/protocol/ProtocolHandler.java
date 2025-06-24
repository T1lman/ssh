package ssh.shared_model.protocol;

import java.io.*;
import java.nio.ByteBuffer;

import ssh.shared_model.crypto.SymmetricEncryption;
import ssh.utils.Logger;

/**
 * Handles the SSH protocol communication between client and server.
 */
public class ProtocolHandler {
    private InputStream inputStream;
    private OutputStream outputStream;
    private SymmetricEncryption encryption;
    private boolean encryptionEnabled = false;
    private static final int MAX_MESSAGE_LENGTH = 1024 * 1024; // 1MB
    private int sendSequenceNumber = 0;
    private int recvSequenceNumber = 0;
    private byte[] hmacKey = null;

    public ProtocolHandler(InputStream inputStream, OutputStream outputStream) {
        this.inputStream = inputStream;
        this.outputStream = outputStream;
    }

    /**
     * Send a message to the remote peer.
     */
    public void sendMessage(Message message) throws IOException {
        // Für Key-Exchange-Nachrichten keinen HMAC-Key verlangen
        boolean isKeyExchange = message.getType() == ssh.shared_model.protocol.MessageType.KEY_EXCHANGE_INIT || message.getType() == ssh.shared_model.protocol.MessageType.KEY_EXCHANGE_REPLY;
        if (!isKeyExchange && hmacKey == null) throw new IOException("HMAC key not initialized");
        message.setSequenceNumber(sendSequenceNumber++);
        byte[] packet = message.toPacket(isKeyExchange ? null : hmacKey);
        Logger.info("Sending message: " + message.getType() + ", length: " + packet.length);
        if (encryptionEnabled && encryption != null) {
            try {
                packet = encryptPayload(packet);
                Logger.info("Message encrypted, new length: " + packet.length);
                ByteBuffer encryptedPacket = ByteBuffer.allocate(4 + packet.length);
                encryptedPacket.putInt(packet.length);
                encryptedPacket.put(packet);
                packet = encryptedPacket.array();
                Logger.info("Added length prefix for encrypted message, total length: " + packet.length);
            } catch (Exception e) {
                throw new IOException("Failed to encrypt payload", e);
            }
        }
        outputStream.write(packet);
        outputStream.flush();
        Logger.info("Message sent successfully");
    }

    /**
     * Receive a message from the remote peer.
     */
    public Message receiveMessage() throws IOException {
        if (hmacKey == null) {
            // Only allow if we expect a key exchange message
            // We'll parse the type byte manually
            byte[] lengthBytes = new byte[4];
            int bytesRead = inputStream.read(lengthBytes);
            if (bytesRead == -1) throw new IOException("Client disconnected gracefully");
            if (bytesRead != 4) throw new IOException("Failed to read message length");
            int messageLength = java.nio.ByteBuffer.wrap(lengthBytes).getInt();
            if (messageLength <= 0 || messageLength > MAX_MESSAGE_LENGTH) throw new IOException("Invalid message length: " + messageLength);
            byte[] messageBytes = new byte[messageLength];
            int totalRead = 0;
            while (totalRead < messageLength) {
                int read = inputStream.read(messageBytes, totalRead, messageLength - totalRead);
                if (read == -1) throw new IOException("Client disconnected gracefully");
                totalRead += read;
            }
            // Type is at offset 0
            int typeByte = messageBytes[0] & 0xFF;
            ssh.shared_model.protocol.MessageType type = ssh.shared_model.protocol.MessageType.fromValue(typeByte);
            if (type == ssh.shared_model.protocol.MessageType.KEY_EXCHANGE_INIT || type == ssh.shared_model.protocol.MessageType.KEY_EXCHANGE_REPLY) {
                byte[] fullPacketBytes = new byte[4 + messageBytes.length];
                System.arraycopy(lengthBytes, 0, fullPacketBytes, 0, 4);
                System.arraycopy(messageBytes, 0, fullPacketBytes, 4, messageBytes.length);
                return ssh.shared_model.protocol.Message.fromPacket(fullPacketBytes, null);
            } else {
                throw new IOException("HMAC key not initialized");
            }
        }
        try {
            Logger.info("Waiting to receive message...");
            byte[] fullPacketBytes;
            if (encryptionEnabled && encryption != null) {
                Logger.info("Reading encrypted message...");
                byte[] lengthBytes = new byte[4];
                int bytesRead = inputStream.read(lengthBytes);
                Logger.info("Read " + bytesRead + " bytes for encrypted message length");
                if (bytesRead == -1) {
                    Logger.info("Client disconnected gracefully (end of stream)");
                    throw new IOException("Client disconnected gracefully");
                }
                if (bytesRead != 4) {
                    Logger.error("Failed to read encrypted message length. Expected 4 bytes, got " + bytesRead);
                    throw new IOException("Failed to read encrypted message length");
                }
                int encryptedLength = java.nio.ByteBuffer.wrap(lengthBytes).getInt();
                Logger.info("Encrypted message length: " + encryptedLength);
                if (encryptedLength <= 0 || encryptedLength > MAX_MESSAGE_LENGTH) {
                    Logger.error("Invalid encrypted message length: " + encryptedLength);
                    throw new IOException("Invalid encrypted message length: " + encryptedLength);
                }
                byte[] encryptedMessage = new byte[encryptedLength];
                int totalRead = 0;
                while (totalRead < encryptedLength) {
                    int read = inputStream.read(encryptedMessage, totalRead, encryptedLength - totalRead);
                    if (read == -1) {
                        Logger.info("Client disconnected gracefully during encrypted message read");
                        throw new IOException("Client disconnected gracefully");
                    }
                    totalRead += read;
                }
                Logger.info("Read " + totalRead + " bytes for encrypted message body");
                try {
                    Logger.info("Decrypting encrypted message, length: " + encryptedMessage.length);
                    byte[] decryptedMessage = decryptPayload(encryptedMessage);
                    Logger.info("Message decrypted successfully, new length: " + decryptedMessage.length);
                    fullPacketBytes = decryptedMessage;
                } catch (Exception e) {
                    Logger.error("Failed to decrypt payload: " + e.getMessage());
                    throw new IOException("Failed to decrypt payload", e);
                }
            } else {
                byte[] lengthBytes = new byte[4];
                int bytesRead = inputStream.read(lengthBytes);
                Logger.info("Read " + bytesRead + " bytes for message length");
                if (bytesRead == -1) {
                    Logger.info("Client disconnected gracefully (end of stream)");
                    throw new IOException("Client disconnected gracefully");
                }
                if (bytesRead != 4) {
                    Logger.error("Failed to read message length. Expected 4 bytes, got " + bytesRead);
                    throw new IOException("Failed to read message length");
                }
                int messageLength = java.nio.ByteBuffer.wrap(lengthBytes).getInt();
                Logger.info("Message length: " + messageLength);
                if (messageLength <= 0 || messageLength > MAX_MESSAGE_LENGTH) {
                    Logger.error("Invalid message length: " + messageLength);
                    throw new IOException("Invalid message length: " + messageLength);
                }
                byte[] messageBytes = new byte[messageLength];
                int totalRead = 0;
                while (totalRead < messageLength) {
                    int read = inputStream.read(messageBytes, totalRead, messageLength - totalRead);
                    if (read == -1) {
                        Logger.info("Client disconnected gracefully during message read");
                        throw new IOException("Client disconnected gracefully");
                    }
                    totalRead += read;
                }
                Logger.info("Read " + totalRead + " bytes for message body (expected " + messageLength + ")");
                fullPacketBytes = new byte[4 + messageBytes.length];
                System.arraycopy(lengthBytes, 0, fullPacketBytes, 0, 4);
                System.arraycopy(messageBytes, 0, fullPacketBytes, 4, messageBytes.length);
            }
            // Type is at offset 4 (after length)
            int typeByte = fullPacketBytes[4] & 0xFF;
            ssh.shared_model.protocol.MessageType messageType = ssh.shared_model.protocol.MessageType.fromValue(typeByte);
            Message message = Message.fromPacket(fullPacketBytes, (messageType == ssh.shared_model.protocol.MessageType.KEY_EXCHANGE_INIT || messageType == ssh.shared_model.protocol.MessageType.KEY_EXCHANGE_REPLY) ? null : hmacKey);
            Logger.info("Received message: " + message.getType());
            // Sequence number check
            if (message.getSequenceNumber() != recvSequenceNumber) {
                Logger.error("Sequence number mismatch! Expected " + recvSequenceNumber + ", got " + message.getSequenceNumber());
                throw new IOException("Sequence number mismatch!");
            }
            recvSequenceNumber++;
            return message;
        } catch (OutOfMemoryError e) {
            Logger.error("OutOfMemoryError in receiveMessage: " + e.getMessage());
            System.gc();
            throw new IOException("OutOfMemoryError while receiving message", e);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            Logger.error("Unexpected error in receiveMessage: " + e.getMessage());
            throw new IOException("Unexpected error while receiving message", e);
        }
    }

    /**
     * Enable encryption for subsequent messages.
     */
    public void enableEncryption(SymmetricEncryption encryption, byte[] sharedSecret) {
        this.encryption = encryption;
        this.encryptionEnabled = true;
        try {
            this.hmacKey = SymmetricEncryption.deriveHmacKey(sharedSecret);
        } catch (Exception e) {
            throw new RuntimeException("Failed to derive HMAC key", e);
        }
        this.sendSequenceNumber = 0;
        this.recvSequenceNumber = 0;
        Logger.info("Encryption and HMAC enabled - subsequent messages will be encrypted/decrypted and authenticated");
    }

    /**
     * Disable encryption.
     */
    public void disableEncryption() {
        this.encryptionEnabled = false;
        this.encryption = null;
        Logger.info("Encryption disabled");
    }

    /**
     * Check if encryption is enabled.
     */
    public boolean isEncryptionEnabled() {
        return encryptionEnabled;
    }

    /**
     * Encrypt payload data.
     */
    private byte[] encryptPayload(byte[] payload) throws Exception {
        if (encryption == null) {
            return payload;
        }
        return encryption.encrypt(payload);
    }

    /**
     * Decrypt payload data.
     */
    private byte[] decryptPayload(byte[] encryptedPayload) throws Exception {
        if (encryption == null) {
            return encryptedPayload;
        }
        return encryption.decrypt(encryptedPayload);
    }

    /**
     * Close the protocol handler and underlying streams.
     */
    public void close() throws IOException {
        try {
            if (inputStream != null) {
                inputStream.close();
                inputStream = null;
            }
            if (outputStream != null) {
                outputStream.close();
                outputStream = null;
            }
            
            // Clean up encryption object
            if (encryption != null) {
                encryption = null;
            }
            
            encryptionEnabled = false;
            Logger.info("Protocol handler closed");
            
            // Force garbage collection
            System.gc();
            
        } catch (IOException e) {
            Logger.error("Error closing protocol handler: " + e.getMessage());
            throw e;
        }
    }
} 