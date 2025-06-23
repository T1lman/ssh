package ssh.client;

import ssh.client.ui.AuthCredentials;
import ssh.client.ui.ClientUI;
import ssh.client.ui.ServerInfo;
import ssh.utils.CredentialsManager;
import ssh.utils.Logger;
import ssh.protocol.messages.ShellMessage;
import ssh.protocol.messages.ServiceMessage;
import java.io.IOException;

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
            System.out.println("DEBUG: SSHClient.start() called");
            
            // For GUI clients, don't start the connection process immediately
            // The GUI will handle user input and call startConnection() when ready
            if (ui instanceof ssh.client.gui.JavaFXClientUI) {
                System.out.println("DEBUG: GUI client - waiting for user input before starting connection");
                return;
            }
            
            // For console clients, proceed with the normal flow
            startConnection();
            
        } catch (OutOfMemoryError e) {
            Logger.error("OutOfMemoryError occurred: " + e.getMessage());
            ui.displayError("OutOfMemoryError: " + e.getMessage());
            System.gc(); // Force garbage collection
            e.printStackTrace();
        } catch (Exception e) {
            ui.displayError("Client error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Don't cleanup immediately for GUI clients
            if (!(ui instanceof ssh.client.gui.JavaFXClientUI)) {
                cleanup();
            }
        }
    }
    
    /**
     * Start the connection process (called by GUI after user input).
     */
    public void startConnection() {
        try {
            System.out.println("DEBUG: startConnection() called");
            ui.showConnectionProgress("Requesting user credentials...");
            // Use a temporary manager to get available users before full connection
            CredentialsManager tempManager = new CredentialsManager("config/credentials.properties");
            System.out.println("DEBUG: Got credentials manager, requesting auth");
            this.credentials = ui.getAuthCredentials(tempManager.getAvailableUsers());

            if (credentials == null) {
                System.out.println("DEBUG: Authentication cancelled by user");
                ui.displayError("Authentication cancelled.");
                return;
            }

            System.out.println("DEBUG: Authentication successful for user: " + credentials.getUsername());

            // Set the username in server info
            serverInfo.setUsername(credentials.getUsername());

            // Get server information if not already provided
            if (this.serverInfo == null) {
                this.serverInfo = ui.getServerInfo();
            }
            
            // Create connection
            System.out.println("DEBUG: Creating client connection");
            connection = new ClientConnection(serverInfo, credentials, ui);
            
            // Connect to server
            ui.showConnectionProgress("Connecting to server...");
            System.out.println("DEBUG: Attempting to connect to server");
            if (!connection.connect()) {
                System.out.println("DEBUG: Connection failed");
                ui.displayError("Failed to connect to server");
                return;
            }
            
            System.out.println("DEBUG: Connection successful");
            ui.showConnectionStatus(true);
            
            // Perform key exchange
            ui.showConnectionProgress("Performing key exchange...");
            System.out.println("DEBUG: Starting key exchange");
            if (!connection.performKeyExchange()) {
                System.out.println("DEBUG: Key exchange failed");
                ui.displayError("Key exchange failed");
                return;
            }
            
            System.out.println("DEBUG: Key exchange successful");
            
            // Authenticate
            ui.showConnectionProgress("Authenticating...");
            System.out.println("DEBUG: Starting authentication");
            if (!connection.authenticate()) {
                System.out.println("DEBUG: Authentication failed");
                ui.displayError("Authentication failed");
                return;
            }
            
            System.out.println("DEBUG: Authentication successful");
            ui.showAuthenticationResult(true, "Authentication successful");
            
            // For GUI clients, transition to main window after authentication
            if (ui instanceof ssh.client.gui.JavaFXClientUI) {
                System.out.println("DEBUG: GUI client - authentication successful, showing main window");
                
                // Start message handling thread for GUI clients
                handleIncomingMessages();
                
                ((ssh.client.gui.JavaFXClientUI) ui).showMainWindow();
                return;
            }
            
            // Main client loop (for console clients)
            System.out.println("DEBUG: Starting main client loop");
            mainLoop();
            
        } catch (Exception e) {
            ui.displayError("Client error: " + e.getMessage());
            e.printStackTrace();
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
                sendShellCommand(command);
                
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

    /**
     * Send a disconnect message to the server.
     */
    public void sendDisconnect() {
        if (connection != null && connection.isActive()) {
            connection.sendDisconnect();
        }
    }

    /**
     * Request the server to reload its user database.
     */
    public void reloadServerUsers() {
        if (connection != null && connection.isActive()) {
            connection.sendReloadUsers();
        }
    }

    /**
     * Get the client connection for direct access (used by GUI).
     */
    public ClientConnection getConnection() {
        return connection;
    }

    public void sendShellCommand(String command) {
        if (connection != null && connection.isActive()) {
            try {
                connection.sendShellCommand(command);
                
                // For GUI clients, receive the response in a background thread
                if (ui instanceof ssh.client.gui.JavaFXClientUI) {
                    new Thread(() -> {
                        try {
                            String response = connection.receiveShellResponse();
                            
                            // Pass both command and response to the GUI
                            if (ui instanceof ssh.client.gui.JavaFXClientUI) {
                                ((ssh.client.gui.JavaFXClientUI) ui).displayShellCommand(command, response);
                            }
                            
                            // Update the working directory display in the GUI
                            String newWorkingDirectory = connection.getWorkingDirectory();
                            if (ui instanceof ssh.client.gui.JavaFXClientUI) {
                                ((ssh.client.gui.JavaFXClientUI) ui).updateWorkingDirectory(newWorkingDirectory);
                            }
                        } catch (Exception e) {
                            ui.displayError("Error receiving response: " + e.getMessage());
                        }
                    }).start();
                }
            } catch (Exception e) {
                ui.displayError("Error sending command: " + e.getMessage());
            }
        } else {
            ui.displayError("Not connected to server.");
        }
    }

    private void handleIncomingMessages() {
        new Thread(() -> {
            try {
                while (running && connection != null && connection.isActive()) {
                    try {
                        // For GUI clients, we'll use the existing receiveShellResponse method
                        // which handles the message receiving internally
                        // This method will be called when we send a shell command
                        
                        // Just keep the thread alive to handle responses
                        Thread.sleep(100);
                        
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        Logger.error("Unexpected error in message handling: " + e.getMessage());
                        break;
                    }
                }
            } catch (Exception e) {
                Logger.error("Error in handleIncomingMessages: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }
}