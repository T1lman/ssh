package ssh.server;

import ssh.auth.AuthenticationManager;
import ssh.crypto.DiffieHellmanKeyExchange;
import ssh.crypto.SymmetricEncryption;
import ssh.protocol.Message;
import ssh.protocol.MessageType;
import ssh.protocol.ProtocolHandler;
import ssh.protocol.messages.AuthMessage;
import ssh.protocol.messages.KeyExchangeMessage;
import ssh.server.ui.ServerConfig;
import ssh.server.ui.ServerUI;

import java.io.IOException;
import java.net.Socket;
import java.security.KeyPair;
import java.util.HashMap;
import java.util.Map;

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

    public ServerConnection(Socket clientSocket, AuthenticationManager authManager, 
                          KeyPair serverKeyPair, ServerUI ui, ServerConfig config) {
        this.clientSocket = clientSocket;
        this.authManager = authManager;
        this.serverKeyPair = serverKeyPair;
        this.ui = ui;
        this.config = config;
        this.authenticated = false;
        this.authenticatedUser = null;
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

        // Sign the DH public key with server's private key
        byte[] signature = ssh.crypto.RSAKeyGenerator.sign(
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
            credentials.put("sessionData", "session_data_placeholder"); // In real implementation, this would be actual session data
        } else if ("password".equals(authType)) {
            credentials.put("password", authMessage.getPassword());
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
            Message message = protocolHandler.receiveMessage();
            
            switch (message.getType()) {
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
                    ui.displayMessage("Client " + getClientInfo() + " disconnected");
                    return;
                default:
                    ui.displayError("Unknown message type: " + message.getType());
                    break;
            }
        }
    }

    /**
     * Handle shell command execution.
     */
    private void handleShellCommand(ssh.protocol.messages.ShellMessage message) throws Exception {
        String command = message.getCommand();
        String workingDirectory = message.getWorkingDirectory();

        ui.showShellCommand(authenticatedUser, command);

        // Execute command (basic implementation)
        ssh.shell.ShellExecutor executor = new ssh.shell.ShellExecutor();
        ssh.shell.CommandResult result = executor.execute(command, workingDirectory);

        // Send result back to client
        ssh.protocol.messages.ShellMessage response = new ssh.protocol.messages.ShellMessage(MessageType.SHELL_RESULT);
        response.setExitCode(result.getExitCode());
        response.setStdout(result.getStdout());
        response.setStderr(result.getStderr());
        response.setWorkingDirectory(workingDirectory);

        protocolHandler.sendMessage(response);
    }

    /**
     * Handle file upload request.
     */
    private void handleFileUpload(ssh.protocol.messages.FileTransferMessage message) throws Exception {
        String filename = message.getFilename();
        long fileSize = message.getFileSize();
        String targetPath = message.getTargetPath();

        ui.showFileTransferProgress(filename, 0, fileSize);

        // Basic file upload implementation
        // In a real implementation, you would receive file data chunks and save them
        
        // Send acknowledgment
        ssh.protocol.messages.FileTransferMessage ack = new ssh.protocol.messages.FileTransferMessage(MessageType.FILE_ACK);
        ack.setSequenceNumber(1);
        ack.setStatus("received");
        ack.setMessage("File upload request received");

        protocolHandler.sendMessage(ack);
    }

    /**
     * Handle file download request.
     */
    private void handleFileDownload(ssh.protocol.messages.FileTransferMessage message) throws Exception {
        String filename = message.getFilename();
        
        ui.displayMessage("File download request for: " + filename);

        // Basic file download implementation
        // In a real implementation, you would read the file and send data chunks
        
        // Send file info
        ssh.protocol.messages.FileTransferMessage response = new ssh.protocol.messages.FileTransferMessage(MessageType.FILE_DATA);
        response.setFilename(filename);
        response.setFileSize(0); // In real implementation, this would be actual file size
        response.setSequenceNumber(1);
        response.setLast(true);

        protocolHandler.sendMessage(response);
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
            if (protocolHandler != null) {
                protocolHandler.close();
            }
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
        } catch (IOException e) {
            ui.displayError("Error during cleanup: " + e.getMessage());
        }
    }
} 