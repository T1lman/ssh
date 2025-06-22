package ssh.client;

import ssh.client.ui.AuthCredentials;
import ssh.client.ui.ClientUI;
import ssh.client.ui.ServerInfo;
import ssh.crypto.DiffieHellmanKeyExchange;
import ssh.crypto.RSAKeyGenerator;
import ssh.crypto.SymmetricEncryption;
import ssh.protocol.Message;
import ssh.protocol.MessageType;
import ssh.protocol.ProtocolHandler;
import ssh.protocol.messages.AuthMessage;
import ssh.protocol.messages.KeyExchangeMessage;
import ssh.protocol.messages.ShellMessage;
import ssh.utils.Logger;

import java.io.IOException;
import java.net.Socket;
import java.security.KeyPair;
import java.util.HashMap;
import java.util.Map;
import java.nio.charset.StandardCharsets;

/**
 * Handles client connection to SSH server.
 */
public class ClientConnection {
    private ServerInfo serverInfo;
    private AuthCredentials credentials;
    private ClientUI ui;
    private Socket socket;
    private ProtocolHandler protocolHandler;
    private DiffieHellmanKeyExchange keyExchange;
    private SymmetricEncryption encryption;
    private KeyPair clientKeyPair;
    private boolean connected;
    private boolean authenticated;
    private String workingDirectory;
    private String sessionId;

    public ClientConnection(ServerInfo serverInfo, AuthCredentials credentials, ClientUI ui) {
        Logger.info("ClientConnection constructor called");
        this.serverInfo = serverInfo;
        this.credentials = credentials;
        this.ui = ui;
        this.connected = false;
        this.authenticated = false;
        this.workingDirectory = System.getProperty("user.home"); // Default to home directory
    }

    /**
     * Connect to the server.
     */
    public boolean connect() {
        try {
            Logger.info("Connecting to " + serverInfo.getHost() + ":" + serverInfo.getPort());
            socket = new Socket(serverInfo.getHost(), serverInfo.getPort());
            
            // Set socket timeouts to prevent hanging
            socket.setSoTimeout(30000); // 30 second read timeout
            socket.setTcpNoDelay(true); // Disable Nagle's algorithm for better performance
            
            protocolHandler = new ProtocolHandler(socket.getInputStream(), socket.getOutputStream());
            connected = true;
            Logger.info("Connected successfully");
            return true;
        } catch (IOException e) {
            Logger.error("Connection failed: " + e.getMessage());
            ui.displayError("Connection failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Perform key exchange with the server.
     */
    public boolean performKeyExchange() {
        try {
            Logger.info("Starting key exchange...");
            
            // Initialize DH key exchange
            keyExchange = new DiffieHellmanKeyExchange();
            keyExchange.generateKeyPair();
            Logger.info("Generated DH key pair");

            // Send key exchange init message
            KeyExchangeMessage initMessage = new KeyExchangeMessage(MessageType.KEY_EXCHANGE_INIT);
            initMessage.setDhPublicKey(keyExchange.getPublicKeyBytes());
            initMessage.setClientId("SSH-2.0-JavaSSH");
            Logger.info("Created key exchange init message");

            protocolHandler.sendMessage(initMessage);
            Logger.info("Sent key exchange init message");

            // Receive key exchange reply
            Logger.info("Waiting for key exchange reply...");
            Message replyMessage = protocolHandler.receiveMessage();
            Logger.info("Received reply message: " + replyMessage.getType());
            
            if (replyMessage.getType() != MessageType.KEY_EXCHANGE_REPLY) {
                Logger.error("Expected KEY_EXCHANGE_REPLY, got " + replyMessage.getType());
                ui.displayError("Expected KEY_EXCHANGE_REPLY, got " + replyMessage.getType());
                return false;
            }

            KeyExchangeMessage keyReply = (KeyExchangeMessage) replyMessage;
            keyExchange.setOtherPublicKey(keyReply.getDhPublicKeyBytes());
            Logger.info("Set server's public key");

            // Compute shared secret
            byte[] sharedSecret = keyExchange.computeSharedSecret();
            Logger.info("Computed shared secret, length: " + sharedSecret.length);

            // Initialize symmetric encryption
            encryption = new SymmetricEncryption();
            encryption.initializeKey(sharedSecret);
            Logger.info("Initialized symmetric encryption");

            // Enable encryption in protocol handler AFTER receiving the reply
            protocolHandler.enableEncryption(encryption);
            Logger.info("Enabled encryption in protocol handler");

            // Store the session ID from the server
            this.sessionId = keyReply.getSessionId();
            if (this.sessionId == null || this.sessionId.isEmpty()) {
                throw new IOException("Server did not provide a session ID.");
            }
            Logger.info("Received session ID: " + this.sessionId);

            return true;

        } catch (Exception e) {
            Logger.error("Key exchange failed: " + e.getMessage());
            e.printStackTrace();
            ui.displayError("Key exchange failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Authenticate with the server.
     */
    public boolean authenticate() {
        try {
            Logger.info("Starting authentication...");
            
            // Prepare authentication message
            AuthMessage authMessage = new AuthMessage(MessageType.AUTH_REQUEST);
            authMessage.setUsername(serverInfo.getUsername());
            authMessage.setAuthType(credentials.getAuthType());
            Logger.info("Preparing auth for user: " + serverInfo.getUsername() + ", type: " + credentials.getAuthType());

            if (credentials.isPasswordAuth()) {
                authMessage.setPassword(credentials.getPassword());
                Logger.info("Using password authentication");
            } else if (credentials.isPublicKeyAuth()) {
                // Load client key pair
                clientKeyPair = RSAKeyGenerator.loadKeyPair(
                    credentials.getPrivateKeyPath(), 
                    credentials.getPublicKeyPath()
                );
                
                authMessage.setPublicKey(RSAKeyGenerator.getPublicKeyString(clientKeyPair.getPublic()));
                
                // Sign the session ID received from the server
                byte[] sessionData = this.sessionId.getBytes(StandardCharsets.UTF_8);
                byte[] signature = RSAKeyGenerator.sign(sessionData, clientKeyPair.getPrivate());
                authMessage.setSignature(signature);
                Logger.info("Using public key authentication, signed session ID.");
            }

            // Send authentication request
            protocolHandler.sendMessage(authMessage);
            Logger.info("Sent authentication request");

            // Receive authentication response
            Message response = protocolHandler.receiveMessage();
            Logger.info("Received auth response: " + response.getType());
            
            if (response.getType() != MessageType.AUTH_SUCCESS && response.getType() != MessageType.AUTH_FAILURE) {
                Logger.error("Expected AUTH_SUCCESS or AUTH_FAILURE, got " + response.getType());
                ui.displayError("Expected AUTH_SUCCESS or AUTH_FAILURE, got " + response.getType());
                return false;
            }

            AuthMessage authResponse = (AuthMessage) response;
            boolean success = authResponse.isSuccess();
            
            if (success) {
                authenticated = true;
                Logger.info("Authentication successful");
                
                // Send service request for shell
                if (!sendServiceRequest("shell")) {
                    Logger.error("Failed to send service request");
                    return false;
                }
            } else {
                Logger.error("Authentication failed");
            }

            return success;

        } catch (Exception e) {
            Logger.error("Authentication failed: " + e.getMessage());
            e.printStackTrace();
            ui.displayError("Authentication failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Send a service request to the server.
     */
    public boolean sendServiceRequest(String service) {
        try {
            Logger.info("Sending service request for: " + service);
            
            ssh.protocol.messages.ServiceMessage serviceMessage = new ssh.protocol.messages.ServiceMessage(MessageType.SERVICE_REQUEST);
            serviceMessage.setService(service);
            
            protocolHandler.sendMessage(serviceMessage);
            Logger.info("Sent service request");
            
            // Receive service accept response
            Message response = protocolHandler.receiveMessage();
            Logger.info("Received service response: " + response.getType());
            
            if (response.getType() != MessageType.SERVICE_ACCEPT) {
                Logger.error("Expected SERVICE_ACCEPT, got " + response.getType());
                return false;
            }
            
            Logger.info("Service request accepted");
            return true;
            
        } catch (Exception e) {
            Logger.error("Service request failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Send a shell command to the server.
     */
    public void sendShellCommand(String command) throws Exception {
        if (!authenticated) {
            throw new IllegalStateException("Not authenticated");
        }

        ShellMessage shellMessage = new ShellMessage(MessageType.SHELL_COMMAND);
        shellMessage.setCommand(command);
        shellMessage.setWorkingDirectory(this.workingDirectory);

        protocolHandler.sendMessage(shellMessage);
    }

    /**
     * Receive shell response from the server.
     */
    public String receiveShellResponse() throws Exception {
        if (!authenticated) {
            throw new IllegalStateException("Not authenticated");
        }

        Message response = protocolHandler.receiveMessage();
        if (response.getType() != MessageType.SHELL_RESULT) {
            throw new IOException("Expected SHELL_RESULT, got " + response.getType());
        }

        ShellMessage shellResult = (ShellMessage) response;
        
        // Update the working directory
        if (shellResult.getWorkingDirectory() != null && !shellResult.getWorkingDirectory().isEmpty()) {
            this.workingDirectory = shellResult.getWorkingDirectory();
        }

        if (shellResult.getExitCode() == 0) {
            return shellResult.getStdout();
        } else {
            return shellResult.getStderr();
        }
    }

    /**
     * Get the current working directory.
     */
    public String getWorkingDirectory() {
        return workingDirectory;
    }

    /**
     * Upload a file to the server.
     */
    public void uploadFile(String localPath, String remotePath) throws Exception {
        if (!authenticated) {
            throw new IllegalStateException("Not authenticated");
        }

        // Check if local file exists
        java.io.File localFile = new java.io.File(localPath);
        if (!localFile.exists()) {
            throw new IOException("Local file does not exist: " + localPath);
        }
        if (!localFile.canRead()) {
            throw new IOException("Cannot read local file: " + localPath);
        }

        long fileSize = localFile.length();
        String filename = localFile.getName();
        
        Logger.info("Starting file upload: " + filename + " (" + fileSize + " bytes)");
        ui.showFileTransferProgress(filename, 0);

        // Send file upload request
        ssh.protocol.messages.FileTransferMessage uploadRequest = new ssh.protocol.messages.FileTransferMessage(MessageType.FILE_UPLOAD_REQUEST);
        uploadRequest.setFilename(filename);
        uploadRequest.setFileSize(fileSize);
        uploadRequest.setTargetPath(remotePath);
        
        protocolHandler.sendMessage(uploadRequest);
        Logger.info("Sent file upload request");

        // Wait for acknowledgment
        Message response = protocolHandler.receiveMessage();
        if (response.getType() != MessageType.FILE_ACK) {
            throw new IOException("Expected FILE_ACK, got " + response.getType());
        }

        // Read and send file in chunks
        final int CHUNK_SIZE = 8192; // 8KB chunks
        byte[] buffer = new byte[CHUNK_SIZE];
        int sequenceNumber = 1;
        long bytesTransferred = 0;

        try (java.io.FileInputStream fis = new java.io.FileInputStream(localFile)) {
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                // Create file data message
                ssh.protocol.messages.FileTransferMessage dataMessage = new ssh.protocol.messages.FileTransferMessage(MessageType.FILE_DATA);
                dataMessage.setFilename(filename);
                dataMessage.setSequenceNumber(sequenceNumber);
                dataMessage.setLast(bytesRead < CHUNK_SIZE); // Last chunk if we read less than buffer size
                
                // Set the actual data (only the bytes we read)
                byte[] actualData = new byte[bytesRead];
                System.arraycopy(buffer, 0, actualData, 0, bytesRead);
                dataMessage.setData(actualData);

                // Send the chunk
                protocolHandler.sendMessage(dataMessage);
                
                bytesTransferred += bytesRead;
                sequenceNumber++;
                
                // Update progress
                int percentage = (int) ((bytesTransferred * 100) / fileSize);
                ui.showFileTransferProgress(filename, percentage);
                
                Logger.info("Sent chunk " + (sequenceNumber - 1) + ", bytes: " + bytesTransferred + "/" + fileSize);
                
                // Small delay to prevent overwhelming the network
                Thread.sleep(10);
            }
        }

        // Wait for final acknowledgment
        response = protocolHandler.receiveMessage();
        if (response.getType() != MessageType.FILE_ACK) {
            throw new IOException("Expected final FILE_ACK, got " + response.getType());
        }

        ssh.protocol.messages.FileTransferMessage finalAck = (ssh.protocol.messages.FileTransferMessage) response;
        Logger.info("File upload completed: " + finalAck.getMessage());
        ui.showFileTransferProgress(filename, 100);
    }

    /**
     * Download a file from the server.
     */
    public void downloadFile(String remotePath, String localPath) throws Exception {
        if (!authenticated) {
            throw new IllegalStateException("Not authenticated");
        }

        String filename = new java.io.File(remotePath).getName();
        Logger.info("Starting file download: " + filename);
        ui.showFileTransferProgress(filename, 0);

        // Send file download request
        ssh.protocol.messages.FileTransferMessage downloadRequest = new ssh.protocol.messages.FileTransferMessage(MessageType.FILE_DOWNLOAD_REQUEST);
        downloadRequest.setFilename(filename);
        downloadRequest.setTargetPath(remotePath);
        
        protocolHandler.sendMessage(downloadRequest);
        Logger.info("Sent file download request");

        // The first message from the server will be metadata or an error
        Message response = protocolHandler.receiveMessage();

        // Handle potential error message from server (e.g., file not found)
        if (response.getType() == MessageType.ERROR) {
            ssh.protocol.messages.ErrorMessage errorMsg = (ssh.protocol.messages.ErrorMessage) response;
            throw new IOException("Server error: " + errorMsg.getErrorMessage());
        }

        if (response.getType() != MessageType.FILE_DATA) {
            throw new IOException("Expected first chunk as FILE_DATA, got " + response.getType());
        }

        ssh.protocol.messages.FileTransferMessage firstDataChunk = (ssh.protocol.messages.FileTransferMessage) response;
        long totalBytes = firstDataChunk.getFileSize();
        Logger.info("Expecting file size: " + totalBytes + " bytes");
        
        // Create local file
        java.io.File localFile = new java.io.File(localPath);
        java.io.File parentDir = localFile.getParentFile();
        if (parentDir != null) {
            parentDir.mkdirs();
        }

        long bytesReceived = 0;
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(localFile)) {
            // Write the first chunk of data
            byte[] firstChunkData = firstDataChunk.getDataBytes();
            if (firstChunkData != null && firstChunkData.length > 0) {
                fos.write(firstChunkData);
                bytesReceived += firstChunkData.length;
            }

            // Read subsequent chunks until the total file size is reached
            while (bytesReceived < totalBytes) {
                Message subsequentResponse = protocolHandler.receiveMessage();
                if (subsequentResponse.getType() != MessageType.FILE_DATA) {
                    throw new IOException("Expected FILE_DATA, got " + subsequentResponse.getType());
                }

                ssh.protocol.messages.FileTransferMessage dataChunk = (ssh.protocol.messages.FileTransferMessage) subsequentResponse;
                byte[] chunkData = dataChunk.getDataBytes();
                if (chunkData != null && chunkData.length > 0) {
                    fos.write(chunkData);
                    bytesReceived += chunkData.length;
                }
                
                // Update progress
                int percentage = (int) ((bytesReceived * 100) / totalBytes);
                ui.showFileTransferProgress(filename, percentage);
            }
        }

        // Send final acknowledgment
        ssh.protocol.messages.FileTransferMessage ack = new ssh.protocol.messages.FileTransferMessage(MessageType.FILE_ACK);
        ack.setStatus("completed");
        ack.setMessage("File download completed successfully");
        
        protocolHandler.sendMessage(ack);
        
        Logger.info("File download completed: " + filename + " (" + bytesReceived + " bytes)");
        ui.showFileTransferProgress(filename, 100);
    }

    /**
     * Send a disconnect message to the server.
     */
    public void sendDisconnect() {
        try {
            if (protocolHandler != null) {
                ssh.protocol.messages.ErrorMessage disconnectMsg = new ssh.protocol.messages.ErrorMessage();
                disconnectMsg.setType(ssh.protocol.MessageType.DISCONNECT);
                disconnectMsg.setErrorMessage("Client disconnecting");
                protocolHandler.sendMessage(disconnectMsg);
            }
        } catch (Exception e) {
            // Ignore errors on disconnect
        }
    }

    /**
     * Disconnect from the server.
     */
    public void disconnect() {
        try {
            sendDisconnect();
            if (protocolHandler != null) {
                protocolHandler.close();
                protocolHandler = null;
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
                socket = null;
            }
            
            // Clean up large objects to help with garbage collection
            if (keyExchange != null) {
                keyExchange = null;
            }
            if (encryption != null) {
                encryption = null;
            }
            if (clientKeyPair != null) {
                clientKeyPair = null;
            }
            
            connected = false;
            authenticated = false;
            Logger.info("Disconnected from server");
            
            // Force garbage collection
            System.gc();
            
        } catch (IOException e) {
            Logger.error("Error during disconnect: " + e.getMessage());
        }
    }

    /**
     * Check if connected to server.
     */
    public boolean isConnected() {
        return connected && socket != null && !socket.isClosed();
    }

    /**
     * Check if authenticated with server.
     */
    public boolean isAuthenticated() {
        return authenticated;
    }
} 