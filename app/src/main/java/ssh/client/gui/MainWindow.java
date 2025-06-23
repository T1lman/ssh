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
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

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
        
        // Header bar
        HBox headerBar = new HBox();
        headerBar.setPadding(new Insets(0, 32, 0, 32));
        headerBar.setStyle("-fx-background-color: linear-gradient(to right, #232526, #414345); -fx-min-height: 54px; -fx-max-height: 54px;");
        headerBar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Label appName = new Label("SSH Client");
        appName.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #ecf0f1;");
        String sessionId = (client.getConnection() != null) ? client.getConnection().getSessionId() : "-";
        // Green status dot
        Label statusDot = new Label("●");
        statusDot.setStyle("-fx-font-size: 16px; -fx-text-fill: #2ecc71; -fx-padding: 0 8 0 18;");
        Label userLabel = new Label("User: " + (client.getConnection() != null && client.getConnection().getServerInfo() != null ? client.getConnection().getServerInfo().getUsername() : "-") + "  |  Session: " + sessionId);
        userLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #bdc3c7; -fx-padding: 0 0 0 0;");
        // Custom burger menu without arrow
        MenuItem fileTransferMenuItem = new MenuItem("File Transfer");
        fileTransferMenuItem.setOnAction(e -> handleFileTransfer());
        MenuItem manageUsersMenuItem = new MenuItem("Manage Users");
        manageUsersMenuItem.setOnAction(e -> handleManageSSHUsers());
        MenuItem disconnectMenuItem = new MenuItem("Disconnect");
        disconnectMenuItem.setOnAction(e -> handleDisconnect());
        Button burgerButton = new Button("≡");
        burgerButton.setStyle("-fx-background-color: transparent; -fx-font-size: 30px; -fx-font-weight: bold; -fx-text-fill: #fff; -fx-cursor: hand; -fx-padding: 0 0 0 32;");
        burgerButton.setTooltip(new Tooltip("Actions"));
        ContextMenu burgerMenu = new ContextMenu(fileTransferMenuItem, manageUsersMenuItem, disconnectMenuItem);
        burgerButton.setOnAction(e -> {
            burgerMenu.show(burgerButton, javafx.geometry.Side.BOTTOM, 0, 0);
        });
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        headerBar.getChildren().clear();
        headerBar.getChildren().addAll(appName, statusDot, userLabel, spacer, burgerButton);
        headerBar.setSpacing(0);

        // Terminal output area: direct, edge-to-edge
        terminalOutput = new TextArea();
        terminalOutput.setEditable(false);
        terminalOutput.setWrapText(true);
        terminalOutput.getStyleClass().add("ssh-terminal-area");
        terminalOutput.getStylesheets().add(getClass().getResource("/terminal.css").toExternalForm());
        terminalOutput.appendText("SSH Terminal Connected\n");
        terminalOutput.appendText("Type commands to execute on the server.\n\n");

        // Command input area (full width, flat)
        HBox inputBar = new HBox(0);
        inputBar.setPadding(new Insets(0));
        inputBar.setStyle("-fx-background-color: #23272e; -fx-background-radius: 0;");
        commandInput = new ShellInputField();
        commandInput.setStyle("-fx-control-inner-background: #23272e; -fx-text-fill: #e0e6ed; -fx-font-family: 'JetBrains Mono', 'Fira Mono', 'Consolas', monospace; -fx-font-size: 15px; -fx-background-radius: 0; -fx-border-radius: 0; -fx-border-width: 1px; -fx-border-color: #22262c; -fx-padding: 16 16 16 16;");
        commandInput.setPrefWidth(0);
        commandInput.setPrompt(formatShellPrompt(currentWorkingDirectory));
        Button sendButton = new Button("▶");
        sendButton.setTooltip(new Tooltip("Send Command (Enter)"));
        sendButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-size: 22px; -fx-background-radius: 0; -fx-cursor: hand; -fx-min-width: 64px; -fx-min-height: 54px; -fx-border-radius: 0;");
        sendButton.setOnAction(e -> sendCommand());
        HBox.setHgrow(commandInput, Priority.ALWAYS);
        inputBar.getChildren().addAll(commandInput, sendButton);

        // Main layout
        BorderPane mainLayout = new BorderPane();
        mainLayout.setTop(headerBar);
        mainLayout.setCenter(terminalOutput);
        mainLayout.setBottom(inputBar);
        mainLayout.setStyle("-fx-background-color: #181c22;");

        // Set up the scene
        javafx.scene.Scene scene = new javafx.scene.Scene(mainLayout, 900, 600);
        primaryStage.setScene(scene);
        primaryStage.setTitle("SSH Client - Terminal");
        primaryStage.setMinWidth(700);
        primaryStage.setMinHeight(500);

        // Keyboard shortcuts
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.isControlDown() && event.getCode() == KeyCode.L) {
                terminalOutput.clear();
                event.consume();
            } else if (event.isControlDown() && event.getCode() == KeyCode.U) {
                handleFileTransfer();
                event.consume();
            } else if (event.isControlDown() && event.getCode() == KeyCode.M) {
                handleManageSSHUsers();
                event.consume();
            } else if (event.isControlDown() && event.getCode() == KeyCode.D) {
                handleDisconnect();
                event.consume();
            } else if (event.getCode() == KeyCode.ENTER && commandInput.isFocused()) {
                sendCommand();
                event.consume();
            }
        });

        // Focus on command input
        Platform.runLater(commandInput::requestFocus);
        primaryStage.show();
        primaryStage.toFront();
    }
    
    private void sendCommand() {
        String command = commandInput.getCommand();
        if (!command.trim().isEmpty()) {
            if (command.trim().equalsIgnoreCase("cls")) {
                terminalOutput.clear();
                commandInput.clearCommand();
                return;
            }
            try {
                client.sendShellCommand(command);
                commandInput.clearCommand();
            } catch (Exception ex) {
                displayError("Failed to send command: " + ex.getMessage());
            }
        }
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
            String prompt = formatShellPrompt(currentWorkingDirectory);
            terminalOutput.appendText(prompt);
            if (output != null && !output.isEmpty()) {
                terminalOutput.appendText(output);
            }
            Platform.runLater(() -> {
                terminalOutput.positionCaret(terminalOutput.getLength());
                terminalOutput.setScrollTop(Double.MAX_VALUE);
            });
        });
    }
    
    public void displayShellCommand(String command, String output) {
        Platform.runLater(() -> {
            String prompt = formatShellPrompt(currentWorkingDirectory);
            String commandLine = prompt + command + "\n";
            terminalOutput.appendText(commandLine);
            if (output != null && !output.isEmpty()) {
                terminalOutput.appendText(output);
            }
            terminalOutput.appendText("\n");
            Platform.runLater(() -> {
                terminalOutput.positionCaret(terminalOutput.getLength());
                terminalOutput.setScrollTop(Double.MAX_VALUE);
            });
        });
    }
    
    public void displayMessage(String message) {
        Platform.runLater(() -> {
            terminalOutput.appendText(message + "\n");
            Platform.runLater(() -> {
                terminalOutput.positionCaret(terminalOutput.getLength());
                terminalOutput.setScrollTop(Double.MAX_VALUE);
            });
        });
    }
    
    public void displayError(String error) {
        Platform.runLater(() -> {
            terminalOutput.appendText("ERROR: " + error + "\n");
            Platform.runLater(() -> {
                terminalOutput.positionCaret(terminalOutput.getLength());
                terminalOutput.setScrollTop(Double.MAX_VALUE);
            });
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
            Platform.runLater(() -> {
                terminalOutput.positionCaret(terminalOutput.getLength());
                terminalOutput.setScrollTop(Double.MAX_VALUE);
            });
        });
    }
} 