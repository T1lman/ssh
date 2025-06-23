package ssh.client.model;

import ssh.client.model.AuthCredentials;
import ssh.client.model.ServerInfo;
import ssh.client.view.ClientUI;
import ssh.model.crypto.DiffieHellmanKeyExchange;
import ssh.model.crypto.RSAKeyGenerator;
import ssh.model.crypto.SymmetricEncryption;
import ssh.model.protocol.Message;
import ssh.model.protocol.MessageType;
import ssh.model.protocol.ProtocolHandler;
import ssh.model.protocol.messages.AuthMessage;
import ssh.model.protocol.messages.KeyExchangeMessage;
import ssh.model.protocol.messages.ShellMessage;
import ssh.model.utils.Logger;

import java.io.IOException;
import java.net.Socket;
import java.security.KeyPair;
import java.util.HashMap;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

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
    private Consumer<String> onError;
    private Consumer<String> onStatus;
    private Consumer<String> onResult;

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
            System.out.println("DEBUG: ClientConnection.connect() called");
            Logger.info("Connecting to " + serverInfo.getHost() + ":" + serverInfo.getPort());
            System.out.println("DEBUG: Creating socket to " + serverInfo.getHost() + ":" + serverInfo.getPort());
            
            // Create socket with timeout to prevent hanging
            socket = new Socket();
            socket.connect(new java.net.InetSocketAddress(serverInfo.getHost(), serverInfo.getPort()), 10000); // 10 second timeout
            
            System.out.println("DEBUG: Socket created successfully");
            
            // Set socket timeouts to prevent hanging
            socket.setSoTimeout(30000); // 30 second read timeout
            socket.setTcpNoDelay(true); // Disable Nagle's algorithm for better performance
            
            System.out.println("DEBUG: Creating protocol handler");
            protocolHandler = new ProtocolHandler(socket.getInputStream(), socket.getOutputStream());
            connected = true;
            System.out.println("DEBUG: Connection successful");
            Logger.info("Connected successfully");
            return true;
        } catch (IOException e) {
            System.out.println("DEBUG: Connection failed with exception: " + e.getMessage());
            Logger.error("Connection failed: " + e.getMessage());
            if (onError != null) onError.accept("Connection failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Perform key exchange with the server.
     */
    public boolean performKeyExchange() {
        try {
            System.out.println("DEBUG: performKeyExchange() called");
            Logger.info("Starting key exchange...");
            
            // Initialize DH key exchange
            System.out.println("DEBUG: Initializing DH key exchange");
            keyExchange = new DiffieHellmanKeyExchange();
            keyExchange.generateKeyPair();
            System.out.println("DEBUG: Generated DH key pair");
            Logger.info("Generated DH key pair");

            // Send key exchange init message
            System.out.println("DEBUG: Creating key exchange init message");
            KeyExchangeMessage initMessage = new KeyExchangeMessage(MessageType.KEY_EXCHANGE_INIT);
            initMessage.setDhPublicKey(keyExchange.getPublicKeyBytes());
            initMessage.setClientId("SSH-2.0-JavaSSH");
            System.out.println("DEBUG: Created key exchange init message");
            Logger.info("Created key exchange init message");

            System.out.println("DEBUG: Sending key exchange init message");
            protocolHandler.sendMessage(initMessage);
            System.out.println("DEBUG: Sent key exchange init message");
            Logger.info("Sent key exchange init message");

            // Receive key exchange reply
            System.out.println("DEBUG: Waiting for key exchange reply...");
            Logger.info("Waiting for key exchange reply...");
            Message replyMessage = protocolHandler.receiveMessage();
            System.out.println("DEBUG: Received reply message: " + replyMessage.getType());
            Logger.info("Received reply message: " + replyMessage.getType());
            
            if (replyMessage.getType() != MessageType.KEY_EXCHANGE_REPLY) {
                Logger.error("Expected KEY_EXCHANGE_REPLY, got " + replyMessage.getType());
                if (onError != null) onError.accept("Expected KEY_EXCHANGE_REPLY, got " + replyMessage.getType());
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
            if (onError != null) onError.accept("Key exchange failed: " + e.getMessage());
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

            if (credentials.requiresPassword()) {
                authMessage.setPassword(credentials.getPassword());
                Logger.info("Using password authentication");
            }
            
            if (credentials.requiresPublicKey()) {
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

            if (response.getType() == MessageType.AUTH_SUCCESS) {
                AuthMessage authResponse = (AuthMessage) response;
                this.authenticated = authResponse.isSuccess();
                Logger.info("Authentication successful: " + this.authenticated);
                return this.authenticated;
            } else if (response.getType() == MessageType.AUTH_FAILURE) {
                AuthMessage authResponse = (AuthMessage) response;
                Logger.error("Authentication failed: " + authResponse.getMessage());
                this.authenticated = false;
                return false;
            } else {
                Logger.error("Unexpected response type: " + response.getType());
                return false;
            }

        } catch (Exception e) {
            Logger.error("Authentication failed: " + e.getMessage());
            e.printStackTrace();
            if (onError != null) onError.accept("Authentication failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Send a service request to the server.
     */
    public boolean sendServiceRequest(String service) {
        try {
            Logger.info("Sending service request for: " + service);
            
            ssh.model.protocol.messages.ServiceMessage serviceMessage = new ssh.model.protocol.messages.ServiceMessage(MessageType.SERVICE_REQUEST);
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
            if (onError != null) onError.accept("Service request failed: " + e.getMessage());
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
        if (response.getType() == MessageType.ERROR) {
            ssh.model.protocol.messages.ErrorMessage errorMsg = (ssh.model.protocol.messages.ErrorMessage) response;
            if (onError != null) onError.accept("Server error: " + errorMsg.getErrorMessage());
            return "[SERVER ERROR] " + errorMsg.getErrorMessage();
        }
        if (response.getType() != MessageType.SHELL_RESULT) {
            throw new IOException("Expected SHELL_RESULT, got " + response.getType());
        }

        ShellMessage shellResult = (ShellMessage) response;
        
        // Update the working directory
        if (shellResult.getWorkingDirectory() != null && !shellResult.getWorkingDirectory().isEmpty()) {
            this.workingDirectory = shellResult.getWorkingDirectory();
        }

        // Always return both stdout and stderr concatenated
        StringBuilder output = new StringBuilder();
        if (shellResult.getStdout() != null && !shellResult.getStdout().isEmpty()) {
            output.append(shellResult.getStdout());
        }
        if (shellResult.getStderr() != null && !shellResult.getStderr().isEmpty()) {
            output.append(shellResult.getStderr());
        }
        // Only call onResult for non-GUI clients
        if (onResult != null && !(ui instanceof ssh.client.view.JavaFXClientUI)) onResult.accept(output.toString());
        return output.toString();
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
        if (onStatus != null) onStatus.accept(filename + " (" + fileSize + " bytes)");

        // Send file upload request
        ssh.model.protocol.messages.FileTransferMessage uploadRequest = new ssh.model.protocol.messages.FileTransferMessage(MessageType.FILE_UPLOAD_REQUEST);
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
                ssh.model.protocol.messages.FileTransferMessage dataMessage = new ssh.model.protocol.messages.FileTransferMessage(MessageType.FILE_DATA);
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
                if (onStatus != null) onStatus.accept(percentage + "%");
                Logger.info("Sent chunk " + (sequenceNumber - 1) + ", bytes: " + bytesTransferred + "/" + fileSize);
            }
        }

        // Wait for final acknowledgment
        response = protocolHandler.receiveMessage();
        if (response.getType() != MessageType.FILE_ACK) {
            throw new IOException("Expected final FILE_ACK, got " + response.getType());
        }

        ssh.model.protocol.messages.FileTransferMessage finalAck = (ssh.model.protocol.messages.FileTransferMessage) response;
        Logger.info("File upload completed: " + finalAck.getMessage());
        if (onStatus != null) onStatus.accept("100%");
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
        if (onStatus != null) onStatus.accept(filename);

        // Send file download request
        ssh.model.protocol.messages.FileTransferMessage downloadRequest = new ssh.model.protocol.messages.FileTransferMessage(MessageType.FILE_DOWNLOAD_REQUEST);
        downloadRequest.setFilename(filename);
        downloadRequest.setTargetPath(remotePath);
        
        protocolHandler.sendMessage(downloadRequest);
        Logger.info("Sent file download request");

        // The first message from the server will be metadata or an error
        Message response = protocolHandler.receiveMessage();

        // Handle potential error message from server (e.g., file not found)
        if (response.getType() == MessageType.ERROR) {
            ssh.model.protocol.messages.ErrorMessage errorMsg = (ssh.model.protocol.messages.ErrorMessage) response;
            throw new IOException("Server error: " + errorMsg.getErrorMessage());
        }

        if (response.getType() != MessageType.FILE_DATA) {
            throw new IOException("Expected first chunk as FILE_DATA, got " + response.getType());
        }

        ssh.model.protocol.messages.FileTransferMessage firstDataChunk = (ssh.model.protocol.messages.FileTransferMessage) response;
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

                ssh.model.protocol.messages.FileTransferMessage dataChunk = (ssh.model.protocol.messages.FileTransferMessage) subsequentResponse;
                byte[] chunkData = dataChunk.getDataBytes();
                if (chunkData != null && chunkData.length > 0) {
                    fos.write(chunkData);
                    bytesReceived += chunkData.length;
                }
                
                // Update progress
                int percentage = (int) ((bytesReceived * 100) / totalBytes);
                if (onStatus != null) onStatus.accept(percentage + "%");
            }
        }

        // Send final acknowledgment
        ssh.model.protocol.messages.FileTransferMessage ack = new ssh.model.protocol.messages.FileTransferMessage(MessageType.FILE_ACK);
        ack.setStatus("completed");
        ack.setMessage("File download completed successfully");
        
        protocolHandler.sendMessage(ack);
        
        Logger.info("File download completed: " + filename + " (" + bytesReceived + " bytes)");
        if (onStatus != null) onStatus.accept("100%");
    }

    /**
     * Send a disconnect message to the server.
     */
    public void sendDisconnect() {
        try {
            if (protocolHandler != null) {
                ssh.model.protocol.messages.DisconnectMessage disconnectMsg = new ssh.model.protocol.messages.DisconnectMessage();
                protocolHandler.sendMessage(disconnectMsg);
            }
        } catch (Exception e) {
            // Ignore errors on disconnect
        }
    }

    /**
     * Send a reload users request to the server.
     */
    public void sendReloadUsers() {
        try {
            if (protocolHandler != null && authenticated) {
                ssh.model.protocol.messages.ReloadUsersMessage reloadMsg = new ssh.model.protocol.messages.ReloadUsersMessage();
                protocolHandler.sendMessage(reloadMsg);
                
                // Wait for acknowledgment
                Message response = protocolHandler.receiveMessage();
                if (response.getType() == MessageType.SERVICE_ACCEPT) {
                    Logger.info("Server acknowledged user database reload");
                } else if (response.getType() == MessageType.ERROR) {
                    ssh.model.protocol.messages.ErrorMessage errorMsg = (ssh.model.protocol.messages.ErrorMessage) response;
                    Logger.error("Server failed to reload user database: " + errorMsg.getErrorMessage());
                }
            }
        } catch (Exception e) {
            Logger.error("Failed to send reload users request: " + e.getMessage());
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

    public boolean isActive() {
        return isConnected() && isAuthenticated();
    }

    public ServerInfo getServerInfo() {
        return serverInfo;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setOnError(Consumer<String> onError) { this.onError = onError; }
    public void setOnStatus(Consumer<String> onStatus) { this.onStatus = onStatus; }
    public void setOnResult(Consumer<String> onResult) { this.onResult = onResult; }
} 