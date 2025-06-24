package ssh.server.model;

import ssh.config.ServerConfig;
import ssh.shared_model.auth.AuthenticationManager;
import ssh.shared_model.auth.UserStore;
import ssh.shared_model.crypto.RSAKeyGenerator;
import ssh.utils.Logger;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyPair;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * True MVC Model for SSH Server - contains all business logic for server operations.
 * No UI dependencies, pure business logic.
 */
public class SSHServerModel {
    private ServerConfig config;
    private ServerSocket serverSocket;
    private ExecutorService threadPool;
    private AuthenticationManager authManager;
    private KeyPair serverKeyPair;
    private boolean running = false;
    
    // Event callbacks for view updates
    private Consumer<String> onServerStatusChanged;
    private Consumer<String> onConnectionInfo;
    private Consumer<String> onAuthenticationResult;
    private Consumer<String> onServiceRequest;
    private Consumer<String> onFileTransferProgress;
    private Consumer<String> onShellCommand;
    private Consumer<String> onError;
    private Consumer<String> onMessage;

    public SSHServerModel() {
        Logger.info("SSHServerModel: Created new model instance");
    }

    /**
     * Start the SSH server.
     */
    public void start(ServerConfig config) throws Exception {
        Logger.info("SSHServerModel: Starting server");
        
        this.config = config;
        
        // Initialize server components
        initializeServer();
        
        // Show startup information
        notifyServerStatusChanged("Server started on " + config.getHost() + ":" + config.getPort());
        
        // Start listening for connections
        startListening();
        
    }

    /**
     * Initialize the server components.
     */
    private void initializeServer() throws Exception {
        // Initialize user store and authentication manager
        UserStore userStore = new UserStore(config.getUsersFile(), config.getAuthorizedKeysDir());
        authManager = new AuthenticationManager(userStore);
        
        // Generate or load server key pair
        initializeServerKeys();
        
        // Create server socket
        serverSocket = new ServerSocket(config.getPort());
        serverSocket.setReuseAddress(true);
        
        // Create thread pool
        threadPool = Executors.newFixedThreadPool(config.getMaxConnections());
        
        running = true;
        
        notifyMessage("Server initialized successfully");
    }

    /**
     * Initialize server RSA key pair.
     */
    private void initializeServerKeys() throws Exception {
        String privateKeyPath = config.getKeyDirectory() + "/server_rsa_key";
        String publicKeyPath = config.getKeyDirectory() + "/server_rsa_key.pub";
        
        try {
            // Try to load existing keys
            serverKeyPair = RSAKeyGenerator.loadKeyPair(privateKeyPath, publicKeyPath);
            notifyMessage("Loaded existing server keys");
        } catch (Exception e) {
            // Generate new keys if they don't exist
            notifyMessage("Generating new server keys...");
            serverKeyPair = RSAKeyGenerator.generateKeyPair();
            
            // Create directory if it doesn't exist
            java.io.File keyDir = new java.io.File(config.getKeyDirectory());
            keyDir.mkdirs();
            
            // Save the keys
            RSAKeyGenerator.saveKeyPair(serverKeyPair, privateKeyPath, publicKeyPath);
            notifyMessage("Server keys generated and saved");
        }
    }

    /**
     * Start listening for client connections.
     */
    private void startListening() {
        notifyServerStatusChanged("Listening for connections on " + config.getHost() + ":" + config.getPort());
        
        while (running) {
            try {
                // Accept client connection
                Socket clientSocket = serverSocket.accept();
                
                // Get client information
                String clientAddress = clientSocket.getInetAddress().getHostAddress();
                int clientPort = clientSocket.getPort();
                notifyConnectionInfo(clientAddress, clientPort);
                
                // Handle client in separate thread
                ServerConnection connection = new ServerConnection(
                    clientSocket, authManager, serverKeyPair, config
                );
                
                // Wire up event handlers for MVC compliance
                connection.setOnError(this::notifyError);
                connection.setOnStatus(this::notifyMessage);
                connection.setOnAuthenticationResult(this::handleAuthenticationResult);
                connection.setOnServiceRequest(this::handleServiceRequest);
                connection.setOnFileTransferProgress(this::handleFileTransferProgress);
                connection.setOnShellCommand(this::handleShellCommand);
                
                threadPool.submit(connection);
                
            } catch (IOException e) {
                if (running) {
                    notifyError("Error accepting connection: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Stop the server.
     */
    public void stop() {
        Logger.info("SSHServerModel: Stopping server");
        running = false;
        
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                notifyError("Error closing server socket: " + e.getMessage());
            }
        }
        
        if (threadPool != null) {
            threadPool.shutdown();
        }
        
        notifyServerStatusChanged("Server stopped");
    }

    /**
     * Check if the server is running.
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Get the server configuration.
     */
    public ServerConfig getConfig() {
        return config;
    }

    /**
     * Get the authentication manager.
     */
    public AuthenticationManager getAuthManager() {
        return authManager;
    }

    /**
     * Get the server key pair.
     */
    public KeyPair getServerKeyPair() {
        return serverKeyPair;
    }

    // Event notification methods
    private void notifyServerStatusChanged(String status) {
        if (onServerStatusChanged != null) {
            onServerStatusChanged.accept(status);
        }
    }

    private void notifyConnectionInfo(String clientAddress, int clientPort) {
        if (onConnectionInfo != null) {
            onConnectionInfo.accept(clientAddress + ":" + clientPort);
        }
    }

    private void notifyAuthenticationResult(String username, boolean success, String message) {
        if (onAuthenticationResult != null) {
            onAuthenticationResult.accept(username + " - " + (success ? "SUCCESS" : "FAILED") + ": " + message);
        }
    }

    private void notifyServiceRequest(String username, String serviceType) {
        if (onServiceRequest != null) {
            onServiceRequest.accept(username + " requested " + serviceType);
        }
    }

    private void notifyFileTransferProgress(String filename, long bytesTransferred, long totalBytes) {
        if (onFileTransferProgress != null) {
            int percentage = (int) ((bytesTransferred * 100) / totalBytes);
            onFileTransferProgress.accept(filename + " - " + percentage + "%");
        }
    }

    private void notifyShellCommand(String username, String command) {
        if (onShellCommand != null) {
            onShellCommand.accept(username + " executed: " + command);
        }
    }

    private void notifyError(String error) {
        if (onError != null) {
            onError.accept(error);
        }
    }

    private void notifyMessage(String message) {
        if (onMessage != null) {
            onMessage.accept(message);
        }
    }

    // Event handler setters
    public void setOnServerStatusChanged(Consumer<String> onServerStatusChanged) {
        this.onServerStatusChanged = onServerStatusChanged;
    }

    public void setOnConnectionInfo(Consumer<String> onConnectionInfo) {
        this.onConnectionInfo = onConnectionInfo;
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

    public void setOnError(Consumer<String> onError) {
        this.onError = onError;
    }

    public void setOnMessage(Consumer<String> onMessage) {
        this.onMessage = onMessage;
    }

    // Wrapper methods for ServerConnection event handlers
    private void handleAuthenticationResult(String result) {
        // Parse the result string: "username - SUCCESS/FAILED: message"
        String[] parts = result.split(" - ");
        if (parts.length >= 2) {
            String username = parts[0];
            String statusPart = parts[1];
            boolean success = statusPart.startsWith("SUCCESS");
            String message = statusPart.substring(statusPart.indexOf(":") + 1).trim();
            notifyAuthenticationResult(username, success, message);
        }
    }

    private void handleServiceRequest(String result) {
        // Parse the result string: "username requested serviceType"
        String[] parts = result.split(" requested ");
        if (parts.length >= 2) {
            String username = parts[0];
            String serviceType = parts[1];
            notifyServiceRequest(username, serviceType);
        }
    }

    private void handleFileTransferProgress(String result) {
        // Parse the result string: "filename - percentage%"
        String[] parts = result.split(" - ");
        if (parts.length >= 2) {
            String filename = parts[0];
            String percentageStr = parts[1].replace("%", "");
            try {
                int percentage = Integer.parseInt(percentageStr);
                // Estimate bytes based on percentage (this is a simplification)
                long totalBytes = 1000; // Default total
                long bytesTransferred = (percentage * totalBytes) / 100;
                notifyFileTransferProgress(filename, bytesTransferred, totalBytes);
            } catch (NumberFormatException e) {
                notifyFileTransferProgress(result, 0, 0);
            }
        }
    }

    private void handleShellCommand(String result) {
        // Parse the result string: "username executed: command"
        String[] parts = result.split(" executed: ");
        if (parts.length >= 2) {
            String username = parts[0];
            String command = parts[1];
            notifyShellCommand(username, command);
        }
    }
} 