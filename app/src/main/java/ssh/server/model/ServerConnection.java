package ssh.server.model;

import ssh.config.ServerConfig;
import ssh.shared_model.auth.AuthenticationManager;
import ssh.shared_model.crypto.DiffieHellmanKeyExchange;
import ssh.shared_model.crypto.RSAKeyGenerator;
import ssh.shared_model.crypto.SymmetricEncryption;
import ssh.shared_model.protocol.Message;
import ssh.shared_model.protocol.MessageType;
import ssh.shared_model.protocol.ProtocolHandler;
import ssh.shared_model.protocol.messages.AuthMessage;
import ssh.shared_model.protocol.messages.DisconnectMessage;
import ssh.shared_model.protocol.messages.ErrorMessage;
import ssh.shared_model.protocol.messages.FileTransferMessage;
import ssh.shared_model.protocol.messages.KeyExchangeMessage;
import ssh.shared_model.protocol.messages.ReloadUsersMessage;
import ssh.shared_model.protocol.messages.ServiceMessage;
import ssh.shared_model.protocol.messages.ShellMessage;
import ssh.shared_model.shell.ShellExecutor;
import ssh.utils.Logger;

import java.io.IOException;
import java.net.Socket;
import java.security.KeyPair;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Handles a single client connection on the server side.
 */
public class ServerConnection implements Runnable {
    private Socket clientSocket;
    private ProtocolHandler protocolHandler;
    private AuthenticationManager authManager;
    private KeyPair serverKeyPair;
    private ServerConfig config;
    private boolean authenticated;
    private String authenticatedUser;
    private DiffieHellmanKeyExchange keyExchange;
    private SymmetricEncryption encryption;
    private ShellExecutor shellExecutor;
    private String sessionId;
    
    // Event callbacks for MVC compliance
    private Consumer<String> onError;
    private Consumer<String> onStatus;
    private Consumer<String> onAuthenticationResult;
    private Consumer<String> onServiceRequest;
    private Consumer<String> onFileTransferProgress;
    private Consumer<String> onShellCommand;

    public ServerConnection(Socket clientSocket, AuthenticationManager authManager, 
                          KeyPair serverKeyPair, ServerConfig config) {
        this.clientSocket = clientSocket;
        this.authManager = authManager;
        this.serverKeyPair = serverKeyPair;
        this.config = config;
        this.authenticated = false;
        this.authenticatedUser = null;
        this.shellExecutor = new ShellExecutor();
        this.sessionId = UUID.randomUUID().toString();
        
        // Set socket timeouts to prevent hanging
        try {
            clientSocket.setTcpNoDelay(true); // Disable Nagle's algorithm for better performance
        } catch (Exception e) {
            notifyError("Failed to set socket timeout: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        try {
            Logger.info("Server: Starting client connection handler for " + getClientInfo());
            
            // Initialize protocol handler
            Logger.info("Server: Initializing protocol handler");
            protocolHandler = new ProtocolHandler(
                clientSocket.getInputStream(), 
                clientSocket.getOutputStream()
            );
            Logger.info("Server: Protocol handler initialized");

            // Perform key exchange
            Logger.info("Server: Starting key exchange");
            if (!handleKeyExchange()) {
                Logger.error("Server: Key exchange failed for client " + getClientInfo());
                safeDisplayError("Key exchange failed for client " + getClientInfo());
                return;
            }
            Logger.info("Server: Key exchange completed successfully");

            // Handle authentication
            Logger.info("Server: Starting authentication");
            if (!handleAuthentication()) {
                Logger.error("Server: Authentication failed for client " + getClientInfo());
                safeDisplayError("Authentication failed for client " + getClientInfo());
                return;
            }
            Logger.info("Server: Authentication completed successfully");

            // Handle service requests
            Logger.info("Server: Starting service request handling");
            handleServiceRequests();

        } catch (Exception e) {
            Logger.error("Server: Error handling client " + getClientInfo() + ": " + e.getMessage(), e);
            safeDisplayError("Error handling client " + getClientInfo() + ": " + e.getMessage());
        } finally {
            Logger.info("Server: Cleaning up client connection for " + getClientInfo());
            cleanup();
        }
    }

    /**
     * Handle the key exchange phase.
     */
    private boolean handleKeyExchange() throws Exception {
        // Receive key exchange init message
        Message message = protocolHandler.receiveMessage();
        if (message.getType() != MessageType.KEY_EXCHANGE_INIT) {
            safeDisplayError("Expected KEY_EXCHANGE_INIT, got " + message.getType());
            return false;
        }

        KeyExchangeMessage initMessage = (KeyExchangeMessage) message;
        safeDisplayMessage("Received key exchange init from " + initMessage.getClientId());
        Logger.info("Server: Received key exchange init from " + initMessage.getClientId());

        // Initialize DH key exchange
        Logger.info("Server: Initializing DH key exchange");
        keyExchange = new DiffieHellmanKeyExchange();
        keyExchange.generateKeyPair();
        Logger.info("Server: Generated DH key pair");
        keyExchange.setOtherPublicKey(initMessage.getDhPublicKeyBytes());
        Logger.info("Server: Set client's public key");

        // Compute shared secret
        Logger.info("Server: Computing shared secret");
        byte[] sharedSecret = keyExchange.computeSharedSecret();
        Logger.info("Server: Computed shared secret, length: " + sharedSecret.length);

        // Initialize symmetric encryption
        Logger.info("Server: Initializing symmetric encryption");
        encryption = new SymmetricEncryption();
        encryption.initializeKey(sharedSecret);
        Logger.info("Server: Initialized symmetric encryption");

        // Send key exchange reply BEFORE enabling encryption
        Logger.info("Server: Creating key exchange reply message");
        KeyExchangeMessage replyMessage = new KeyExchangeMessage(MessageType.KEY_EXCHANGE_REPLY);
        replyMessage.setDhPublicKey(keyExchange.getPublicKeyBytes());
        replyMessage.setServerId("SSH-2.0-JavaSSH-Server");
        replyMessage.setSessionId(this.sessionId);
        Logger.info("Server: Created reply message, signing DH public key");

        // Sign the DH public key with server's private key
        Logger.info("Server: Starting RSA signature generation");
        byte[] signature = RSAKeyGenerator.sign(
            keyExchange.getPublicKeyBytes(), 
            serverKeyPair.getPrivate()
        );
        Logger.info("Server: RSA signature generated, length: " + signature.length);
        replyMessage.setSignature(signature);
        // Add server public key for client verification
        replyMessage.setServerPublicKey(RSAKeyGenerator.getPublicKeyString(serverKeyPair.getPublic()));

        Logger.info("Server: Sending key exchange reply");
        protocolHandler.sendMessage(replyMessage);
        Logger.info("Server: Key exchange reply sent successfully");
        safeDisplayMessage("Key exchange completed successfully");

        // Enable encryption AFTER sending the reply
        Logger.info("Server: Enabling encryption");
        protocolHandler.enableEncryption(encryption, sharedSecret);
        Logger.info("Encryption and HMAC enabled in protocol handler");

        return true;
    }

    /**
     * Handle the authentication phase.
     */
    private boolean handleAuthentication() throws Exception {
        // Receive authentication request
        Message message = protocolHandler.receiveMessage();
        if (message.getType() != MessageType.AUTH_REQUEST) {
            safeDisplayError("Expected AUTH_REQUEST, got " + message.getType());
            return false;
        }

        AuthMessage authMessage = (AuthMessage) message;
        String username = authMessage.getUsername();
        String authType = authMessage.getAuthType();

        safeDisplayMessage("Authentication request from " + username + " using " + authType);

        // Prepare credentials map
        Map<String, String> credentials = new HashMap<>();
        
        if ("publickey".equals(authType)) {
            credentials.put("publicKey", authMessage.getPublicKey());
            credentials.put("signature", authMessage.getSignature());
            // For public key auth, we need session data for signature verification
            credentials.put("sessionData", java.util.Base64.getEncoder().encodeToString(this.sessionId.getBytes()));
        } else if ("password".equals(authType)) {
            credentials.put("password", authMessage.getPassword());
        } else if ("dual".equals(authType)) {
            // For dual authentication, include both password and public key
            credentials.put("password", authMessage.getPassword());
            credentials.put("publicKey", authMessage.getPublicKey());
            credentials.put("signature", authMessage.getSignature());
            credentials.put("sessionData", java.util.Base64.getEncoder().encodeToString(this.sessionId.getBytes()));
        }

        // Authenticate user
        boolean authSuccess = authManager.authenticate(username, authType, credentials);

        // Send authentication response
        AuthMessage responseMessage = new AuthMessage(
            authSuccess ? MessageType.AUTH_SUCCESS : MessageType.AUTH_FAILURE
        );
        responseMessage.setSuccess(authSuccess);
        responseMessage.setMessage(authSuccess ? "Authentication successful" : "Authentication failed");

        protocolHandler.sendMessage(responseMessage);

        if (authSuccess) {
            this.authenticated = true;
            this.authenticatedUser = username;
            notifyAuthenticationResult(username, true, "Authentication successful");
        } else {
            notifyAuthenticationResult(username, false, "Authentication failed");
        }

        return authSuccess;
    }

    /**
     * Handle service requests after authentication.
     */
    private void handleServiceRequests() throws Exception {
        while (authenticated && !clientSocket.isClosed()) {
            Message message = null;
            try {
                message = protocolHandler.receiveMessage();
            } catch (IOException e) {
                if (e.getMessage().contains("Client disconnected gracefully")) {
                    safeDisplayMessage("Client " + getClientInfo() + " disconnected gracefully");
                } else {
                    safeDisplayError("Client disconnected or error: " + e.getMessage());
                }
                break;
            } catch (Exception e) {
                safeDisplayError("Unexpected error: " + e.getMessage());
                break;
            }
            if (message == null) {
                safeDisplayMessage("Client disconnected (null message)");
                break;
            }
            switch (message.getType()) {
                case SERVICE_REQUEST:
                    handleServiceRequest((ServiceMessage) message);
                    break;
                case SHELL_COMMAND:
                    handleShellCommand((ShellMessage) message);
                    break;
                case FILE_UPLOAD_REQUEST:
                    handleFileUpload((FileTransferMessage) message);
                    break;
                case FILE_DOWNLOAD_REQUEST:
                    handleFileDownload((FileTransferMessage) message);
                    break;
                case DISCONNECT:
                    safeDisplayMessage("Client " + getClientInfo() + " disconnected cleanly.");
                    return;
                case RELOAD_USERS:
                    handleReloadUsers();
                    break;
                case ERROR:
                    safeDisplayError("Received error message from client: " + ((ErrorMessage) message).getErrorMessage());
                    break;
                default:
                    safeDisplayError("Unknown message type: " + message.getType());
                    break;
            }
        }
    }

    /**
     * Handle service request.
     */
    private void handleServiceRequest(ServiceMessage message) throws Exception {
        String service = message.getService();
        safeDisplayMessage("Service request from " + authenticatedUser + " for " + service);
        
        // Send service accept response
        ServiceMessage response = new ServiceMessage(MessageType.SERVICE_ACCEPT);
        response.setService(service);
        
        protocolHandler.sendMessage(response);
        safeDisplayMessage("Service " + service + " accepted for " + authenticatedUser);
    }

    /**
     * Handle shell command execution.
     */
    private void handleShellCommand(ShellMessage message) {
        try {
            String command = message.getCommand();
            safeDisplayMessage("Executing shell command: '" + command + "' for user: " + authenticatedUser);

            // Execute command using the session-specific executor
            ssh.shared_model.shell.CommandResult result = shellExecutor.execute(command);
            Logger.info("Command result: exitCode=" + result.getExitCode() + ", stdout='" + result.getStdout() + "', stderr='" + result.getStderr() + "'");

            // Send result back to client
            ShellMessage response = new ShellMessage(MessageType.SHELL_RESULT);
            response.setExitCode(result.getExitCode());
            response.setStdout(result.getStdout());
            response.setStderr(result.getStderr());
            response.setWorkingDirectory(shellExecutor.getCurrentWorkingDirectory());

            protocolHandler.sendMessage(response);
            Logger.info("Sent SHELL_RESULT message to client.");
        } catch (Exception e) {
            safeDisplayError("Shell command error: " + e.getMessage());
            Logger.error("Exception in handleShellCommand: " + e.getMessage(), e);
            try {
                ErrorMessage errorMsg = new ErrorMessage();
                errorMsg.setErrorMessage("Server error: " + e.getMessage());
                protocolHandler.sendMessage(errorMsg);
                Logger.info("Sent ErrorMessage to client: " + e.getMessage());
            } catch (Exception ex) {
                safeDisplayError("Failed to send error message to client: " + ex.getMessage());
                Logger.error("Failed to send ErrorMessage to client: " + ex.getMessage(), ex);
            }
        }
    }

    /**
     * Handle file upload request.
     */
    private void handleFileUpload(FileTransferMessage message) throws Exception {
        String filename = message.getFilename();
        long fileSize = message.getFileSize();
        String targetPath = message.getTargetPath();

        safeDisplayMessage("File upload request from " + authenticatedUser + ": " + filename + " (" + fileSize + " bytes)");

        // Create file storage directory for the user
        java.io.File userDir = new java.io.File("data/server/files/" + authenticatedUser);
        userDir.mkdirs();
        
        // Determine target file path
        java.io.File targetFile;
        if (targetPath != null && !targetPath.trim().isEmpty()) {
            targetFile = new java.io.File(userDir, targetPath);
        } else {
            targetFile = new java.io.File(userDir, filename);
        }
        
        // Create parent directories if they don't exist
        targetFile.getParentFile().mkdirs();

        // Send initial acknowledgment
        FileTransferMessage ack = new FileTransferMessage(MessageType.FILE_ACK);
        ack.setSequenceNumber(0);
        ack.setStatus("ready");
        ack.setMessage("Ready to receive file: " + filename);
        protocolHandler.sendMessage(ack);

        // Receive file data in chunks
        long bytesReceived = 0;
        int sequenceNumber = 1;
        boolean isLast = false;

        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(targetFile)) {
            while (!isLast) {
                // Receive file data message
                Message dataMessage = protocolHandler.receiveMessage();
                if (dataMessage.getType() != MessageType.FILE_DATA) {
                    throw new IOException("Expected FILE_DATA, got " + dataMessage.getType());
                }

                FileTransferMessage fileData = (FileTransferMessage) dataMessage;
                
                // Get the file data
                byte[] fileBytes = fileData.getDataBytes();
                if (fileBytes != null && fileBytes.length > 0) {
                    fos.write(fileBytes);
                    bytesReceived += fileBytes.length;
                }

                isLast = fileData.isLast();
                sequenceNumber++;
                
                // Update progress
                int percentage = (int) ((bytesReceived * 100) / fileSize);
                safeDisplayMessage("Received chunk " + (sequenceNumber - 1) + " from " + authenticatedUser + 
                          ", bytes: " + bytesReceived + "/" + fileSize + ", last: " + isLast);
            }
        }

        // Send final acknowledgment
        FileTransferMessage finalAck = new FileTransferMessage(MessageType.FILE_ACK);
        finalAck.setSequenceNumber(sequenceNumber - 1);
        finalAck.setStatus("completed");
        finalAck.setMessage("File upload completed: " + filename + " (" + bytesReceived + " bytes)");
        protocolHandler.sendMessage(finalAck);

        safeDisplayMessage("File upload completed for " + authenticatedUser + ": " + filename + " -> " + targetFile.getAbsolutePath());
    }

    /**
     * Handle file download request.
     */
    private void handleFileDownload(FileTransferMessage message) throws Exception {
        String filename = message.getFilename();
        String targetPath = message.getTargetPath();
        
        safeDisplayMessage("File download request from " + authenticatedUser + " for: " + filename);

        try {
            // Determine file path
            java.io.File fileToSend;
            if (targetPath != null && !targetPath.trim().isEmpty()) {
                fileToSend = new java.io.File("data/server/files/" + authenticatedUser, targetPath);
            } else {
                fileToSend = new java.io.File("data/server/files/" + authenticatedUser, filename);
            }

            // Check if file exists and is readable
            if (!fileToSend.exists() || !fileToSend.canRead()) {
                throw new IOException("File not found or is not readable: " + filename);
            }

            long fileSize = fileToSend.length();
            safeDisplayMessage("Sending file: " + filename + " (" + fileSize + " bytes)");

            // Send file data in chunks
            final int CHUNK_SIZE = 8192; // 8KB chunks
            byte[] buffer = new byte[CHUNK_SIZE];
            long bytesSent = 0;
            int sequenceNumber = 1;

            try (java.io.FileInputStream fis = new java.io.FileInputStream(fileToSend)) {
                int bytesRead;
                boolean firstChunk = true;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    // Create file data message
                    FileTransferMessage dataMessage = new FileTransferMessage(MessageType.FILE_DATA);
                    
                    if (firstChunk) {
                        dataMessage.setFilename(filename);
                        dataMessage.setFileSize(fileSize);
                        firstChunk = false;
                    }
                    
                    // Set the actual data (only the bytes we read)
                    byte[] actualData = new byte[bytesRead];
                    System.arraycopy(buffer, 0, actualData, 0, bytesRead);
                    dataMessage.setData(actualData);

                    // Send the chunk
                    protocolHandler.sendMessage(dataMessage);
                    
                    bytesSent += bytesRead;
                    
                    // Update progress
                    int percentage = (int) ((bytesSent * 100) / fileSize);
                    safeDisplayMessage("Sent chunk " + sequenceNumber + " from " + authenticatedUser + 
                              ", bytes: " + bytesSent + "/" + fileSize + ", last: " + (bytesSent == fileSize));
                    sequenceNumber++;
                }
            }

            // Wait for acknowledgment from client
            Message ackResponse = protocolHandler.receiveMessage();
            if (ackResponse.getType() != MessageType.FILE_ACK) {
                safeDisplayError("Expected FILE_ACK after download, but got " + ackResponse.getType());
            }

            safeDisplayMessage("File download completed for " + authenticatedUser + ": " + filename);

        } catch (IOException e) {
            safeDisplayError("File download failed for user " + authenticatedUser + ": " + e.getMessage());
            // Send a clear error message to the client
            ErrorMessage errorMsg = new ErrorMessage();
            errorMsg.setErrorMessage(e.getMessage());
            protocolHandler.sendMessage(errorMsg);
        }
    }

    /**
     * Handle reload users request.
     */
    private void handleReloadUsers() {
        try {
            safeDisplayMessage("Reloading user database...");
            authManager.reloadUsers();
            safeDisplayMessage("User database reloaded successfully");
            
            // Send acknowledgment back to client
            ServiceMessage response = new ServiceMessage(MessageType.SERVICE_ACCEPT);
            response.setService("reload_users");
            protocolHandler.sendMessage(response);
            
        } catch (Exception e) {
            safeDisplayError("Failed to reload user database: " + e.getMessage());
            
            // Send error response to client
            ErrorMessage errorMsg = new ErrorMessage();
            errorMsg.setErrorMessage("Failed to reload user database: " + e.getMessage());
            try {
                protocolHandler.sendMessage(errorMsg);
            } catch (Exception sendError) {
                safeDisplayError("Failed to send error response: " + sendError.getMessage());
            }
        }
    }

    /**
     * Get client information string.
     */
    private String getClientInfo() {
        return clientSocket.getInetAddress().getHostAddress() + ":" + clientSocket.getPort();
    }

    /**
     * Clean up resources.
     */
    private void cleanup() {
        try {
            if (authenticatedUser != null) {
                safeDisplayMessage("Cleaning up connection for " + getClientInfo() + " (user: " + authenticatedUser + ")");
            } else {
                safeDisplayMessage("Cleaning up connection for " + getClientInfo());
            }
            if (protocolHandler != null) {
                protocolHandler.close();
            }
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
            // Null out large fields to help GC
            shellExecutor = null;
            protocolHandler = null;
            keyExchange = null;
            encryption = null;
            authManager = null;
            serverKeyPair = null;
            config = null;
        } catch (IOException e) {
            safeDisplayError("Error during cleanup: " + e.getMessage());
        }
    }

    // Helper methods for safe UI calls
    private void safeDisplayMessage(String message) {
        if (onStatus != null) {
            onStatus.accept(message);
        }
        Logger.info("Server: " + message);
    }

    private void safeDisplayError(String error) {
        if (onError != null) {
            onError.accept(error);
        }
        Logger.error("Server: " + error);
    }
    
    // Event notification methods for MVC compliance
    private void notifyError(String error) {
        if (onError != null) {
            onError.accept(error);
        }
        Logger.error("Server: " + error);
    }
    
    private void notifyStatus(String status) {
        if (onStatus != null) {
            onStatus.accept(status);
        }
        Logger.info("Server: " + status);
    }
    
    private void notifyAuthenticationResult(String username, boolean success, String message) {
        if (onAuthenticationResult != null) {
            onAuthenticationResult.accept(username + " - " + (success ? "SUCCESS" : "FAILED") + ": " + message);
        }
        Logger.info("Server: Authentication " + (success ? "SUCCESS" : "FAILED") + " for " + username + ": " + message);
    }
    
    private void notifyServiceRequest(String username, String serviceType) {
        if (onServiceRequest != null) {
            onServiceRequest.accept(username + " requested " + serviceType);
        }
        Logger.info("Server: " + username + " requested " + serviceType);
    }
    
    private void notifyFileTransferProgress(String filename, long bytesTransferred, long totalBytes) {
        if (onFileTransferProgress != null) {
            int percentage = (int) ((bytesTransferred * 100) / totalBytes);
            onFileTransferProgress.accept(filename + " - " + percentage + "%");
        }
        Logger.info("Server: File transfer " + filename + " - " + bytesTransferred + "/" + totalBytes + " bytes");
    }
    
    private void notifyShellCommand(String username, String command) {
        if (onShellCommand != null) {
            onShellCommand.accept(username + " executed: " + command);
        }
        Logger.info("Server: " + username + " executed: " + command);
    }
    
    // Event handler setters
    public void setOnError(Consumer<String> onError) {
        this.onError = onError;
    }
    
    public void setOnStatus(Consumer<String> onStatus) {
        this.onStatus = onStatus;
    }
    
    public void setOnAuthenticationResult(Consumer<String> onAuthenticationResult) {
        this.onAuthenticationResult = onAuthenticationResult;
    }
    
    public void setOnServiceRequest(Consumer<String> onServiceRequest) {
        this.onServiceRequest = onServiceRequest;
    }
    
    public void setOnFileTransferProgress(Consumer<String> onFileTransferProgress) {
        this.onFileTransferProgress = onFileTransferProgress;
    }
    
    public void setOnShellCommand(Consumer<String> onShellCommand) {
        this.onShellCommand = onShellCommand;
    }
} 