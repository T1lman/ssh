package ssh.client.view;

import javafx.application.Platform;
import javafx.stage.Stage;
import ssh.client.model.AuthCredentials;
import ssh.client.model.ServerInfo;
import ssh.utils.CredentialsManager;
import ssh.utils.Logger;

import java.util.function.Consumer;

/**
 * Main JavaFX client UI implementation.
 * Orchestrates the startup scene and main window, delegating specific functionality to specialized classes.
 */
public class JavaFXClientUI implements ClientUI {

    private Stage primaryStage;
    
    // UI Components
    private StartupScene startupScene;
    private MainWindow mainWindow;
    
    // State
    private ServerInfo pendingServerInfo;
    private ssh.client.controller.SSHClientController controller;

    // Event/callback fields
    private Consumer<AuthCredentials> onLoginRequested;
    private Consumer<String> onCommandEntered;
    private Consumer<String> onFileTransferProgress;
    private Consumer<String> onConnectionStatus;
    private Consumer<String> onAuthenticationResult;
    private Consumer<String> onShellOutput;
    private Consumer<String> onWorkingDirectoryUpdate;
    private Consumer<String> onShellCommand;
    private Consumer<java.io.File> onFileUploadRequested;
    private Consumer<String> onFileDownloadRequested;

    public JavaFXClientUI(Stage primaryStage) {
        this.primaryStage = primaryStage;
        this.primaryStage.setTitle("SSH Client");
        
        // Create the controller first
        this.controller = new ssh.client.controller.SSHClientController(this);
        
        // Initialize startup scene
        createStartupScene();
        
        // Show the stage immediately with the startup scene
        this.primaryStage.show();
    }

    private void createStartupScene() {
        // Create startup scene with controller reference
        startupScene = new StartupScene(primaryStage, controller);
        
        // Set up callbacks
        startupScene.setOnConnectionRequested(serverInfo -> {
            // This is handled by the login flow
        });
        
        startupScene.setOnLoginRequested(this::handleLogin);
        
        startupScene.setOnCancelRequested(() -> {
            Logger.info("User cancelled connection");
            Platform.exit();
        });
        
        // Create a Scene and set it on the Stage
        javafx.scene.Scene scene = new javafx.scene.Scene(startupScene.getRoot());
        primaryStage.setScene(scene);
    }

    private void handleLogin(ServerInfo serverInfo) {
        try {
            Logger.debug("handleLogin called with user: " + (serverInfo != null ? serverInfo.getUsername() : "null"));
            this.pendingServerInfo = serverInfo;
            // Controller is already created in constructor
            // Start the connection process
            controller.startConnectionFlow();
        } catch (Exception e) {
            Logger.error("Failed to handle login: " + e.getMessage());
            startupScene.showError("Failed to start connection: " + e.getMessage());
        }
    }

    public void showMainWindow() {
        Logger.debug("showMainWindow() called");
        
        // Create main window (this will handle its own scene creation)
        mainWindow = new MainWindow(primaryStage);
        
        // Set the controller for business logic operations
        if (controller != null) {
            mainWindow.setController(controller);
        }
        
        // Set up working directory provider
        if (controller != null) {
            mainWindow.setWorkingDirectoryProvider(() -> controller.getWorkingDirectory());
        }
        
        // Propagate the command handler if it was already set
        if (onCommandEntered != null) {
            mainWindow.setOnCommandEntered(onCommandEntered);
        }
        
        // Set file transfer callbacks
        mainWindow.setOnFileUploadRequested(file -> {
            if (controller != null && file != null) {
                if (onFileUploadRequested != null) {
                    onFileUploadRequested.accept(file);
                }
            }
        });
        mainWindow.setOnFileDownloadRequested(remotePath -> {
            if (controller != null && remotePath != null && !remotePath.isEmpty()) {
                if (onFileDownloadRequested != null) {
                    onFileDownloadRequested.accept(remotePath);
                }
            }
        });
        
        // Set up port forwarding callback
        if (mainWindow != null) {
            mainWindow.setOnPortForwardRequested(req -> {
                if (controller != null) {
                    controller.handlePortForwardRequest(req.isLocal, req.sourcePort, req.destHost, req.destPort);
                }
            });
        }
        
        // Update the header with user and server info and session id
        if (pendingServerInfo != null) {
            String username = pendingServerInfo.getUsername();
            String host = pendingServerInfo.getHost();
            int port = pendingServerInfo.getPort();
            String sessionId = controller != null ? controller.getSessionId() : "-";
            mainWindow.updateHeader(username, host, port, sessionId);
        }
        
        Logger.debug("showMainWindow() completed");
        Logger.debug("Main window setup complete");
    }

    @Override
    public ServerInfo getServerInfoFromUser() {
        // This is handled by the startup scene
        return pendingServerInfo;
    }

    @Override
    public void displayMessage(String message) {
        if (mainWindow != null) {
            Platform.runLater(() -> mainWindow.displayMessage(message));
        }
    }

    @Override
    public void displayError(String error) {
        if (mainWindow != null) {
            Platform.runLater(() -> mainWindow.displayError(error));
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
            Platform.runLater(() -> mainWindow.showConnectionStatus(connected));
        }
    }

    @Override
    public void showAuthenticationResult(boolean success, String message) {
        if (mainWindow != null) {
            Platform.runLater(() -> mainWindow.showAuthenticationResult(success, message));
        }
    }

    @Override
    public void displayShellOutput(String output) {
        if (mainWindow != null) {
            Platform.runLater(() -> mainWindow.displayShellOutput(output));
        }
    }

    @Override
    public void showFileTransferProgress(String filename, int percentage) {
        if (mainWindow != null) {
            Platform.runLater(() -> mainWindow.showFileTransferProgress(filename, percentage));
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
        Logger.debug("getAuthCredentials called with " + (availableUsers != null ? availableUsers.length : 0) + " users");
        
        if (startupScene != null && pendingServerInfo != null) {
            String selectedUser = pendingServerInfo.getUsername();
            if (selectedUser != null && !selectedUser.isEmpty()) {
                // Delegate to controller for business logic
                AuthCredentials credentials = controller.getAuthCredentials(selectedUser);
                Logger.debug("Returning credentials for user: " + selectedUser);
                return credentials;
            } else {
                Logger.debug("No user selected, returning null");
                return null;
            }
        }
        return null;
    }

    @Override
    public void showConnectionProgress(String step) {
        Logger.debug("GUI_PROGRESS: " + step);
        if (startupScene != null) {
            Platform.runLater(() -> startupScene.showConnectingState(step));
        }
    }

    @Override
    public boolean shouldContinue() {
        // The GUI's lifecycle is managed by the user closing the window
        return true;
    }
    
    public void updateWorkingDirectory(String newWorkingDirectory) {
        if (mainWindow != null) {
            Platform.runLater(() -> mainWindow.updateWorkingDirectory(newWorkingDirectory));
        }
    }

    public void displayShellCommand(String command, String output, String workingDirectory) {
        if (mainWindow != null) {
            Platform.runLater(() -> mainWindow.displayShellCommand(command, output, workingDirectory));
        }
    }

    // Keep the old method for backward compatibility
    public void displayShellCommand(String command, String output) {
        if (mainWindow != null) {
            Platform.runLater(() -> mainWindow.displayShellCommand(command, output));
        }
    }

    // Event/callback methods
    public void setOnLoginRequested(Consumer<AuthCredentials> onLoginRequested) {
        this.onLoginRequested = onLoginRequested;
    }

    public void setOnCommandEntered(Consumer<String> onCommandEntered) {
        this.onCommandEntered = onCommandEntered;
        if (mainWindow != null) {
            mainWindow.setOnCommandEntered(onCommandEntered);
        }
    }

    public void setOnFileTransferProgress(Consumer<String> onFileTransferProgress) {
        this.onFileTransferProgress = onFileTransferProgress;
    }

    public void setOnConnectionStatus(Consumer<String> onConnectionStatus) {
        this.onConnectionStatus = onConnectionStatus;
    }

    public void setOnAuthenticationResult(Consumer<String> onAuthenticationResult) {
        this.onAuthenticationResult = onAuthenticationResult;
    }

    public void setOnShellOutput(Consumer<String> onShellOutput) {
        this.onShellOutput = onShellOutput;
    }

    public void setOnWorkingDirectoryUpdate(Consumer<String> onWorkingDirectoryUpdate) {
        this.onWorkingDirectoryUpdate = onWorkingDirectoryUpdate;
    }

    public void setOnShellCommand(Consumer<String> onShellCommand) {
        this.onShellCommand = onShellCommand;
    }

    public void setOnFileUploadRequested(Consumer<java.io.File> onFileUploadRequested) {
        this.onFileUploadRequested = onFileUploadRequested;
    }

    public void setOnFileDownloadRequested(Consumer<String> onFileDownloadRequested) {
        this.onFileDownloadRequested = onFileDownloadRequested;
    }

    public MainWindow getMainWindow() {
        return mainWindow;
    }
} 