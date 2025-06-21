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

    public ClientConnection(ServerInfo serverInfo, AuthCredentials credentials, ClientUI ui) {
        Logger.info("ClientConnection constructor called");
        this.serverInfo = serverInfo;
        this.credentials = credentials;
        this.ui = ui;
        this.connected = false;
        this.authenticated = false;
    }

    /**
     * Connect to the server.
     */
    public boolean connect() {
        try {
            Logger.info("Connecting to " + serverInfo.getHost() + ":" + serverInfo.getPort());
            socket = new Socket(serverInfo.getHost(), serverInfo.getPort());
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
                
                // Sign session data (simplified - in real implementation, this would be actual session data)
                byte[] sessionData = "session_data_placeholder".getBytes();
                byte[] signature = RSAKeyGenerator.sign(sessionData, clientKeyPair.getPrivate());
                authMessage.setSignature(signature);
                Logger.info("Using public key authentication");
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
     * Send a shell command to the server.
     */
    public void sendShellCommand(String command) throws Exception {
        if (!authenticated) {
            throw new IllegalStateException("Not authenticated");
        }

        ShellMessage shellMessage = new ShellMessage(MessageType.SHELL_COMMAND);
        shellMessage.setCommand(command);
        shellMessage.setWorkingDirectory(System.getProperty("user.dir"));

        protocolHandler.sendMessage(shellMessage);
    }

    /**
     * Receive shell response from the server.
     */
    public String receiveShellResponse() throws Exception {
        Message response = protocolHandler.receiveMessage();
        if (response.getType() != MessageType.SHELL_RESULT) {
            throw new IOException("Expected SHELL_RESULT, got " + response.getType());
        }

        ShellMessage shellResponse = (ShellMessage) response;
        
        StringBuilder result = new StringBuilder();
        if (shellResponse.getStdout() != null && !shellResponse.getStdout().isEmpty()) {
            result.append("STDOUT:\n").append(shellResponse.getStdout());
        }
        if (shellResponse.getStderr() != null && !shellResponse.getStderr().isEmpty()) {
            if (result.length() > 0) {
                result.append("\n");
            }
            result.append("STDERR:\n").append(shellResponse.getStderr());
        }
        result.append("\nExit code: ").append(shellResponse.getExitCode());

        return result.toString();
    }

    /**
     * Upload a file to the server.
     */
    public void uploadFile(String localPath, String remotePath) throws Exception {
        if (!authenticated) {
            throw new IllegalStateException("Not authenticated");
        }

        // TODO: Implement file upload
        throw new UnsupportedOperationException("File upload not yet implemented");
    }

    /**
     * Download a file from the server.
     */
    public void downloadFile(String remotePath, String localPath) throws Exception {
        if (!authenticated) {
            throw new IllegalStateException("Not authenticated");
        }

        // TODO: Implement file download
        throw new UnsupportedOperationException("File download not yet implemented");
    }

    /**
     * Disconnect from the server.
     */
    public void disconnect() {
        try {
            if (protocolHandler != null) {
                protocolHandler.close();
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            connected = false;
            authenticated = false;
            Logger.info("Disconnected from server");
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