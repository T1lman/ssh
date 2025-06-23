package ssh.client.gui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import ssh.client.SSHClient;
import ssh.client.ui.ClientUI;

/**
 * Handles the main SSH terminal interface.
 * Manages the terminal output, command input, and action buttons.
 */
public class MainWindow {
    private final Stage primaryStage;
    private final SSHClient client;
    private final ClientUI parentUI;
    
    // UI Components
    private TextArea terminalOutput;
    private ShellInputField commandInput;
    private String currentWorkingDirectory = "~";
    
    // Action buttons
    private Button fileTransferButton;
    private Button manageUsersButton;
    private Button disconnectButton;
    
    // Dialogs
    private FileTransferDialog fileTransferDialog;
    private UserManagementDialog userManagementDialog;
    
    public MainWindow(Stage primaryStage, SSHClient client, ClientUI parentUI) {
        this.primaryStage = primaryStage;
        this.client = client;
        this.parentUI = parentUI;
        createMainWindow();
    }
    
    private void createMainWindow() {
        System.out.println("DEBUG: Creating main window on FX thread");
        
        // Create the main layout
        BorderPane mainLayout = new BorderPane();
        mainLayout.setStyle("-fx-background-color: #2c3e50;");
        
        // Create terminal output area
        terminalOutput = new TextArea();
        terminalOutput.setEditable(false);
        terminalOutput.setWrapText(true);
        terminalOutput.setStyle("-fx-control-inner-background: #34495e; -fx-text-fill: #ecf0f1; -fx-font-family: 'Monaco', 'Consolas', monospace; -fx-font-size: 12px;");
        terminalOutput.setPrefRowCount(20);
        
        // Add initial welcome message
        terminalOutput.appendText("SSH Terminal Connected\n");
        terminalOutput.appendText("Type commands to execute on the server.\n\n");
        
        // Create command input field
        commandInput = new ShellInputField();
        commandInput.setStyle("-fx-control-inner-background: #34495e; -fx-text-fill: #ecf0f1; -fx-font-family: 'Monaco', 'Consolas', monospace; -fx-font-size: 12px; -fx-border-color: #3498db; -fx-border-radius: 3px;");
        commandInput.setPrompt(formatShellPrompt(currentWorkingDirectory));
        
        // Set up command input behavior
        commandInput.setOnAction(e -> {
            String command = commandInput.getCommand();
            if (!command.trim().isEmpty()) {
                try {
                    client.sendShellCommand(command);
                    commandInput.clearCommand();
                } catch (Exception ex) {
                    displayError("Failed to send command: " + ex.getMessage());
                }
            }
        });
        
        // Create action buttons
        createActionButtons();
        
        // Create button panel
        HBox buttonPanel = new HBox(10);
        buttonPanel.setPadding(new Insets(10));
        buttonPanel.setStyle("-fx-background-color: #34495e;");
        buttonPanel.getChildren().addAll(fileTransferButton, manageUsersButton, disconnectButton);
        
        // Assemble the layout
        VBox terminalPanel = new VBox(5);
        terminalPanel.setPadding(new Insets(10));
        terminalPanel.getChildren().addAll(terminalOutput, commandInput);
        
        mainLayout.setCenter(terminalPanel);
        mainLayout.setBottom(buttonPanel);
        
        // Set up the scene
        primaryStage.setScene(new javafx.scene.Scene(mainLayout, 800, 600));
        primaryStage.setTitle("SSH Client - Terminal");
        primaryStage.setMinWidth(600);
        primaryStage.setMinHeight(400);
        
        // Focus on command input
        Platform.runLater(commandInput::requestFocus);
        
        System.out.println("DEBUG: Main window created, ensuring stage is visible");
        primaryStage.show();
        primaryStage.toFront();
        
        System.out.println("DEBUG: Requesting focus on command input");
        Platform.runLater(commandInput::requestFocus);
    }
    
    private void createActionButtons() {
        fileTransferButton = UIUtils.createPrimaryButton("File Transfer");
        fileTransferButton.setOnAction(e -> handleFileTransfer());
        
        manageUsersButton = UIUtils.createSecondaryButton("Manage SSH Users");
        manageUsersButton.setOnAction(e -> handleManageSSHUsers());
        
        disconnectButton = UIUtils.createRedButton("Disconnect");
        disconnectButton.setOnAction(e -> handleDisconnect());
    }
    
    private void handleFileTransfer() {
        if (fileTransferDialog == null) {
            fileTransferDialog = new FileTransferDialog(primaryStage, client.getConnection());
        }
        fileTransferDialog.show();
    }
    
    private void handleManageSSHUsers() {
        if (userManagementDialog == null) {
            userManagementDialog = new UserManagementDialog(primaryStage, client);
        }
        userManagementDialog.show();
    }
    
    private void handleDisconnect() {
        if (client != null) {
            try {
                client.sendDisconnect();
            } catch (Exception e) {
                System.out.println("DEBUG: Error sending disconnect message: " + e.getMessage());
            }
            client.stop();
        }
        Platform.exit();
    }
    
    public void updateWorkingDirectory(String newWorkingDirectory) {
        this.currentWorkingDirectory = newWorkingDirectory;
        String formattedDir = UIUtils.formatWorkingDirectory(newWorkingDirectory);
        String prompt = formatShellPrompt(formattedDir);
        
        Platform.runLater(() -> {
            commandInput.setPrompt(prompt);
            commandInput.clearCommand();
        });
    }
    
    private String formatShellPrompt(String workingDirectory) {
        return workingDirectory + " $ ";
    }
    
    public void displayShellOutput(String output) {
        Platform.runLater(() -> {
            // Add the shell prompt with current working directory
            String prompt = formatShellPrompt(currentWorkingDirectory);
            terminalOutput.appendText(prompt);
            
            // Add the output
            if (output != null && !output.isEmpty()) {
                terminalOutput.appendText(output);
            }
            
            // Auto-scroll to bottom
            terminalOutput.setScrollTop(Double.MAX_VALUE);
        });
    }
    
    public void displayShellCommand(String command, String output) {
        Platform.runLater(() -> {
            // Add the shell prompt with current working directory and command
            String prompt = formatShellPrompt(currentWorkingDirectory);
            String commandLine = prompt + command + "\n";
            
            // Add the command line (commands will appear in the terminal's default color)
            terminalOutput.appendText(commandLine);
            
            // Add the output
            if (output != null && !output.isEmpty()) {
                terminalOutput.appendText(output);
            }
            
            // Add a subtle separator line after command output
            terminalOutput.appendText("\n");
            
            // Auto-scroll to bottom
            terminalOutput.setScrollTop(Double.MAX_VALUE);
        });
    }
    
    public void displayMessage(String message) {
        Platform.runLater(() -> {
            terminalOutput.appendText(message + "\n");
            terminalOutput.setScrollTop(Double.MAX_VALUE);
        });
    }
    
    public void displayError(String error) {
        Platform.runLater(() -> {
            terminalOutput.appendText("ERROR: " + error + "\n");
            terminalOutput.setScrollTop(Double.MAX_VALUE);
        });
    }
    
    public void showFileTransferProgress(String filename, int percentage) {
        if (fileTransferDialog != null) {
            fileTransferDialog.updateProgress(filename, percentage);
        }
    }
    
    public void showConnectionStatus(boolean connected) {
        Platform.runLater(() -> {
            if (connected) {
                primaryStage.setTitle("SSH Client - Terminal (Connected)");
            } else {
                primaryStage.setTitle("SSH Client - Terminal (Disconnected)");
            }
        });
    }
    
    public void showAuthenticationResult(boolean success, String message) {
        Platform.runLater(() -> {
            if (success) {
                terminalOutput.appendText("Authentication successful: " + message + "\n");
            } else {
                terminalOutput.appendText("Authentication failed: " + message + "\n");
            }
            terminalOutput.setScrollTop(Double.MAX_VALUE);
        });
    }
} 