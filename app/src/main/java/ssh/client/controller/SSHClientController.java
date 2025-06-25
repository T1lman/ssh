package ssh.client.controller;

import ssh.client.model.AuthCredentials;
import ssh.client.model.ServerInfo;
import ssh.client.model.ClientConnection;
import ssh.client.model.SSHClientModel;
import ssh.client.view.ClientUI;
import ssh.client.view.ConsoleClientUI;
import ssh.utils.CredentialsManager;
import ssh.utils.Logger;

import java.util.function.Consumer;

/**
 * True MVC Controller - only coordinates between Model and View.
 * Contains no business logic, only orchestration.
 */
public class SSHClientController {
    private final SSHClientModel model;
    private final ClientUI view;
    private boolean running = true;

    public SSHClientController(ClientUI view) {
        this.view = view;
        this.model = new SSHClientModel();
        setupEventHandlers();
    }

    /**
     * Setup event handlers to properly mediate between view and model.
     */
    private void setupEventHandlers() {
        // Model events -> View updates
        model.setOnConnectionStatusChanged(view::showConnectionStatus);
        model.setOnAuthenticationResult(this::handleAuthenticationResult);
        model.setOnShellOutput(view::displayShellOutput);
        model.setOnFileTransferProgress(this::handleFileTransferProgress);
        model.setOnError(view::displayError);
        model.setOnStatus(view::displayMessage);
        model.setOnWorkingDirectoryChanged(this::handleWorkingDirectoryChanged);
        model.setOnServiceRequested(this::handleServiceRequested);
        
        // View events -> Model actions (mediated by controller)
        if (view instanceof ssh.client.view.JavaFXClientUI) {
            ssh.client.view.JavaFXClientUI guiView = (ssh.client.view.JavaFXClientUI) view;
            guiView.setOnCommandEntered(this::handleShellCommand);
            guiView.setOnFileUploadRequested(this::handleFileUpload);
            guiView.setOnFileDownloadRequested(this::handleFileDownload);
        }
    }

    /**
     * Handle authentication result from model and convert to view format.
     */
    private void handleAuthenticationResult(String result) {
        boolean success = result.startsWith("SUCCESS:");
        String message = result.substring(result.indexOf(":") + 1).trim();
        view.showAuthenticationResult(success, message);
    }

    /**
     * Handle file transfer progress from model and convert to view format.
     */
    private void handleFileTransferProgress(String progress) {
        // Extract filename and percentage from progress string
        // Format: "filename - percentage%"
        String[] parts = progress.split(" - ");
        if (parts.length >= 2) {
            String filename = parts[0];
            String percentageStr = parts[1].replace("%", "");
            try {
                int percentage = Integer.parseInt(percentageStr);
                view.showFileTransferProgress(filename, percentage);
            } catch (NumberFormatException e) {
                view.showFileTransferProgress(progress, 0);
            }
        } else {
            view.showFileTransferProgress(progress, 0);
        }
    }

    /**
     * Handle working directory change from model and update the view.
     */
    private void handleWorkingDirectoryChanged(String newWorkingDirectory) {
        if (view instanceof ssh.client.view.JavaFXClientUI) {
            ssh.client.view.JavaFXClientUI guiView = (ssh.client.view.JavaFXClientUI) view;
            guiView.updateWorkingDirectory(newWorkingDirectory);
        }
    }

    /**
     * Start the client application.
     */
    public void start() {
        try {
            Logger.info("SSHClientController: Starting client");
            
            // For GUI clients, don't start connection immediately
            if (view instanceof ssh.client.view.JavaFXClientUI) {
                Logger.info("SSHClientController: GUI client - waiting for user input");
                return;
            }
            
            // For console clients, start the connection flow
            startConnectionFlow();
            
        } catch (Exception e) {
            Logger.error("SSHClientController: Failed to start client: " + e.getMessage());
            view.displayError("Failed to start client: " + e.getMessage());
        }
    }

    /**
     * Start the connection process (called by GUI after user input).
     */
    public void startConnectionFlow() throws Exception {
        Logger.info("SSHClientController: Starting connection flow");
        
        // Get server information from view
        view.showConnectionProgress("Requesting server connection details...");
        ServerInfo serverInfo = view.getServerInfoFromUser();
        
        // Get authentication credentials from view
        view.showConnectionProgress("Requesting user credentials...");
        CredentialsManager tempManager = new CredentialsManager("config/credentials.properties");
        AuthCredentials credentials = view.getAuthCredentials(tempManager.getAvailableUsers());
        
        if (credentials == null) {
            throw new Exception("Authentication cancelled by user");
        }
        
        // Delegate to model for connection logic
        model.connect(serverInfo, credentials);
        
        // For console clients, handle service selection after authentication
        if (view instanceof ConsoleClientUI) {
            handleConsoleServiceSelection();
        } else {
            // For GUI clients, automatically request shell service
            try {
                model.requestService("shell");
                Logger.info("SSHClientController: Shell service requested for GUI client");
            } catch (Exception e) {
                Logger.error("SSHClientController: Failed to request shell service for GUI: " + e.getMessage());
                view.displayError("Failed to initialize shell: " + e.getMessage());
            }
        }
    }

    /**
     * Handle service selection for console clients.
     */
    private void handleConsoleServiceSelection() {
        while (running && view.shouldContinue()) {
            try {
                String service = view.selectService();
                
                switch (service) {
                    case "shell":
                        handleConsoleShell();
                        break;
                    case "file-transfer":
                        handleConsoleFileTransfer();
                        break;
                    case "exit":
                        running = false;
                        break;
                    default:
                        view.displayError("Unknown service: " + service);
                        break;
                }
            } catch (Exception e) {
                Logger.error("SSHClientController: Error in service selection: " + e.getMessage());
                view.displayError("Service selection error: " + e.getMessage());
                break;
            }
        }
        
        // Clean up when done
        stop();
    }
    
    /**
     * Handle shell mode for console clients.
     */
    private void handleConsoleShell() {
        try {
            // Request shell service
            model.requestService("shell");
            view.displayMessage("Entering shell mode. Type 'exit' to return to service menu.");
        } catch (Exception e) {
            Logger.error("SSHClientController: Failed to request shell service: " + e.getMessage());
            view.displayError("Failed to enter shell mode: " + e.getMessage());
            return;
        }
        
        while (running && view.shouldContinue() && model.isConnected()) {
            try {
                // Fetch the current working directory from the model
                String currentDir = model.getWorkingDirectory();
                if (currentDir == null || currentDir.isEmpty()) {
                    currentDir = "~";
                }
                // Show prompt with working directory
                String prompt = currentDir + " $ ";
                String command = view.getInput(prompt);
                
                if (command == null || command.trim().isEmpty()) {
                    continue;
                }
                
                if (command.trim().equalsIgnoreCase("exit")) {
                    break;
                }
                
                // Echo the command (like a real shell)
                view.displayMessage(prompt + command);
                
                // Send command to model
                model.sendShellCommand(command);
                
            } catch (Exception e) {
                Logger.error("SSHClientController: Error in shell mode: " + e.getMessage());
                view.displayError("Shell error: " + e.getMessage());
                break;
            }
        }
    }
    
    /**
     * Handle file transfer mode for console clients.
     */
    private void handleConsoleFileTransfer() {
        view.displayMessage("File transfer mode not yet implemented for console client.");
        view.displayMessage("Please use the GUI client for file transfers.");
    }

    /**
     * Handle shell command from view.
     */
    public void handleShellCommand(String command) {
        try {
            Logger.info("SSHClientController: Handling shell command: " + command);
            model.sendShellCommand(command);
        } catch (Exception e) {
            Logger.error("SSHClientController: Error handling shell command: " + e.getMessage());
            view.displayError("Error sending shell command: " + e.getMessage());
        }
    }

    /**
     * Handle file upload request from view.
     */
    public void handleFileUpload(java.io.File file) {
        try {
            Logger.info("SSHClientController: Handling file upload: " + file.getName());
            model.uploadFile(file.getAbsolutePath(), file.getName());
        } catch (Exception e) {
            Logger.error("SSHClientController: Error handling file upload: " + e.getMessage());
            view.displayError("File upload failed: " + e.getMessage());
        }
    }

    /**
     * Handle file download request from view.
     */
    public void handleFileDownload(String remotePath) {
        try {
            Logger.info("SSHClientController: Handling file download: " + remotePath);
            String localPath = "downloads/" + new java.io.File(remotePath).getName();
            model.downloadFile(remotePath, localPath);
        } catch (Exception e) {
            Logger.error("SSHClientController: Error handling file download: " + e.getMessage());
            view.displayError("File download failed: " + e.getMessage());
        }
    }

    /**
     * Stop the client.
     */
    public void stop() {
        Logger.info("SSHClientController: Stopping client");
        running = false;
        model.disconnect();
    }

    /**
     * Get the current session ID.
     */
    public String getSessionId() {
        return model.getSessionId();
    }

    /**
     * Get the current working directory.
     */
    public String getWorkingDirectory() {
        return model.getWorkingDirectory();
    }

    /**
     * Check if the client should continue running.
     */
    public boolean shouldContinue() {
        return running && view.shouldContinue();
    }

    /**
     * Get authentication credentials for the specified user.
     * This moves business logic from the view to the controller.
     */
    public AuthCredentials getAuthCredentials(String username) {
        try {
            CredentialsManager credentialsManager = new CredentialsManager("config/credentials.properties");
            return credentialsManager.getAuthCredentials(username);
        } catch (Exception e) {
            Logger.error("SSHClientController: Failed to get credentials for user " + username + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Get available users for authentication.
     */
    public String[] getAvailableUsers() {
        try {
            CredentialsManager credentialsManager = new CredentialsManager("config/credentials.properties");
            return credentialsManager.getAvailableUsers();
        } catch (Exception e) {
            Logger.error("SSHClientController: Failed to get available users: " + e.getMessage());
            return new String[0];
        }
    }

    /**
     * Check if the client is connected.
     */
    public boolean isConnected() {
        return model.isConnected();
    }

    /**
     * Check if the client is authenticated.
     */
    public boolean isAuthenticated() {
        return model.isAuthenticated();
    }

    /**
     * Create a new verified user.
     * This moves business logic from the view to the controller.
     */
    public boolean createUser(String username, String password) {
        try {
            Logger.info("SSHClientController: Creating user: " + username);
            ssh.utils.CreateVerifiedUser.createUser(username, password);
            return true;
        } catch (Exception e) {
            Logger.error("SSHClientController: Failed to create user " + username + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Delete a verified user.
     * This moves business logic from the view to the controller.
     */
    public boolean deleteUser(String username) {
        try {
            Logger.info("SSHClientController: Deleting user: " + username);
            ssh.utils.DeleteVerifiedUser.deleteUser(username);
            return true;
        } catch (Exception e) {
            Logger.error("SSHClientController: Failed to delete user " + username + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Handle service requested from model.
     */
    private void handleServiceRequested(String service) {
        Logger.info("SSHClientController: Service requested: " + service);
        
        // For GUI clients, transition to main window after service is requested
        if (view instanceof ssh.client.view.JavaFXClientUI) {
            ssh.client.view.JavaFXClientUI guiView = (ssh.client.view.JavaFXClientUI) view;
            guiView.showMainWindow();
        }
    }

    /**
     * Handle a port forward request from the UI.
     */
    public void handlePortForwardRequest(boolean isLocal, int sourcePort, String destHost, int destPort) {
        try {
            if (isLocal) {
                model.requestLocalPortForward(sourcePort, destHost, destPort);
                view.displayMessage("Started local port forward: localhost:" + sourcePort + " -> " + destHost + ":" + destPort);
            } else {
                model.requestRemotePortForward(sourcePort, destHost, destPort);
                view.displayMessage("Started remote port forward: server:" + sourcePort + " -> " + destHost + ":" + destPort);
            }
        } catch (Exception e) {
            view.displayError("Failed to start port forward: " + e.getMessage());
        }
    }
} 