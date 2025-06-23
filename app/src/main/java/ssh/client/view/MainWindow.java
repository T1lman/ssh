package ssh.client.view;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.scene.control.ScrollPane;
import java.util.function.Consumer;
import java.io.File;
import ssh.model.utils.Logger;

/**
 * Handles the main SSH terminal interface.
 * Manages the terminal output, command input, and action buttons.
 */
public class MainWindow {
    private final Stage primaryStage;
    
    // UI Components - Using TextFlow for styled text display
    private ScrollPane terminalScrollPane;
    private TextFlow terminalOutput;
    private ShellInputField commandInput;
    private String currentWorkingDirectory = "~";
    
    // Action buttons
    private Button fileTransferButton;
    private Button manageUsersButton;
    private Button disconnectButton;
    
    // Dialogs
    private FileTransferDialog fileTransferDialog;
    private UserManagementDialog userManagementDialog;
    
    // Event/callback fields
    private Consumer<String> onCommandEntered;
    private Runnable onFileTransfer;
    private Runnable onDisconnect;
    private java.util.function.Supplier<String> workingDirectoryProvider;
    
    // Add fields for file transfer and user management consumers
    private Consumer<File> onFileUploadRequested;
    private Consumer<String> onFileDownloadRequested;
    private Consumer<String> onUserCreateRequested;
    private Consumer<String> onUserDeleteRequested;
    private Consumer<String> onUserViewRequested;
    
    private Label userLabel;
    private HBox headerBar;
    private String currentUsername = "-";
    private String currentHost = "-";
    private int currentPort = -1;
    private String currentSessionId = "-";
    
    public MainWindow(Stage primaryStage) {
        this.primaryStage = primaryStage;
        
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::createUI);
        } else {
            createUI();
        }
    }
    
    private void createUI() {
        Logger.debug("Creating main window on FX thread");
        
        // Header bar
        headerBar = new HBox();
        headerBar.setPadding(new Insets(0, 32, 0, 32));
        headerBar.setStyle("-fx-background-color: linear-gradient(to right, #232526, #414345); -fx-min-height: 54px; -fx-max-height: 54px;");
        headerBar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Label appName = new Label("SSH Client");
        appName.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #ecf0f1;");
        // Green status dot
        Label statusDot = new Label("●");
        statusDot.setStyle("-fx-font-size: 16px; -fx-text-fill: #2ecc71; -fx-padding: 0 8 0 18;");
        userLabel = new Label();
        userLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #bdc3c7; -fx-padding: 0 0 0 0;");
        updateHeader(currentUsername, currentHost, currentPort, currentSessionId);
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

        // Terminal output area: Using TextFlow for styled text display
        terminalOutput = new TextFlow();
        terminalOutput.setStyle("-fx-background-color: #181c22; -fx-padding: 8px;");
        terminalOutput.setLineSpacing(2);
        
        // Wrap TextFlow in ScrollPane
        terminalScrollPane = new ScrollPane(terminalOutput);
        terminalScrollPane.setFitToWidth(true);
        terminalScrollPane.setFitToHeight(true);
        terminalScrollPane.setStyle("-fx-background-color: #181c22; -fx-background: #181c22;");
        terminalScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        terminalScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        
        // Add initial content
        addStyledText("SSH Terminal Connected\n", "info");
        addStyledText("Type commands to execute on the server.\n\n", "info");
        
        // Add context menu for copy
        ContextMenu contextMenu = new ContextMenu();
        MenuItem copyItem = new MenuItem("Copy");
        copyItem.setOnAction(e -> {
            // Copy all text from TextFlow (simplified approach)
            StringBuilder allText = new StringBuilder();
            for (javafx.scene.Node node : terminalOutput.getChildren()) {
                if (node instanceof Text) {
                    allText.append(((Text) node).getText());
                }
            }
            if (allText.length() > 0) {
                javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
                javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
                content.putString(allText.toString());
                clipboard.setContent(content);
            }
        });
        MenuItem clearItem = new MenuItem("Clear Terminal");
        clearItem.setOnAction(e -> clearTerminal());
        contextMenu.getItems().addAll(copyItem, clearItem);
        terminalScrollPane.setContextMenu(contextMenu);

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
        mainLayout.setCenter(terminalScrollPane);
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
                clearTerminal();
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
    
    private void addStyledText(String text, String style) {
        Platform.runLater(() -> {
            Text styledText = new Text(text);
            styledText.setStyle("-fx-font-family: 'JetBrains Mono', 'Fira Mono', 'Consolas', monospace; -fx-font-size: 15px;");
            
            switch (style) {
                case "command":
                    styledText.setStyle(styledText.getStyle() + " -fx-fill: #3498db; -fx-font-weight: bold;");
                    break;
                case "output":
                    styledText.setStyle(styledText.getStyle() + " -fx-fill: #e0e6ed;");
                    break;
                case "error":
                    styledText.setStyle(styledText.getStyle() + " -fx-fill: #e74c3c;");
                    break;
                case "success":
                    styledText.setStyle(styledText.getStyle() + " -fx-fill: #2ecc71;");
                    break;
                case "info":
                    styledText.setStyle(styledText.getStyle() + " -fx-fill: #f39c12;");
                    break;
                case "prompt":
                    styledText.setStyle(styledText.getStyle() + " -fx-fill: #95a5a6;");
                    break;
                default:
                    styledText.setStyle(styledText.getStyle() + " -fx-fill: #e0e6ed;");
            }
            
            terminalOutput.getChildren().add(styledText);
            scrollToBottom();
        });
    }
    
    private void clearTerminal() {
        Platform.runLater(() -> {
            terminalOutput.getChildren().clear();
            addStyledText("Terminal cleared.\n", "info");
        });
    }
    
    private void scrollToBottom() {
        Platform.runLater(() -> {
            terminalScrollPane.setVvalue(1.0);
        });
    }
    
    private void sendCommand() {
        String command = commandInput.getCommand();
        if (!command.trim().isEmpty()) {
            if (command.trim().equalsIgnoreCase("cls")) {
                clearTerminal();
                commandInput.clearCommand();
                return;
            }
            try {
                onCommandEntered.accept(command);
                commandInput.clearCommand();
            } catch (Exception ex) {
                displayError("Failed to send command: " + ex.getMessage());
            }
        }
    }
    
    private void handleFileTransfer() {
        if (fileTransferDialog == null) {
            fileTransferDialog = new FileTransferDialog(primaryStage, onFileUploadRequested, onFileDownloadRequested);
        }
        fileTransferDialog.show();
    }
    
    private void handleManageSSHUsers() {
        if (userManagementDialog == null) {
            userManagementDialog = new UserManagementDialog(primaryStage, onUserCreateRequested, onUserDeleteRequested, onUserViewRequested);
        }
        userManagementDialog.show();
    }
    
    private void handleDisconnect() {
        if (onDisconnect != null) {
            onDisconnect.run();
        }
        // Force close the entire application
        Platform.exit();
        System.exit(0);
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
            if (output != null && !output.isEmpty()) {
                addStyledText(output + "\n", "output");
            }
            scrollToBottom();
        });
    }
    
    public void displayShellCommand(String command, String output) {
        Platform.runLater(() -> {
            String prompt = formatShellPrompt(currentWorkingDirectory);
            // Add prompt in gray color
            addStyledText(prompt, "prompt");
            // Add command in blue color
            addStyledText(command + "\n", "command");
            if (output != null && !output.isEmpty()) {
                // Add output in white color
                addStyledText(output.trim() + "\n", "output");
            }
        });
    }
    
    public void displayMessage(String message) {
        Platform.runLater(() -> {
            addStyledText(message + "\n", "info");
        });
    }
    
    public void displayError(String error) {
        Platform.runLater(() -> {
            addStyledText(error + "\n", "error");
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
                addStyledText(message + "\n", "success");
            } else {
                addStyledText(message + "\n", "error");
            }
            scrollToBottom();
        });
    }

    public void setOnCommandEntered(Consumer<String> onCommandEntered) {
        this.onCommandEntered = command -> {
            // Capture the working directory BEFORE processing the command
            String currentDir = workingDirectoryProvider != null ? workingDirectoryProvider.get() : currentWorkingDirectory;
            String prompt = formatShellPrompt(currentDir);
            // Display prompt and command before sending
            Platform.runLater(() -> {
                // Add prompt in gray color
                addStyledText(prompt, "prompt");
                // Add command in blue color
                addStyledText(command + "\n", "command");
            });
            onCommandEntered.accept(command);
        };
    }

    public void setWorkingDirectoryProvider(java.util.function.Supplier<String> workingDirectoryProvider) {
        this.workingDirectoryProvider = workingDirectoryProvider;
    }

    public void updateHeader(String username, String host, int port, String sessionId) {
        this.currentUsername = username;
        this.currentHost = host;
        this.currentPort = port;
        this.currentSessionId = sessionId;
        Platform.runLater(() -> {
            String label = String.format("Server: %s:%s  |  User: %s  |  Session: %s",
                host != null && !host.isEmpty() ? host : "-",
                port > 0 ? port : "-",
                username != null && !username.isEmpty() ? username : "-",
                sessionId != null && !sessionId.isEmpty() ? sessionId : "-");
            userLabel.setText(label);
        });
    }

    public void setOnFileUploadRequested(Consumer<File> onFileUploadRequested) {
        this.onFileUploadRequested = onFileUploadRequested;
    }
    public void setOnFileDownloadRequested(Consumer<String> onFileDownloadRequested) {
        this.onFileDownloadRequested = onFileDownloadRequested;
    }
} 