package ssh.client.gui;

import javafx.application.Platform;
import javafx.stage.Stage;
import ssh.client.SSHClient;
import ssh.client.ui.AuthCredentials;
import ssh.client.ui.ClientUI;
import ssh.client.ui.ServerInfo;
import ssh.utils.CredentialsManager;

/**
 * Main JavaFX client UI implementation.
 * Orchestrates the startup scene and main window, delegating specific functionality to specialized classes.
 */
public class JavaFXClientUI implements ClientUI {

    private Stage primaryStage;
    private SSHClient client;
    
    // UI Components
    private StartupScene startupScene;
    private MainWindow mainWindow;
    
    // State
    private ServerInfo pendingServerInfo;

    public JavaFXClientUI(Stage primaryStage) {
        this.primaryStage = primaryStage;
        this.primaryStage.setTitle("SSH Client");
        
        // Initialize startup scene
        createStartupScene();
        
        // Show the stage immediately with the startup scene
        this.primaryStage.show();
    }
    
    private void createStartupScene() {
        startupScene = new StartupScene(primaryStage);
        
        // Set up callbacks
        startupScene.setOnLoginRequested(this::handleLogin);
        startupScene.setOnCancelRequested(Platform::exit);
        
        // Set the scene
        primaryStage.setScene(new javafx.scene.Scene(startupScene.getRoot(), 450, 400));
        primaryStage.setMinWidth(450);
        primaryStage.setMinHeight(400);
        primaryStage.setResizable(false);
    }
    
    private void handleLogin(String selectedUser) {
        pendingServerInfo = startupScene.getPendingServerInfo();
        pendingServerInfo.setUsername(selectedUser);
        
        // Show connecting state
        startupScene.showConnectingState("Connecting to " + pendingServerInfo.getHost() + ":" + pendingServerInfo.getPort() + " as " + selectedUser + "...");
        
        // Create SSH client with null credentials - they will be requested in startConnection()
        client = new SSHClient(pendingServerInfo, null, this);
        
        // Start the connection process in a background thread
        new Thread(() -> {
            try {
                client.startConnection();
            } catch (Exception e) {
                Platform.runLater(() -> {
                    startupScene.showError("Connection failed: " + e.getMessage());
                });
            }
        }).start();
    }

    public void setClient(SSHClient client) {
        this.client = client;
    }

    public SSHClient getClient() {
        return client;
    }

    public void showMainWindow() {
        System.out.println("DEBUG: Authentication successful, showing main window");
        
        if (!Platform.isFxApplicationThread()) {
            System.out.println("DEBUG: Not on FX thread, using Platform.runLater");
            Platform.runLater(this::showMainWindowDirectly);
            return;
        }
        
        showMainWindowDirectly();
    }
    
    private void showMainWindowDirectly() {
        System.out.println("DEBUG: showMainWindow() called");
        
        // Create main window
        mainWindow = new MainWindow(primaryStage, client, this);
        
        System.out.println("DEBUG: showMainWindow() completed");
        System.out.println("DEBUG: Main window setup complete");
    }

    @Override
    public ServerInfo getServerInfoFromUser() {
        // This is handled by the startup scene
        return pendingServerInfo;
    }

    @Override
    public void displayMessage(String message) {
        if (mainWindow != null) {
            mainWindow.displayMessage(message);
        }
    }

    @Override
    public void displayError(String error) {
        if (mainWindow != null) {
            mainWindow.displayError(error);
        }
    }

    @Override
    public String getInput(String prompt) {
        // Not used in GUI mode
        return "";
    }

    @Override
    public String getPassword(String prompt) {
        // Not used in GUI mode
        return "";
    }

    @Override
    public void showConnectionStatus(boolean connected) {
        if (mainWindow != null) {
            mainWindow.showConnectionStatus(connected);
        }
    }

    @Override
    public void showAuthenticationResult(boolean success, String message) {
        if (mainWindow != null) {
            mainWindow.showAuthenticationResult(success, message);
        }
    }

    @Override
    public void displayShellOutput(String output) {
        if (mainWindow != null) {
            mainWindow.displayShellOutput(output);
        }
    }

    @Override
    public void showFileTransferProgress(String filename, int percentage) {
        if (mainWindow != null) {
            mainWindow.showFileTransferProgress(filename, percentage);
        }
    }

    @Override
    public String selectService() {
        // Default to shell service
        return "shell";
    }

    @Override
    public ServerInfo getServerInfo() {
        return pendingServerInfo;
    }

    @Override
    public AuthCredentials getAuthCredentials(String[] availableUsers) {
        System.out.println("DEBUG: getAuthCredentials called with " + (availableUsers != null ? availableUsers.length : 0) + " users");
        
        // Return credentials for the user selected in the startup window
        if (pendingServerInfo != null && pendingServerInfo.getUsername() != null) {
            String selectedUser = pendingServerInfo.getUsername();
            try {
                CredentialsManager credentialsManager = new CredentialsManager("config/credentials.properties");
                AuthCredentials credentials = credentialsManager.getAuthCredentials(selectedUser);
                System.out.println("DEBUG: Returning credentials for user: " + selectedUser);
                return credentials;
            } catch (Exception e) {
                System.out.println("DEBUG: Error getting credentials for user " + selectedUser + ": " + e.getMessage());
                return null;
            }
        }
        
        // Fallback: if somehow we don't have a selected user, return null
        System.out.println("DEBUG: No user selected, returning null");
        return null;
    }

    @Override
    public void showConnectionProgress(String step) {
        // Update the startup status label if we're still in startup phase
        Platform.runLater(() -> {
            if (startupScene != null) {
                startupScene.showConnectingState(step);
            }
            System.out.println("GUI_PROGRESS: " + step);
        });
    }

    @Override
    public boolean shouldContinue() {
        // The GUI's lifecycle is managed by the user closing the window
        return true;
    }
    
    public void updateWorkingDirectory(String newWorkingDirectory) {
        if (mainWindow != null) {
            mainWindow.updateWorkingDirectory(newWorkingDirectory);
        }
    }

    public void displayShellCommand(String command, String output) {
        if (mainWindow != null) {
            mainWindow.displayShellCommand(command, output);
        }
    }
} 