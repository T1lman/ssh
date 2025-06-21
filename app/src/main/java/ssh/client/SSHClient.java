package ssh.client;

import ssh.client.ui.AuthCredentials;
import ssh.client.ui.ClientUI;
import ssh.client.ui.ServerInfo;

/**
 * Main SSH client class that manages the client lifecycle.
 */
public class SSHClient {
    private ClientUI ui;
    private ClientConnection connection;
    private boolean running;

    public SSHClient(ClientUI ui) {
        this.ui = ui;
        this.running = true;
    }

    /**
     * Start the SSH client.
     */
    public void start() {
        try {
            // Get server information
            ServerInfo serverInfo = ui.getServerInfo();
            
            // Get authentication credentials
            AuthCredentials credentials = ui.getAuthCredentials();
            
            // Create connection
            connection = new ClientConnection(serverInfo, credentials, ui);
            
            // Connect to server
            ui.showConnectionProgress("Connecting to server...");
            if (!connection.connect()) {
                ui.displayError("Failed to connect to server");
                return;
            }
            
            ui.showConnectionStatus(true);
            ui.displayMessage("Connected to server successfully");
            
            // Perform key exchange
            ui.showConnectionProgress("Performing key exchange...");
            if (!connection.performKeyExchange()) {
                ui.displayError("Key exchange failed");
                return;
            }
            
            // Authenticate
            ui.showConnectionProgress("Authenticating...");
            if (!connection.authenticate()) {
                ui.displayError("Authentication failed");
                return;
            }
            
            ui.displayMessage("Authentication successful");
            
            // Main client loop
            mainLoop();
            
        } catch (Exception e) {
            ui.displayError("Client error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            cleanup();
        }
    }

    /**
     * Main client interaction loop.
     */
    private void mainLoop() {
        while (running && ui.shouldContinue()) {
            try {
                // Select service
                String service = ui.selectService();
                
                if ("exit".equals(service)) {
                    running = false;
                    break;
                }
                
                // Handle service
                switch (service) {
                    case "shell":
                        handleShellService();
                        break;
                    case "file-transfer":
                        handleFileTransferService();
                        break;
                    default:
                        ui.displayError("Unknown service: " + service);
                        break;
                }
                
            } catch (Exception e) {
                ui.displayError("Error in main loop: " + e.getMessage());
            }
        }
    }

    /**
     * Handle shell service.
     */
    private void handleShellService() {
        ui.displayMessage("Shell service selected");
        
        while (ui.shouldContinue()) {
            String command = ui.getInput("Enter command (or 'exit' to quit shell)");
            
            if ("exit".equals(command.trim())) {
                break;
            }
            
            if (command.trim().isEmpty()) {
                continue;
            }
            
            try {
                // Send command to server
                connection.sendShellCommand(command);
                
                // Get response
                String response = connection.receiveShellResponse();
                ui.displayShellOutput(response);
                
            } catch (Exception e) {
                ui.displayError("Error executing command: " + e.getMessage());
            }
        }
    }

    /**
     * Handle file transfer service.
     */
    private void handleFileTransferService() {
        ui.displayMessage("File transfer service selected");
        
        String operation = ui.getInput("Operation (upload/download)");
        
        if ("upload".equals(operation.toLowerCase())) {
            handleFileUpload();
        } else if ("download".equals(operation.toLowerCase())) {
            handleFileDownload();
        } else {
            ui.displayError("Unknown operation: " + operation);
        }
    }

    /**
     * Handle file upload.
     */
    private void handleFileUpload() {
        String localPath = ui.getInput("Enter local file path");
        String remotePath = ui.getInput("Enter remote file path");
        
        try {
            connection.uploadFile(localPath, remotePath);
            ui.displayMessage("File upload completed");
        } catch (Exception e) {
            ui.displayError("File upload failed: " + e.getMessage());
        }
    }

    /**
     * Handle file download.
     */
    private void handleFileDownload() {
        String remotePath = ui.getInput("Enter remote file path");
        String localPath = ui.getInput("Enter local file path");
        
        try {
            connection.downloadFile(remotePath, localPath);
            ui.displayMessage("File download completed");
        } catch (Exception e) {
            ui.displayError("File download failed: " + e.getMessage());
        }
    }

    /**
     * Clean up resources.
     */
    private void cleanup() {
        if (connection != null) {
            connection.disconnect();
        }
        
        ui.showConnectionStatus(false);
        
        if (ui instanceof ssh.client.ui.ConsoleClientUI) {
            ((ssh.client.ui.ConsoleClientUI) ui).close();
        }
    }

    /**
     * Stop the client.
     */
    public void stop() {
        this.running = false;
    }
}