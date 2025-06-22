package ssh.protocol;

import ssh.crypto.SymmetricEncryption;
import ssh.utils.Logger;

import java.io.*;
import java.nio.ByteBuffer;

/**
 * Handles the SSH protocol communication between client and server.
 */
public class ProtocolHandler {
    private InputStream inputStream;
    private OutputStream outputStream;
    private SymmetricEncryption encryption;
    private boolean encryptionEnabled = false;
    private static final int MAX_MESSAGE_LENGTH = 1024 * 1024; // 1MB

    public ProtocolHandler(InputStream inputStream, OutputStream outputStream) {
        this.inputStream = inputStream;
        this.outputStream = outputStream;
    }

    /**
     * Send a message to the remote peer.
     */
    public void sendMessage(Message message) throws IOException {
        byte[] packet = message.toPacket();
        
        Logger.info("Sending message: " + message.getType() + ", length: " + packet.length);
        
        if (encryptionEnabled && encryption != null) {
            try {
                packet = encryptPayload(packet);
                Logger.info("Message encrypted, new length: " + packet.length);
                
                // For encrypted messages, add length prefix
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
        try {
            Logger.info("Waiting to receive message...");
            
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
                int encryptedLength = ByteBuffer.wrap(lengthBytes).getInt();
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
                
                // Decrypt the message
                try {
                    Logger.info("Decrypting encrypted message, length: " + encryptedMessage.length);
                    byte[] decryptedMessage = decryptPayload(encryptedMessage);
                    Logger.info("Message decrypted successfully, new length: " + decryptedMessage.length);
                    
                    // Parse the decrypted message
                    Message message = Message.fromPacket(decryptedMessage);
                    Logger.info("Received encrypted message: " + message.getType());
                    
                    // Clear large arrays to free memory
                    encryptedMessage = null;
                    decryptedMessage = null;
                    System.gc();
                    
                    return message;
                    
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
                int messageLength = ByteBuffer.wrap(lengthBytes).getInt();
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
                
                // DEBUG: Log the first few bytes to see the message structure
                StringBuilder hexDump = new StringBuilder();
                for (int i = 0; i < Math.min(20, messageBytes.length); i++) {
                    hexDump.append(String.format("%02X ", messageBytes[i] & 0xFF));
                }
                Logger.info("Message body (first 20 bytes): " + hexDump.toString());
                
                // DEBUG: Try to parse the message type manually
                if (messageBytes.length > 0) {
                    int messageType = messageBytes[0] & 0xFF;
                    Logger.info("Raw message type byte: " + messageType);
                    try {
                        MessageType type = MessageType.fromValue(messageType);
                        Logger.info("Parsed message type: " + type);
                    } catch (Exception e) {
                        Logger.error("Failed to parse message type " + messageType + ": " + e.getMessage());
                    }
                }
                
                // Reconstruct the full packet for fromPacket parsing
                ByteBuffer fullPacket = ByteBuffer.allocate(4 + messageBytes.length);
                fullPacket.putInt(messageLength); // The length we read earlier
                fullPacket.put(messageBytes);     // The message body we read
                byte[] fullPacketBytes = fullPacket.array();
                
                Message message = Message.fromPacket(fullPacketBytes);
                Logger.info("Received message: " + message.getType());
                
                // Clear large arrays to free memory
                messageBytes = null;
                fullPacketBytes = null;
                System.gc();
                
                return message;
            }
        } catch (OutOfMemoryError e) {
            Logger.error("OutOfMemoryError in receiveMessage: " + e.getMessage());
            System.gc(); // Force garbage collection
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
    public void enableEncryption(SymmetricEncryption encryption) {
        this.encryption = encryption;
        this.encryptionEnabled = true;
        Logger.info("Encryption enabled - subsequent messages will be encrypted/decrypted");
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