package ssh.client;

import ssh.client.ui.AuthCredentials;
import ssh.client.ui.ClientUI;
import ssh.client.ui.ServerInfo;
import ssh.utils.Logger;

/**
 * Main SSH client class that manages the client lifecycle.
 */
public class SSHClient {
    private ClientUI ui;
    private ClientConnection connection;
    private ServerInfo serverInfo;
    private AuthCredentials credentials;
    private boolean running;

    public SSHClient(ClientUI ui) {
        this.ui = ui;
        this.running = true;
    }
    
    public SSHClient(ServerInfo serverInfo, AuthCredentials credentials, ClientUI ui) {
        this.serverInfo = serverInfo;
        this.credentials = credentials;
        this.ui = ui;
        this.running = true;
    }

    /**
     * Start the SSH client.
     */
    public void start() {
        try {
            // Get server information if not already provided
            if (this.serverInfo == null) {
                this.serverInfo = ui.getServerInfo();
            }
            
            // Get authentication credentials if not already provided
            if (this.credentials == null) {
                this.credentials = ui.getAuthCredentials();
            }
            
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
            
        } catch (OutOfMemoryError e) {
            Logger.error("OutOfMemoryError occurred: " + e.getMessage());
            ui.displayError("OutOfMemoryError: " + e.getMessage());
            System.gc(); // Force garbage collection
            e.printStackTrace();
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
        try {
            // Directly enter the interactive loop after authentication.
            interactiveLoop();
            
        } catch (Exception e) {
            ui.displayError("Error in main loop: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Interactive loop for user input.
     */
    private void interactiveLoop() {
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
                
                // Force garbage collection periodically
                System.gc();
                
            } catch (Exception e) {
                ui.displayError("Error in interactive loop: " + e.getMessage());
                // Add a small delay to prevent tight error loops
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    /**
     * Handle shell service.
     */
    private void handleShellService() {
        ui.displayMessage("Shell service selected");
        
        while (ui.shouldContinue()) {
            String prompt = String.format("[%s] $ ", connection.getWorkingDirectory());
            String command = ui.getInput(prompt);
            
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
        try {
            if (connection != null) {
                // Send disconnect message if not already sent
                connection.sendDisconnect();
                connection.disconnect();
                connection = null;
            }
            
            ui.showConnectionStatus(false);
            
            if (ui instanceof ssh.client.ui.ConsoleClientUI) {
                ((ssh.client.ui.ConsoleClientUI) ui).close();
            }
            
            // Force garbage collection to free memory
            System.gc();
            
        } catch (Exception e) {
            Logger.error("Error during cleanup: " + e.getMessage());
        }
    }

    /**
     * Stop the client.
     */
    public void stop() {
        this.running = false;
    }
}