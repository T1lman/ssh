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
    // New: Pending synchronous responses (e.g., shell, file transfer)
    private final Map<String, CompletableFuture<Message>> pendingResponses = new ConcurrentHashMap<>();
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
     * Send a shell command to the server and get the response asynchronously (after dispatcher is started).
     */
    public CompletableFuture<Message> sendShellCommandAsync(String command) {
        try {
            if (!authenticated) {
                CompletableFuture<Message> failed = new CompletableFuture<>();
                failed.completeExceptionally(new IllegalStateException("Not authenticated"));
                return failed;
            }
            ShellMessage shellMessage = new ShellMessage(MessageType.SHELL_COMMAND);
            shellMessage.setCommand(command);
            shellMessage.setWorkingDirectory(this.workingDirectory);
            String requestId = java.util.UUID.randomUUID().toString();
            shellMessage.setRequestId(requestId);
            CompletableFuture<Message> future = new CompletableFuture<>();
            pendingResponses.put(requestId, future);
            protocolHandler.sendMessage(shellMessage);
            return future;
        } catch (Exception e) {
            CompletableFuture<Message> failed = new CompletableFuture<>();
            failed.completeExceptionally(e);
            return failed;
        }
    }

    /**
     * Get the current working directory.
     */
    public String getWorkingDirectory() {
        return workingDirectory;
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
        Logger.info("[PortForward] Starting local port forward accept loop on port " + localPort);
        ServerSocket serverSocket = new ServerSocket(localPort);
        portForwardThreadPool.submit(() -> {
            while (!serverSocket.isClosed()) {
                try {
                    Logger.info("[PortForward] Waiting for local client connection on port " + localPort);
                    Socket localClient = serverSocket.accept();
                    Logger.info("[PortForward] Accepted local client connection: " + localClient);
                    String connectionId = UUID.randomUUID().toString();
                    activeForwards.put(connectionId, localClient);
                    PortForwardRequestMessage req = new PortForwardRequestMessage(
                        PortForwardRequestMessage.ForwardType.LOCAL,
                        localPort, remoteHost, remotePort
                    );
                    req.setConnectionId(connectionId);
                    Logger.info("[PortForward] Sending PORT_FORWARD_REQUEST for connectionId=" + connectionId + ", localPort=" + localPort + ", remoteHost=" + remoteHost + ", remotePort=" + remotePort);
                    CompletableFuture<PortForwardAcceptMessage> future = new CompletableFuture<>();
                    pendingPortForwards.put(connectionId, future);
                    Logger.info("[PortForward] Put future for connectionId=" + connectionId);
                    protocolHandler.sendMessage(req);
                    Logger.info("[PortForward] Waiting for PORT_FORWARD_ACCEPT for connectionId=" + connectionId);
                    PortForwardAcceptMessage accept = null;
                    try {
                        accept = future.get(10, java.util.concurrent.TimeUnit.SECONDS);
                        Logger.info("[PortForward] Received PORT_FORWARD_ACCEPT for connectionId=" + connectionId + ": " + (accept != null ? accept.isSuccess() : "null"));
                    } catch (Exception ex) {
                        Logger.error("[PortForward] Exception while waiting for PORT_FORWARD_ACCEPT: " + ex);
                    }
                    if (accept == null) {
                        Logger.error("[PortForward] Did not receive PORT_FORWARD_ACCEPT for connectionId=" + connectionId);
                        localClient.close();
                        activeForwards.remove(connectionId);
                        // Do NOT remove the future here; let the dispatcher clean up
                        continue;
                    }
                    // Start relaying
                    portForwardThreadPool.submit(() -> relayLocalToRemote(connectionId, localClient));
                } catch (Exception e) {
                    Logger.error("[PortForward] Error in local port forward accept loop: " + e);
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
            Logger.info("[Dispatcher] processIncomingMessages started");
            while (isConnected()) {
                try {
                    Logger.info("[Dispatcher] Top of loop, about to receive message...");
                    Message msg = protocolHandler.receiveMessage();
                    Logger.info("[Dispatcher] Received message: " + (msg != null ? msg.getType() : "null"));
                    if (msg == null) {
                        Logger.info("processIncomingMessages: Connection closed or EOF reached, exiting loop.");
                        break;
                    }
                    if (msg instanceof PortForwardAcceptMessage) {
                        PortForwardAcceptMessage acceptMsg = (PortForwardAcceptMessage) msg;
                        Logger.info("[Dispatcher] Completing future for connectionId=" + acceptMsg.getConnectionId());
                        CompletableFuture<PortForwardAcceptMessage> future = pendingPortForwards.get(acceptMsg.getConnectionId());
                        if (future != null) {
                            future.complete(acceptMsg);
                            pendingPortForwards.remove(acceptMsg.getConnectionId());
                            Logger.info("[Dispatcher] Removed future for connectionId=" + acceptMsg.getConnectionId());
                        } else {
                            Logger.warn("[Dispatcher] No future found for connectionId=" + acceptMsg.getConnectionId());
                        }
                    } else if (msg instanceof ShellMessage) {
                        ShellMessage shellMsg = (ShellMessage) msg;
                        String reqId = shellMsg.getRequestId();
                        Logger.info("[Dispatcher] Received ShellMessage: type=" + shellMsg.getType() + ", requestId=" + reqId);
                        if (reqId != null) {
                            CompletableFuture<Message> future = pendingResponses.get(reqId);
                            if (future != null) {
                                Logger.info("[Dispatcher] Completing future for shell requestId=" + reqId + ", type=" + shellMsg.getType());
                                future.complete(shellMsg);
                                pendingResponses.remove(reqId);
                                Logger.info("[Dispatcher] Completed and removed future for shell requestId=" + reqId);
                            } else {
                                Logger.warn("[Dispatcher] No pending future for shell requestId=" + reqId);
                            }
                        }
                    } else if (msg instanceof FileTransferMessage) {
                        FileTransferMessage ftMsg = (FileTransferMessage) msg;
                        String reqId = ftMsg.getRequestId();
                        if (reqId != null) {
                            CompletableFuture<Message> future = pendingResponses.get(reqId);
                            if (future != null) {
                                future.complete(ftMsg);
                                pendingResponses.remove(reqId);
                                Logger.info("[Dispatcher] Completed and removed future for file transfer requestId=" + reqId);
                            } else {
                                Logger.warn("[Dispatcher] No pending future for file transfer requestId=" + reqId);
                            }
                        }
                    } else if (msg instanceof PortForwardDataMessage) {
                        handlePortForwardData((PortForwardDataMessage) msg);
                    } else if (msg instanceof PortForwardCloseMessage) {
                        handlePortForwardClose((PortForwardCloseMessage) msg);
                    } else {
                        Logger.warn("[Dispatcher] Received unhandled message type: " + msg.getType());
                    }
                    // ... handle other message types as needed ...
                } catch (IOException e) {
                    // Check if this is a server disconnect
                    if (e.getMessage() != null && e.getMessage().contains("Client disconnected gracefully")) {
                        Logger.info("[Dispatcher] Server disconnected gracefully, breaking out of message processing loop");
                        break;
                    }
                    Logger.error("[Dispatcher] IOException in processIncomingMessages: " + e.getMessage());
                    break;
                } catch (Exception e) {
                    Logger.error("[Dispatcher] Exception in processIncomingMessages: " + e.getMessage());
                    // For other exceptions, break out of the loop
                    break;
                }
            }
            Logger.info("[Dispatcher] processIncomingMessages loop ended");
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

    public CompletableFuture<Message> uploadFileAsync(String localPath, String remotePath) {
        CompletableFuture<Message> resultFuture = new CompletableFuture<>();
        try {
            if (!authenticated) {
                resultFuture.completeExceptionally(new IllegalStateException("Not authenticated"));
                return resultFuture;
            }
            Logger.info("Ensuring dispatcher is running for file upload...");
            if (!isConnected()) {
                resultFuture.completeExceptionally(new IllegalStateException("Not connected"));
                return resultFuture;
            }
            java.io.File localFile = new java.io.File(localPath);
            Logger.info("[FileUpload] Absolute path: " + localFile.getAbsolutePath());
            Logger.info("[FileUpload] Exists: " + localFile.exists() + ", Readable: " + localFile.canRead());
            Logger.info("[FileUpload] File size: " + localFile.length() + " bytes");
            if (!localFile.exists()) {
                resultFuture.completeExceptionally(new IOException("Local file does not exist: " + localPath));
                Logger.error("[FileUpload] File does not exist: " + localFile.getAbsolutePath());
                return resultFuture;
            }
            if (!localFile.canRead()) {
                resultFuture.completeExceptionally(new IOException("Cannot read local file: " + localPath));
                Logger.error("[FileUpload] Cannot read file: " + localFile.getAbsolutePath());
                return resultFuture;
            }
            long fileSize = localFile.length();
            if (fileSize == 0) {
                Logger.warn("[FileUpload] File is 0 bytes: " + localFile.getAbsolutePath());
            }
            String filename = localFile.getName();
            Logger.info("Starting file upload: " + filename + " (" + fileSize + " bytes)");
            if (onStatus != null) onStatus.accept(filename + " (" + fileSize + " bytes)");
            FileTransferMessage uploadRequest = new FileTransferMessage(MessageType.FILE_UPLOAD_REQUEST);
            uploadRequest.setFilename(filename);
            uploadRequest.setFileSize(fileSize);
            uploadRequest.setTargetPath(remotePath);
            String requestId = java.util.UUID.randomUUID().toString();
            uploadRequest.setRequestId(requestId);
            CompletableFuture<Message> ackFuture = new CompletableFuture<>();
            pendingResponses.put(requestId, ackFuture);
            Logger.info("Sending FILE_UPLOAD_REQUEST with requestId: " + requestId);
            protocolHandler.sendMessage(uploadRequest);
            ackFuture.orTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .thenAccept(response -> {
                    if (response.getType() != MessageType.FILE_ACK) {
                        resultFuture.completeExceptionally(new IOException("Expected FILE_ACK, got " + response.getType()));
                        return;
                    }
                    Logger.info("Received response for upload request " + requestId + ": " + response.getType());
                    Logger.info("Starting to send file data after receiving FILE_ACK for requestId: " + requestId);
                    // Send file data in a background thread
                    portForwardThreadPool.submit(() -> {
                        try {
                            final int CHUNK_SIZE = 8192;
                            byte[] buffer = new byte[CHUNK_SIZE];
                            int sequenceNumber = 1;
                            long bytesTransferred = 0;
                            if (fileSize == 0) {
                                FileTransferMessage dataMessage = new FileTransferMessage(MessageType.FILE_DATA);
                                dataMessage.setFilename(filename);
                                dataMessage.setSequenceNumber(sequenceNumber);
                                dataMessage.setLast(true);
                                dataMessage.setData(new byte[0]);
                                dataMessage.setRequestId(requestId);
                                Logger.info("Sending FILE_DATA for 0-byte file, requestId: " + requestId);
                                protocolHandler.sendMessage(dataMessage);
                                bytesTransferred = 0;
                                sequenceNumber++;
                            } else {
                                try (java.io.FileInputStream fis = new java.io.FileInputStream(localFile)) {
                                    int bytesRead;
                                    while ((bytesRead = fis.read(buffer)) != -1) {
                                        FileTransferMessage dataMessage = new FileTransferMessage(MessageType.FILE_DATA);
                                        dataMessage.setFilename(filename);
                                        dataMessage.setSequenceNumber(sequenceNumber);
                                        dataMessage.setLast(bytesRead < CHUNK_SIZE);
                                        dataMessage.setData(java.util.Arrays.copyOf(buffer, bytesRead));
                                        dataMessage.setRequestId(requestId);
                                        Logger.info("Sending FILE_DATA chunk " + sequenceNumber + " for requestId: " + requestId);
                                        protocolHandler.sendMessage(dataMessage);
                                        bytesTransferred += bytesRead;
                                        sequenceNumber++;
                                        int percentage = (int) ((bytesTransferred * 100) / fileSize);
                                        if (onStatus != null) onStatus.accept(percentage + "%");
                                        Logger.info("Sent chunk " + (sequenceNumber - 1) + ", bytes: " + bytesTransferred + "/" + fileSize);
                                    }
                                }
                            }
                            Logger.info("All file data sent, waiting for final ACK for requestId: " + requestId);
                            CompletableFuture<Message> finalAckFuture = new CompletableFuture<>();
                            pendingResponses.put(requestId, finalAckFuture);
                            finalAckFuture.orTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                                .thenAccept(finalAck -> {
                                    Logger.info("File upload completed successfully for requestId: " + requestId);
                                    resultFuture.complete(finalAck);
                                })
                                .exceptionally(finalAckEx -> {
                                    Logger.error("Error waiting for final ACK: " + finalAckEx.getMessage(), finalAckEx);
                                    resultFuture.completeExceptionally(finalAckEx);
                                    return null;
                                });
                        } catch (Exception e) {
                            Logger.error("Error in file data sending thread: " + e.getMessage(), e);
                            resultFuture.completeExceptionally(e);
                        }
                    });
                })
                .exceptionally(ackEx -> {
                    Logger.error("Error waiting for initial FILE_ACK: " + ackEx.getMessage(), ackEx);
                    resultFuture.completeExceptionally(ackEx);
                    return null;
                });
        } catch (Exception e) {
            Logger.error("Error in uploadFileAsync: " + e.getMessage(), e);
            resultFuture.completeExceptionally(e);
        }
        return resultFuture;
    }

    public CompletableFuture<Message> downloadFileAsync(String remotePath, String localPath) {
        try {
            if (!authenticated) {
                CompletableFuture<Message> failed = new CompletableFuture<>();
                failed.completeExceptionally(new IllegalStateException("Not authenticated"));
                return failed;
            }
            FileTransferMessage downloadRequest = new FileTransferMessage(MessageType.FILE_DOWNLOAD_REQUEST);
            downloadRequest.setTargetPath(remotePath);
            String requestId = java.util.UUID.randomUUID().toString();
            downloadRequest.setRequestId(requestId);
            CompletableFuture<Message> future = new CompletableFuture<>();
            pendingResponses.put(requestId, future);
            protocolHandler.sendMessage(downloadRequest);
            return future.thenCompose(response -> {
                if (response.getType() != MessageType.FILE_ACK) {
                    CompletableFuture<Message> failed = new CompletableFuture<>();
                    failed.completeExceptionally(new IOException("Expected FILE_ACK, got " + response.getType()));
                    return failed;
                }
                final java.io.File localFile = new java.io.File(localPath);
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(localFile)) {
                    boolean done = false;
                    while (!done) {
                        CompletableFuture<Message> chunkFuture = new CompletableFuture<>();
                        pendingResponses.put(requestId, chunkFuture);
                        Message chunkMsg = chunkFuture.get();
                        if (chunkMsg.getType() == MessageType.FILE_DATA) {
                            FileTransferMessage dataMsg = (FileTransferMessage) chunkMsg;
                            fos.write(dataMsg.getDataBytes());
                            if (dataMsg.isLast()) {
                                done = true;
                            }
                        } else if (chunkMsg.getType() == MessageType.FILE_ACK) {
                            done = true;
                        } else {
                            throw new IOException("Unexpected message type during file download: " + chunkMsg.getType());
                        }
                    }
                } catch (Exception e) {
                    CompletableFuture<Message> failed = new CompletableFuture<>();
                    failed.completeExceptionally(e);
                    return failed;
                }
                CompletableFuture<Message> completed = new CompletableFuture<>();
                completed.complete(response);
                return completed;
            });
        } catch (Exception e) {
            CompletableFuture<Message> failed = new CompletableFuture<>();
            failed.completeExceptionally(e);
            return failed;
        }
    }
} 