package ssh.client.model;

import ssh.client.model.AuthCredentials;
import ssh.client.model.ServerInfo;
import ssh.shared_model.crypto.DiffieHellmanKeyExchange;
import ssh.shared_model.crypto.RSAKeyGenerator;
import ssh.shared_model.crypto.SymmetricEncryption;
import ssh.shared_model.protocol.Message;
import ssh.shared_model.protocol.MessageType;
import ssh.shared_model.protocol.ProtocolHandler;
import ssh.shared_model.protocol.messages.AuthMessage;
import ssh.shared_model.protocol.messages.KeyExchangeMessage;
import ssh.shared_model.protocol.messages.ShellMessage;
import ssh.shared_model.protocol.messages.ServiceMessage;
import ssh.shared_model.protocol.messages.DisconnectMessage;
import ssh.shared_model.protocol.messages.ReloadUsersMessage;
import ssh.shared_model.protocol.messages.FileTransferMessage;
import ssh.shared_model.protocol.messages.ErrorMessage;
import ssh.utils.Logger;
import ssh.shared_model.protocol.messages.PortForwardRequestMessage;
import ssh.shared_model.protocol.messages.PortForwardAcceptMessage;
import ssh.shared_model.protocol.messages.PortForwardDataMessage;
import ssh.shared_model.protocol.messages.PortForwardCloseMessage;

import java.io.IOException;
import java.net.Socket;
import java.security.KeyPair;
import java.util.HashMap;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.net.ServerSocket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.UUID;

/**
 * Handles client connection to SSH server.
 */
public class ClientConnection {
    private ServerInfo serverInfo;
    private AuthCredentials credentials;
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

    // Port forwarding state
    private final Map<String, Socket> activeForwards = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<PortForwardAcceptMessage>> pendingPortForwards = new ConcurrentHashMap<>();
    private ExecutorService portForwardThreadPool = Executors.newCachedThreadPool();

    public ClientConnection(ServerInfo serverInfo, AuthCredentials credentials) {
        Logger.info("ClientConnection constructor called");
        this.serverInfo = serverInfo;
        this.credentials = credentials;
        this.connected = false;
        this.authenticated = false;
        this.workingDirectory = System.getProperty("user.home"); // Default to home directory
    }

    /**
     * Connect to the server.
     */
    public boolean connect() {
        Logger.debug("ClientConnection.connect() called");
        
        try {
            Logger.debug("Creating socket to " + serverInfo.getHost() + ":" + serverInfo.getPort());
            socket = new Socket(serverInfo.getHost(), serverInfo.getPort());
            socket.setTcpNoDelay(true);
            Logger.debug("Socket created successfully");
            
            // Create protocol handler
            Logger.debug("Creating protocol handler");
            protocolHandler = new ProtocolHandler(socket.getInputStream(), socket.getOutputStream());
            connected = true;
            Logger.debug("Connection successful");
            Logger.info("Connected successfully");
            return true;
            
        } catch (Exception e) {
            Logger.debug("Connection failed with exception: " + e.getMessage());
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
            Logger.debug("performKeyExchange() called");
            
            // Initialize Diffie-Hellman key exchange
            Logger.debug("Initializing DH key exchange");
            keyExchange = new DiffieHellmanKeyExchange();
            keyExchange.generateKeyPair();
            Logger.debug("Generated DH key pair");
            
            // Create key exchange init message
            Logger.debug("Creating key exchange init message");
            KeyExchangeMessage initMessage = new KeyExchangeMessage(MessageType.KEY_EXCHANGE_INIT);
            initMessage.setDhPublicKey(keyExchange.getPublicKeyBytes());
            Logger.debug("Created key exchange init message");
            
            // Send init message
            Logger.debug("Sending key exchange init message");
            protocolHandler.sendMessage(initMessage);
            Logger.debug("Sent key exchange init message");
            
            // Wait for reply
            Logger.debug("Waiting for key exchange reply...");
            Message replyMessage = protocolHandler.receiveMessage();
            Logger.debug("Received reply message: " + replyMessage.getType());
            
            if (replyMessage.getType() == MessageType.KEY_EXCHANGE_REPLY) {
                KeyExchangeMessage reply = (KeyExchangeMessage) replyMessage;
                // --- Server Authentication ---
                // 1. Load expected server public key from client directory
                String expectedServerPubKeyPath = "data/client/server_keys/server_rsa_key.pub";
                java.io.File expectedKeyFile = new java.io.File(expectedServerPubKeyPath);
                if (!expectedKeyFile.exists()) {
                    Logger.error("Expected server public key not found in client directory! Aborting connection.");
                    return false;
                }
                java.security.PublicKey expectedServerPubKey = ssh.shared_model.crypto.RSAKeyGenerator.loadPublicKey(expectedServerPubKeyPath);
                String expectedServerPubKeyString = ssh.shared_model.crypto.RSAKeyGenerator.getPublicKeyString(expectedServerPubKey);
                // 2. Compare with received server public key
                if (!expectedServerPubKeyString.equals(reply.getServerPublicKey())) {
                    Logger.error("Server public key does not match expected key! Possible MITM attack.");
                    return false;
                }
                // 3. Verify server's signature over DH public key
                boolean sigValid = ssh.shared_model.crypto.RSAKeyGenerator.verify(
                    reply.getDhPublicKeyBytes(),
                    reply.getSignatureBytes(),
                    expectedServerPubKey
                );
                if (!sigValid) {
                    Logger.error("Server signature verification failed! Possible MITM attack.");
                    return false;
                }
                Logger.info("Server authentication succeeded: public key and signature verified.");
                // --- End server authentication ---
                keyExchange.setOtherPublicKey(reply.getDhPublicKeyBytes());
                byte[] sharedSecret = keyExchange.computeSharedSecret();
                
                // Store the session ID from the server
                this.sessionId = reply.getSessionId();
                if (this.sessionId == null || this.sessionId.isEmpty()) {
                    Logger.warn("Server did not provide a session ID, generating one");
                    this.sessionId = java.util.UUID.randomUUID().toString();
                }
                Logger.info("Session ID: " + this.sessionId);
                
                // Initialize encryption and HMAC
                encryption = new SymmetricEncryption();
                encryption.initializeKey(sharedSecret);
                protocolHandler.enableEncryption(encryption, sharedSecret);
                Logger.info("Encryption and HMAC enabled in protocol handler");
                
                Logger.info("Key exchange completed successfully");
                return true;
            } else {
                Logger.error("Unexpected message type during key exchange: " + replyMessage.getType());
                return false;
            }
            
        } catch (Exception e) {
            Logger.error("Key exchange failed: " + e.getMessage());
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
            
            ssh.shared_model.protocol.messages.ServiceMessage serviceMessage = new ssh.shared_model.protocol.messages.ServiceMessage(MessageType.SERVICE_REQUEST);
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
            ssh.shared_model.protocol.messages.ErrorMessage errorMsg = (ssh.shared_model.protocol.messages.ErrorMessage) response;
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
        // Call onResult callback if available
        if (onResult != null) onResult.accept(output.toString());
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
        ssh.shared_model.protocol.messages.FileTransferMessage uploadRequest = new ssh.shared_model.protocol.messages.FileTransferMessage(MessageType.FILE_UPLOAD_REQUEST);
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
                ssh.shared_model.protocol.messages.FileTransferMessage dataMessage = new ssh.shared_model.protocol.messages.FileTransferMessage(MessageType.FILE_DATA);
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

        ssh.shared_model.protocol.messages.FileTransferMessage finalAck = (ssh.shared_model.protocol.messages.FileTransferMessage) response;
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
        ssh.shared_model.protocol.messages.FileTransferMessage downloadRequest = new ssh.shared_model.protocol.messages.FileTransferMessage(MessageType.FILE_DOWNLOAD_REQUEST);
        downloadRequest.setFilename(filename);
        downloadRequest.setTargetPath(remotePath);
        
        protocolHandler.sendMessage(downloadRequest);
        Logger.info("Sent file download request");

        // The first message from the server will be metadata or an error
        Message response = protocolHandler.receiveMessage();

        // Handle potential error message from server (e.g., file not found)
        if (response.getType() == MessageType.ERROR) {
            ssh.shared_model.protocol.messages.ErrorMessage errorMsg = (ssh.shared_model.protocol.messages.ErrorMessage) response;
            throw new IOException("Server error: " + errorMsg.getErrorMessage());
        }

        if (response.getType() != MessageType.FILE_DATA) {
            throw new IOException("Expected first chunk as FILE_DATA, got " + response.getType());
        }

        ssh.shared_model.protocol.messages.FileTransferMessage firstDataChunk = (ssh.shared_model.protocol.messages.FileTransferMessage) response;
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

                ssh.shared_model.protocol.messages.FileTransferMessage dataChunk = (ssh.shared_model.protocol.messages.FileTransferMessage) subsequentResponse;
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
        ssh.shared_model.protocol.messages.FileTransferMessage ack = new ssh.shared_model.protocol.messages.FileTransferMessage(MessageType.FILE_ACK);
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
                ssh.shared_model.protocol.messages.DisconnectMessage disconnectMsg = new ssh.shared_model.protocol.messages.DisconnectMessage();
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
                ssh.shared_model.protocol.messages.ReloadUsersMessage reloadMsg = new ssh.shared_model.protocol.messages.ReloadUsersMessage();
                protocolHandler.sendMessage(reloadMsg);
                
                // Wait for acknowledgment
                Message response = protocolHandler.receiveMessage();
                if (response.getType() == MessageType.SERVICE_ACCEPT) {
                    Logger.info("Server acknowledged user database reload");
                } else if (response.getType() == MessageType.ERROR) {
                    ssh.shared_model.protocol.messages.ErrorMessage errorMsg = (ssh.shared_model.protocol.messages.ErrorMessage) response;
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

    // --- Port Forwarding ---
    /**
     * Request local port forwarding: listen on localPort, forward to remoteHost:remotePort via SSH.
     */
    public void requestLocalPortForward(int localPort, String remoteHost, int remotePort) throws Exception {
        ServerSocket serverSocket = new ServerSocket(localPort);
        portForwardThreadPool.submit(() -> {
            while (!serverSocket.isClosed()) {
                try {
                    Socket localClient = serverSocket.accept();
                    String connectionId = UUID.randomUUID().toString();
                    activeForwards.put(connectionId, localClient);
                    PortForwardRequestMessage req = new PortForwardRequestMessage(
                        PortForwardRequestMessage.ForwardType.LOCAL,
                        localPort, remoteHost, remotePort
                    );
                    req.setConnectionId(connectionId);
                    Logger.info("[PortForward] Sending PORT_FORWARD_REQUEST for connectionId=" + connectionId + ", localPort=" + localPort + ", remoteHost=" + remoteHost + ", remotePort=" + remotePort);
                    // Send the request and wait for accept
                    CompletableFuture<PortForwardAcceptMessage> future = new CompletableFuture<>();
                    pendingPortForwards.put(connectionId, future);
                    protocolHandler.sendMessage(req);
                    PortForwardAcceptMessage accept = future.get(10, java.util.concurrent.TimeUnit.SECONDS);
                    if (accept == null) {
                        Logger.error("[PortForward] Did not receive PORT_FORWARD_ACCEPT for connectionId=" + connectionId);
                        localClient.close();
                        activeForwards.remove(connectionId);
                        continue;
                    }
                    // Start relaying
                    portForwardThreadPool.submit(() -> relayLocalToRemote(connectionId, localClient));
                } catch (Exception e) {
                    Logger.error("[PortForward] Error in local port forward accept loop: " + e.getMessage());
                }
            }
        });
    }

    private void relayLocalToRemote(String connectionId, Socket localClient) {
        try {
            byte[] buffer = new byte[8192];
            while (!localClient.isClosed()) {
                int read = localClient.getInputStream().read(buffer);
                if (read == -1) break;
                String data = Base64.getEncoder().encodeToString(java.util.Arrays.copyOf(buffer, read));
                PortForwardDataMessage dataMsg = new PortForwardDataMessage(connectionId, data);
                protocolHandler.sendMessage(dataMsg);
            }
        } catch (Exception e) {
            Logger.error("Relay local->remote error: " + e.getMessage());
        } finally {
            // Do NOT close or remove the socket here. Wait for PortForwardCloseMessage.
            // try { localClient.close(); } catch (Exception ignored) {}
            // activeForwards.remove(connectionId);
            // Notify server to close
            try { protocolHandler.sendMessage(new PortForwardCloseMessage(connectionId)); } catch (Exception ignored) {}
        }
    }

    /**
     * Request remote port forwarding: ask server to listen on remotePort, forward to localHost:localPort.
     */
    public void requestRemotePortForward(int remotePort, String localHost, int localPort) throws Exception {
        // Generate a unique connectionId for this forward
        String connectionId = java.util.UUID.randomUUID().toString();
        PortForwardRequestMessage req = new PortForwardRequestMessage(
            PortForwardRequestMessage.ForwardType.REMOTE, remotePort, localHost, localPort);
        CompletableFuture<PortForwardAcceptMessage> future = new CompletableFuture<>();
        pendingPortForwards.put(connectionId, future);
        req.setConnectionId(connectionId);
        protocolHandler.sendMessage(req);
        // Wait for PortForwardAcceptMessage (handled by dispatcher)
        PortForwardAcceptMessage resp = future.get();
        if (resp.isSuccess()) {
            // Connect to localHost:localPort
            Socket localSocket = new Socket(localHost, localPort);
            activeForwards.put(connectionId, localSocket);
            // Relay data in both directions
            portForwardThreadPool.submit(() -> relayRemoteToLocal(connectionId, localSocket));
            portForwardThreadPool.submit(() -> relayLocalToRemoteRemoteForward(connectionId, localSocket));
        }
    }

    private void relayRemoteToLocal(String connectionId, Socket localSocket) {
        // Data from server to local
        // Already handled by handlePortForwardData
    }

    private void relayLocalToRemoteRemoteForward(String connectionId, Socket localSocket) {
        try {
            byte[] buffer = new byte[8192];
            while (!localSocket.isClosed()) {
                int read = localSocket.getInputStream().read(buffer);
                if (read == -1) break;
                String data = Base64.getEncoder().encodeToString(java.util.Arrays.copyOf(buffer, read));
                PortForwardDataMessage dataMsg = new PortForwardDataMessage(connectionId, data);
                protocolHandler.sendMessage(dataMsg);
            }
        } catch (Exception e) {
            Logger.error("Relay local->remote (remote forward) error: " + e.getMessage());
        } finally {
            try { localSocket.close(); } catch (Exception ignored) {}
            activeForwards.remove(connectionId);
            try { protocolHandler.sendMessage(new PortForwardCloseMessage(connectionId)); } catch (Exception ignored) {}
        }
    }

    /**
     * Start processing incoming messages in a background thread (for port forwarding, etc).
     */
    public void processIncomingMessages() {
        portForwardThreadPool.submit(() -> {
            while (isConnected()) {
                try {
                    Message msg = protocolHandler.receiveMessage();
                    if (msg == null) {
                        Logger.info("processIncomingMessages: Connection closed or EOF reached, exiting loop.");
                        break;
                    }
                    if (msg instanceof PortForwardAcceptMessage) {
                        PortForwardAcceptMessage acceptMsg = (PortForwardAcceptMessage) msg;
                        CompletableFuture<PortForwardAcceptMessage> future = pendingPortForwards.remove(acceptMsg.getConnectionId());
                        if (future != null) {
                            future.complete(acceptMsg);
                        }
                    } else if (msg instanceof PortForwardDataMessage) {
                        handlePortForwardData((PortForwardDataMessage) msg);
                    } else if (msg instanceof PortForwardCloseMessage) {
                        handlePortForwardClose((PortForwardCloseMessage) msg);
                    }
                    // ... handle other message types as needed ...
                } catch (Exception e) {
                    Logger.error("Error in processIncomingMessages: " + e.getMessage());
                    // If the error is EOF or disconnect, exit the loop
                    if (e.getMessage() != null && e.getMessage().toLowerCase().contains("end of stream")) {
                        Logger.info("processIncomingMessages: Detected end of stream, exiting loop.");
                        break;
                    }
                }
            }
            Logger.info("processIncomingMessages: Exiting and cleaning up.");
            disconnect();
        });
    }

    /**
     * Handle incoming port forward data from the server.
     */
    private void handlePortForwardData(PortForwardDataMessage msg) {
        Socket localClient = activeForwards.get(msg.getConnectionId());
        if (localClient != null && !localClient.isClosed()) {
            try {
                byte[] data = Base64.getDecoder().decode(msg.getData());
                Logger.info("[handlePortForwardData] Writing " + data.length + " bytes to local client");
                localClient.getOutputStream().write(data);
                localClient.getOutputStream().flush();
            } catch (Exception e) {
                Logger.error("Error writing to local client: " + e.getMessage());
            }
        }
    }

    /**
     * Handle incoming port forward close from the server.
     */
    private void handlePortForwardClose(PortForwardCloseMessage msg) {
        Socket localClient = activeForwards.remove(msg.getConnectionId());
        if (localClient != null) {
            try { localClient.close(); } catch (Exception ignored) {}
        }
    }
} 