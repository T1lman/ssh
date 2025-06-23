package ssh.server;

import ssh.auth.AuthenticationManager;
import ssh.crypto.DiffieHellmanKeyExchange;
import ssh.crypto.RSAKeyGenerator;
import ssh.crypto.SymmetricEncryption;
import ssh.protocol.Message;
import ssh.protocol.MessageType;
import ssh.protocol.ProtocolHandler;
import ssh.protocol.messages.AuthMessage;
import ssh.protocol.messages.KeyExchangeMessage;
import ssh.server.ui.ServerConfig;
import ssh.server.ui.ServerUI;
import ssh.shell.ShellExecutor;
import ssh.utils.Logger;

import java.io.IOException;
import java.net.Socket;
import java.security.KeyPair;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles a single client connection on the server side.
 */
public class ServerConnection implements Runnable {
    private Socket clientSocket;
    private ProtocolHandler protocolHandler;
    private AuthenticationManager authManager;
    private KeyPair serverKeyPair;
    private ServerUI ui;
    private ServerConfig config;
    private boolean authenticated;
    private String authenticatedUser;
    private DiffieHellmanKeyExchange keyExchange;
    private SymmetricEncryption encryption;
    private ShellExecutor shellExecutor;
    private String sessionId;

    public ServerConnection(Socket clientSocket, AuthenticationManager authManager, 
                          KeyPair serverKeyPair, ServerUI ui, ServerConfig config) {
        this.clientSocket = clientSocket;
        this.authManager = authManager;
        this.serverKeyPair = serverKeyPair;
        this.ui = ui;
        this.config = config;
        this.authenticated = false;
        this.authenticatedUser = null;
        this.shellExecutor = new ShellExecutor();
        this.sessionId = UUID.randomUUID().toString();
        
        // Set socket timeouts to prevent hanging
        try {
            clientSocket.setTcpNoDelay(true); // Disable Nagle's algorithm for better performance
        } catch (Exception e) {
            ui.displayError("Failed to set socket timeout: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        try {
            // Initialize protocol handler
            protocolHandler = new ProtocolHandler(
                clientSocket.getInputStream(), 
                clientSocket.getOutputStream()
            );

            // Perform key exchange
            if (!handleKeyExchange()) {
                ui.displayError("Key exchange failed for client " + getClientInfo());
                return;
            }

            // Handle authentication
            if (!handleAuthentication()) {
                ui.displayError("Authentication failed for client " + getClientInfo());
                return;
            }

            // Handle service requests
            handleServiceRequests();

        } catch (Exception e) {
            ui.displayError("Error handling client " + getClientInfo() + ": " + e.getMessage());
        } finally {
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
            ui.displayError("Expected KEY_EXCHANGE_INIT, got " + message.getType());
            return false;
        }

        KeyExchangeMessage initMessage = (KeyExchangeMessage) message;
        ui.displayMessage("Received key exchange init from " + initMessage.getClientId());

        // Initialize DH key exchange
        keyExchange = new DiffieHellmanKeyExchange();
        keyExchange.generateKeyPair();
        keyExchange.setOtherPublicKey(initMessage.getDhPublicKeyBytes());

        // Compute shared secret
        byte[] sharedSecret = keyExchange.computeSharedSecret();

        // Initialize symmetric encryption
        encryption = new SymmetricEncryption();
        encryption.initializeKey(sharedSecret);

        // Send key exchange reply BEFORE enabling encryption
        KeyExchangeMessage replyMessage = new KeyExchangeMessage(MessageType.KEY_EXCHANGE_REPLY);
        replyMessage.setDhPublicKey(keyExchange.getPublicKeyBytes());
        replyMessage.setServerId("SSH-2.0-JavaSSH-Server");
        replyMessage.setSessionId(this.sessionId);

        // Sign the DH public key with server's private key
        byte[] signature = RSAKeyGenerator.sign(
            keyExchange.getPublicKeyBytes(), 
            serverKeyPair.getPrivate()
        );
        replyMessage.setSignature(signature);

        protocolHandler.sendMessage(replyMessage);
        ui.displayMessage("Key exchange completed successfully");

        // Enable encryption AFTER sending the reply
        protocolHandler.enableEncryption(encryption);

        return true;
    }

    /**
     * Handle the authentication phase.
     */
    private boolean handleAuthentication() throws Exception {
        // Receive authentication request
        Message message = protocolHandler.receiveMessage();
        if (message.getType() != MessageType.AUTH_REQUEST) {
            ui.displayError("Expected AUTH_REQUEST, got " + message.getType());
            return false;
        }

        AuthMessage authMessage = (AuthMessage) message;
        String username = authMessage.getUsername();
        String authType = authMessage.getAuthType();

        ui.displayMessage("Authentication request from " + username + " using " + authType);

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
            ui.showAuthenticationResult(username, true, "Authentication successful");
        } else {
            ui.showAuthenticationResult(username, false, "Authentication failed");
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
                    ui.displayMessage("Client " + getClientInfo() + " disconnected gracefully");
                } else {
                    ui.displayError("Client disconnected or error: " + e.getMessage());
                }
                break;
            } catch (Exception e) {
                ui.displayError("Unexpected error: " + e.getMessage());
                break;
            }
            if (message == null) {
                ui.displayMessage("Client disconnected (null message)");
                break;
            }
            switch (message.getType()) {
                case SERVICE_REQUEST:
                    handleServiceRequest((ssh.protocol.messages.ServiceMessage) message);
                    break;
                case SHELL_COMMAND:
                    handleShellCommand((ssh.protocol.messages.ShellMessage) message);
                    break;
                case FILE_UPLOAD_REQUEST:
                    handleFileUpload((ssh.protocol.messages.FileTransferMessage) message);
                    break;
                case FILE_DOWNLOAD_REQUEST:
                    handleFileDownload((ssh.protocol.messages.FileTransferMessage) message);
                    break;
                case DISCONNECT:
                    ui.displayMessage("Client " + getClientInfo() + " disconnected cleanly.");
                    return;
                case RELOAD_USERS:
                    handleReloadUsers();
                    break;
                case ERROR:
                    ui.displayError("Received error message from client: " + ((ssh.protocol.messages.ErrorMessage) message).getErrorMessage());
                    break;
                default:
                    ui.displayError("Unknown message type: " + message.getType());
                    break;
            }
        }
    }

    /**
     * Handle service request.
     */
    private void handleServiceRequest(ssh.protocol.messages.ServiceMessage message) throws Exception {
        String service = message.getService();
        ui.displayMessage("Service request from " + authenticatedUser + " for " + service);
        
        // Send service accept response
        ssh.protocol.messages.ServiceMessage response = new ssh.protocol.messages.ServiceMessage(MessageType.SERVICE_ACCEPT);
        response.setService(service);
        
        protocolHandler.sendMessage(response);
        ui.displayMessage("Service " + service + " accepted for " + authenticatedUser);
    }

    /**
     * Handle shell command execution.
     */
    private void handleShellCommand(ssh.protocol.messages.ShellMessage message) {
        try {
            String command = message.getCommand();
            ui.showShellCommand(authenticatedUser, command);
            Logger.info("Executing shell command: '" + command + "' for user: " + authenticatedUser);

            // Execute command using the session-specific executor
            ssh.shell.CommandResult result = shellExecutor.execute(command);
            Logger.info("Command result: exitCode=" + result.getExitCode() + ", stdout='" + result.getStdout() + "', stderr='" + result.getStderr() + "'");

            // Send result back to client
            ssh.protocol.messages.ShellMessage response = new ssh.protocol.messages.ShellMessage(MessageType.SHELL_RESULT);
            response.setExitCode(result.getExitCode());
            response.setStdout(result.getStdout());
            response.setStderr(result.getStderr());
            response.setWorkingDirectory(shellExecutor.getCurrentWorkingDirectory());

            protocolHandler.sendMessage(response);
            Logger.info("Sent SHELL_RESULT message to client.");
        } catch (Exception e) {
            ui.displayError("Shell command error: " + e.getMessage());
            Logger.error("Exception in handleShellCommand: " + e.getMessage(), e);
            try {
                ssh.protocol.messages.ErrorMessage errorMsg = new ssh.protocol.messages.ErrorMessage();
                errorMsg.setErrorMessage("Server error: " + e.getMessage());
                protocolHandler.sendMessage(errorMsg);
                Logger.info("Sent ErrorMessage to client: " + e.getMessage());
            } catch (Exception ex) {
                ui.displayError("Failed to send error message to client: " + ex.getMessage());
                Logger.error("Failed to send ErrorMessage to client: " + ex.getMessage(), ex);
            }
        }
    }

    /**
     * Handle file upload request.
     */
    private void handleFileUpload(ssh.protocol.messages.FileTransferMessage message) throws Exception {
        String filename = message.getFilename();
        long fileSize = message.getFileSize();
        String targetPath = message.getTargetPath();

        ui.showFileTransferProgress(filename, 0, fileSize);
        Logger.info("File upload request from " + authenticatedUser + ": " + filename + " (" + fileSize + " bytes)");

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
        ssh.protocol.messages.FileTransferMessage ack = new ssh.protocol.messages.FileTransferMessage(MessageType.FILE_ACK);
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

                ssh.protocol.messages.FileTransferMessage fileData = (ssh.protocol.messages.FileTransferMessage) dataMessage;
                
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
                ui.showFileTransferProgress(filename, bytesReceived, fileSize);
                
                Logger.info("Received chunk " + (sequenceNumber - 1) + " from " + authenticatedUser + 
                          ", bytes: " + bytesReceived + "/" + fileSize + ", last: " + isLast);
            }
        }

        // Send final acknowledgment
        ssh.protocol.messages.FileTransferMessage finalAck = new ssh.protocol.messages.FileTransferMessage(MessageType.FILE_ACK);
        finalAck.setSequenceNumber(sequenceNumber - 1);
        finalAck.setStatus("completed");
        finalAck.setMessage("File upload completed: " + filename + " (" + bytesReceived + " bytes)");
        protocolHandler.sendMessage(finalAck);

        Logger.info("File upload completed for " + authenticatedUser + ": " + filename + " -> " + targetFile.getAbsolutePath());
        ui.showFileTransferProgress(filename, fileSize, fileSize);
    }

    /**
     * Handle file download request.
     */
    private void handleFileDownload(ssh.protocol.messages.FileTransferMessage message) throws Exception {
        String filename = message.getFilename();
        String targetPath = message.getTargetPath();
        
        Logger.info("File download request from " + authenticatedUser + " for: " + filename);
        ui.displayMessage("File download request for: " + filename);

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
            Logger.info("Sending file: " + filename + " (" + fileSize + " bytes)");
            ui.showFileTransferProgress(filename, 0, fileSize);

            // Send file data in chunks
            final int CHUNK_SIZE = 8192; // 8KB chunks
            byte[] buffer = new byte[CHUNK_SIZE];
            long bytesSent = 0;

            try (java.io.FileInputStream fis = new java.io.FileInputStream(fileToSend)) {
                int bytesRead;
                boolean firstChunk = true;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    // Create file data message
                    ssh.protocol.messages.FileTransferMessage dataMessage = new ssh.protocol.messages.FileTransferMessage(MessageType.FILE_DATA);
                    
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
                    ui.showFileTransferProgress(filename, bytesSent, fileSize);
                }
            }

            // Wait for acknowledgment from client
            Message ackResponse = protocolHandler.receiveMessage();
            if (ackResponse.getType() != MessageType.FILE_ACK) {
                Logger.warn("Expected FILE_ACK after download, but got " + ackResponse.getType());
            }

            Logger.info("File download completed for " + authenticatedUser + ": " + filename);

        } catch (IOException e) {
            Logger.error("File download failed for user " + authenticatedUser + ": " + e.getMessage());
            // Send a clear error message to the client
            ssh.protocol.messages.ErrorMessage errorMsg = new ssh.protocol.messages.ErrorMessage();
            errorMsg.setErrorMessage(e.getMessage());
            protocolHandler.sendMessage(errorMsg);
        }
    }

    /**
     * Handle reload users request.
     */
    private void handleReloadUsers() {
        try {
            ui.displayMessage("Reloading user database...");
            authManager.reloadUsers();
            ui.displayMessage("User database reloaded successfully");
            
            // Send acknowledgment back to client
            ssh.protocol.messages.ServiceMessage response = new ssh.protocol.messages.ServiceMessage(MessageType.SERVICE_ACCEPT);
            response.setService("reload_users");
            protocolHandler.sendMessage(response);
            
        } catch (Exception e) {
            ui.displayError("Failed to reload user database: " + e.getMessage());
            
            // Send error response to client
            ssh.protocol.messages.ErrorMessage errorMsg = new ssh.protocol.messages.ErrorMessage();
            errorMsg.setErrorMessage("Failed to reload user database: " + e.getMessage());
            try {
                protocolHandler.sendMessage(errorMsg);
            } catch (Exception sendError) {
                ui.displayError("Failed to send error response: " + sendError.getMessage());
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
                ui.displayMessage("Cleaning up connection for " + getClientInfo() + " (user: " + authenticatedUser + ")");
            } else {
                ui.displayMessage("Cleaning up connection for " + getClientInfo());
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
            ui.displayError("Error during cleanup: " + e.getMessage());
        }
    }
} 