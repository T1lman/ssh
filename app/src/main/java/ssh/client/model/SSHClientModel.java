package ssh.client.model;

import java.util.function.Consumer;
import java.util.concurrent.CompletableFuture;

import ssh.utils.Logger;

/**
 * True MVC Model - contains all business logic for SSH client operations.
 * No UI dependencies, pure business logic.
 */
public class SSHClientModel {
    private ClientConnection connection;
    private ServerInfo serverInfo;
    private AuthCredentials credentials;
    private boolean connected = false;
    private boolean authenticated = false;
    
    // Event callbacks for view updates
    private Consumer<Boolean> onConnectionStatusChanged;
    private Consumer<String> onAuthenticationResult;
    private Consumer<String> onShellOutput;
    private Consumer<String> onFileTransferProgress;
    private Consumer<String> onError;
    private Consumer<String> onStatus;
    private Consumer<String> onWorkingDirectoryChanged;
    private Consumer<String> onServiceRequested;

    // Add a flag to track if the dispatcher is running
    private boolean dispatcherRunning = false;

    public SSHClientModel() {
        Logger.info("SSHClientModel: Created new model instance");
    }

    /**
     * Connect to the server with the given credentials.
     */
    public void connect(ServerInfo serverInfo, AuthCredentials credentials) throws Exception {
        Logger.info("SSHClientModel: Starting connection process");
        
        this.serverInfo = serverInfo;
        this.credentials = credentials;
        
        // Set the username in server info
        this.serverInfo.setUsername(credentials.getUsername());
        
        // Create connection
        connection = new ClientConnection(serverInfo, credentials);
        
        // Wire up result handlers
        connection.setOnError(this::handleError);
        connection.setOnStatus(this::handleStatus);
        
        // Connect to server
        if (!connection.connect()) {
            throw new Exception("Failed to connect to server");
        }
        
        connected = true;
        notifyConnectionStatusChanged(true);
        
        // Perform key exchange
        if (!connection.performKeyExchange()) {
            throw new Exception("Key exchange failed");
        }
        
        // Authenticate
        if (!connection.authenticate()) {
            throw new Exception("Authentication failed");
        }
        
        authenticated = true;
        notifyAuthenticationResult(true, "Authentication successful");
        
        Logger.info("SSHClientModel: Connection and authentication successful");
    }

    /**
     * Request a specific service from the server.
     */
    public void requestService(String service) throws Exception {
        if (!isConnected()) {
            throw new Exception("Not connected to server");
        }
        
        Logger.info("SSHClientModel: Requesting service: " + service);
        
        if (!connection.sendServiceRequest(service)) {
            throw new Exception("Service request failed");
        }
        
        Logger.info("SSHClientModel: Service request successful");
        notifyServiceRequested(service);
    }

    // Add a data class to hold shell result and working directory
    public static class ShellResult {
        public final String output;
        public final String workingDirectory;
        public ShellResult(String output, String workingDirectory) {
            this.output = output;
            this.workingDirectory = workingDirectory;
        }
    }

    /**
     * Send a shell command.
     */
    public java.util.concurrent.CompletableFuture<ShellResult> sendShellCommandAsync(String command) {
        if (!isConnected()) {
            java.util.concurrent.CompletableFuture<ShellResult> failed = new java.util.concurrent.CompletableFuture<>();
            failed.completeExceptionally(new Exception("Not connected to server"));
            return failed;
        }
        Logger.info("SSHClientModel: Sending shell command (async): " + command);
        return connection.sendShellCommandAsync(command)
            .thenApply(msg -> {
                ssh.shared_model.protocol.messages.ShellMessage shellMsg = (ssh.shared_model.protocol.messages.ShellMessage) msg;
                String output = (shellMsg.getStdout() != null ? shellMsg.getStdout() : "") +
                                (shellMsg.getStderr() != null ? shellMsg.getStderr() : "");
                String newWorkingDirectory = shellMsg.getWorkingDirectory();
                if (newWorkingDirectory != null && !newWorkingDirectory.isEmpty()) {
                    notifyWorkingDirectoryChanged(newWorkingDirectory);
                }
                return new ShellResult(output, newWorkingDirectory != null && !newWorkingDirectory.isEmpty() ? newWorkingDirectory : getWorkingDirectory());
            });
    }

    /**
     * Upload a file.
     */
    public CompletableFuture<Void> uploadFileAsync(String localPath, String remotePath) {
        if (!isConnected()) {
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(new Exception("Not connected to server"));
            return failed;
        }
        Logger.info("SSHClientModel: Uploading file (async) from " + localPath + " to " + remotePath);
        return connection.uploadFileAsync(localPath, remotePath)
            .thenAccept(msg -> notifyFileTransferProgress("Upload completed: " + new java.io.File(localPath).getName(), 100));
    }

    /**
     * Download a file.
     */
    public CompletableFuture<Void> downloadFileAsync(String remotePath, String localPath) {
        if (!isConnected()) {
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(new Exception("Not connected to server"));
            return failed;
        }
        Logger.info("SSHClientModel: Downloading file (async) from " + remotePath + " to " + localPath);
        return connection.downloadFileAsync(remotePath, localPath)
            .thenAccept(msg -> notifyFileTransferProgress("Download completed: " + new java.io.File(remotePath).getName(), 100));
    }

    /**
     * Disconnect from the server.
     */
    public void disconnect() {
        Logger.info("SSHClientModel: Disconnecting from server");
        
        if (connection != null) {
            try {
                connection.sendDisconnect();
                connection.disconnect();
            } catch (Exception e) {
                Logger.error("SSHClientModel: Error during disconnect: " + e.getMessage());
            }
            connection = null;
        }
        
        connected = false;
        authenticated = false;
        notifyConnectionStatusChanged(false);
    }

    /**
     * Check if connected to server.
     */
    public boolean isConnected() {
        return connected && connection != null && connection.isActive();
    }

    /**
     * Check if authenticated.
     */
    public boolean isAuthenticated() {
        return authenticated;
    }

    /**
     * Get the current session ID.
     */
    public String getSessionId() {
        if (connection != null) {
            return connection.getSessionId();
        }
        return "-";
    }

    /**
     * Get the working directory.
     */
    public String getWorkingDirectory() {
        if (connection != null) {
            return connection.getWorkingDirectory();
        }
        return System.getProperty("user.home");
    }

    /**
     * Get the server information.
     */
    public ServerInfo getServerInfo() {
        return serverInfo;
    }

    /**
     * Get the authentication credentials.
     */
    public AuthCredentials getCredentials() {
        return credentials;
    }

    /**
     * Start a local port forward (delegate to ClientConnection).
     */
    public void requestLocalPortForward(int localPort, String remoteHost, int remotePort) throws Exception {
        if (!isConnected()) throw new Exception("Not connected to server");
        connection.requestLocalPortForward(localPort, remoteHost, remotePort);
    }

    /**
     * Start a remote port forward (delegate to ClientConnection).
     */
    public void requestRemotePortForward(int remotePort, String localHost, int localPort) throws Exception {
        if (!isConnected()) throw new Exception("Not connected to server");
        connection.requestRemotePortForward(remotePort, localHost, localPort);
    }

    // Event notification methods
    private void notifyConnectionStatusChanged(boolean connected) {
        if (onConnectionStatusChanged != null) {
            onConnectionStatusChanged.accept(connected);
        }
    }

    private void notifyAuthenticationResult(boolean success, String message) {
        if (onAuthenticationResult != null) {
            onAuthenticationResult.accept(success ? "SUCCESS: " + message : "FAILED: " + message);
        }
    }

    private void notifyShellOutput(String output) {
        Logger.info("[Model] notifyShellOutput called with output: " + output);
        if (onShellOutput != null) {
            Logger.info("[Model] onShellOutput is set, calling accept");
            onShellOutput.accept(output);
        } else {
            Logger.warn("[Model] onShellOutput is null!");
        }
    }

    private void notifyFileTransferProgress(String filename, int percentage) {
        if (onFileTransferProgress != null) {
            onFileTransferProgress.accept(filename + " - " + percentage + "%");
        }
    }

    private void notifyWorkingDirectoryChanged(String newWorkingDirectory) {
        if (onWorkingDirectoryChanged != null) {
            onWorkingDirectoryChanged.accept(newWorkingDirectory);
        }
    }

    private void notifyServiceRequested(String service) {
        if (onServiceRequested != null) {
            onServiceRequested.accept(service);
        }
    }

    private void handleError(String error) {
        if (onError != null) {
            onError.accept(error);
        }
    }

    private void handleStatus(String status) {
        if (onStatus != null) {
            onStatus.accept(status);
        }
    }

    // Event handler setters
    public void setOnConnectionStatusChanged(Consumer<Boolean> onConnectionStatusChanged) {
        this.onConnectionStatusChanged = onConnectionStatusChanged;
    }

    public void setOnAuthenticationResult(Consumer<String> onAuthenticationResult) {
        this.onAuthenticationResult = onAuthenticationResult;
    }

    public void setOnShellOutput(Consumer<String> onShellOutput) {
        Logger.info("[Model] setOnShellOutput called");
        this.onShellOutput = onShellOutput;
    }

    public void setOnFileTransferProgress(Consumer<String> onFileTransferProgress) {
        this.onFileTransferProgress = onFileTransferProgress;
    }

    public void setOnError(Consumer<String> onError) {
        this.onError = onError;
    }

    public void setOnStatus(Consumer<String> onStatus) {
        this.onStatus = onStatus;
    }

    public void setOnWorkingDirectoryChanged(Consumer<String> onWorkingDirectoryChanged) {
        this.onWorkingDirectoryChanged = onWorkingDirectoryChanged;
    }

    public void setOnServiceRequested(Consumer<String> onServiceRequested) {
        this.onServiceRequested = onServiceRequested;
    }

    public ClientConnection getConnection() {
        return connection;
    }
} 