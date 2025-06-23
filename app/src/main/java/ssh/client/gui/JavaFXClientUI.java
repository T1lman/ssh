package ssh.client.gui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.KeyCode;
import ssh.client.SSHClient;
import ssh.client.ui.AuthCredentials;
import ssh.client.ui.ClientUI;
import ssh.client.ui.ServerInfo;
import ssh.utils.CredentialsManager;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;

public class JavaFXClientUI implements ClientUI {

    private Stage primaryStage;
    private TextArea terminalOutput;
    private ShellInputField commandInput;
    private SSHClient client;
    private String currentWorkingDirectory = "~";
    
    // Progress tracking for file transfers
    private javafx.scene.control.ProgressBar activeProgressBar;
    private Label activeStatusLabel;
    private Dialog<Void> reusableDialog;
    private VBox reusableContent;

    // Startup scene references
    private javafx.scene.control.Label startupStatusLabel;
    private javafx.scene.control.ProgressIndicator startupProgressIndicator;
    private VBox startupConnectionForm;
    private VBox startupUsernameForm;
    private TextField startupHostField;
    private TextField startupPortField;
    private javafx.scene.control.Button startupConnectButton;
    private javafx.scene.control.Button startupCancelButton;
    private ComboBox<String> startupUsernameComboBox;
    private javafx.scene.control.Button startupLoginButton;
    private ServerInfo pendingServerInfo;
    private String[] availableUsers;

    /**
     * Custom input field that behaves like a shell prompt
     */
    private static class ShellInputField extends TextField {
        private String prompt;
        private int promptLength;
        
        public ShellInputField() {
            super();
            setupShellBehavior();
        }
        
        private void setupShellBehavior() {
            // Handle key events to maintain shell-like behavior
            this.setOnKeyPressed(this::handleKeyPress);
            this.setOnKeyTyped(this::handleKeyTyped);
        }
        
        private void handleKeyPress(KeyEvent event) {
            if (event.getCode() == KeyCode.ENTER) {
                // Don't handle ENTER here, let the parent handle it
                return;
            }
            
            if (event.getCode() == KeyCode.BACK_SPACE) {
                // Prevent backspace from deleting the prompt
                if (getCaretPosition() <= promptLength) {
                    event.consume();
                }
            }
            
            if (event.getCode() == KeyCode.DELETE) {
                // Prevent delete from affecting the prompt
                if (getCaretPosition() < promptLength) {
                    event.consume();
                }
            }
            
            if (event.getCode() == KeyCode.LEFT) {
                // Prevent moving cursor into the prompt area
                if (getCaretPosition() <= promptLength) {
                    event.consume();
                }
            }
            
            if (event.getCode() == KeyCode.HOME) {
                // Move to start of command (after prompt)
                event.consume();
                positionCaret(promptLength);
            }
            
            // Handle Ctrl+A to select all text (but not the prompt)
            if (event.isControlDown() && event.getCode() == KeyCode.A) {
                event.consume();
                selectRange(promptLength, getText().length());
            }
        }
        
        private void handleKeyTyped(KeyEvent event) {
            // Prevent typing into the prompt area
            if (getCaretPosition() < promptLength) {
                event.consume();
            }
        }
        
        public void setPrompt(String prompt) {
            this.prompt = prompt;
            this.promptLength = prompt.length();
            updateDisplay();
        }
        
        public String getCommand() {
            String fullText = getText();
            if (fullText.length() > promptLength) {
                return fullText.substring(promptLength);
            }
            return "";
        }
        
        public void clearCommand() {
            setText(prompt);
            positionCaret(promptLength);
        }
        
        private void updateDisplay() {
            String currentCommand = getCommand();
            setText(prompt + currentCommand);
            positionCaret(promptLength + currentCommand.length());
        }
        
        @Override
        public void replaceText(int start, int end, String text) {
            // Prevent replacing text in the prompt area
            if (start < promptLength) {
                start = promptLength;
            }
            if (end < promptLength) {
                end = promptLength;
            }
            super.replaceText(start, end, text);
        }
        
        @Override
        public void replaceSelection(String replacement) {
            // Prevent replacing selection if it includes the prompt
            if (getCaretPosition() < promptLength) {
                return;
            }
            super.replaceSelection(replacement);
        }
    }

    public JavaFXClientUI(Stage primaryStage) {
        this.primaryStage = primaryStage;
        this.primaryStage.setTitle("SSH Client");
        
        // Initialize with animated startup scene
        createStartupScene();
        
        // Show the stage immediately with the startup scene
        this.primaryStage.show();
    }
    
    private void createStartupScene() {
        VBox root = new VBox(20);
        root.setPadding(new Insets(30));
        root.setStyle("-fx-background-color: #2c3e50; -fx-alignment: center;");
        
        // Title with nice styling
        javafx.scene.control.Label titleLabel = new javafx.scene.control.Label("SSH Client");
        titleLabel.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: #ecf0f1;");
        
        // Subtitle
        javafx.scene.control.Label subtitleLabel = new javafx.scene.control.Label("Secure Shell Connection");
        subtitleLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #bdc3c7; -fx-font-style: italic;");
        
        // Progress indicator for animation
        javafx.scene.control.ProgressIndicator progressIndicator = new javafx.scene.control.ProgressIndicator();
        progressIndicator.setStyle("-fx-progress-color: #3498db;");
        progressIndicator.setPrefSize(40, 40);
        
        // Status label that will be updated
        javafx.scene.control.Label statusLabel = new javafx.scene.control.Label("Initializing...");
        statusLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #bdc3c7;");
        
        // Connection form (initially hidden)
        VBox connectionForm = new VBox(15);
        connectionForm.setVisible(false);
        connectionForm.setManaged(false);
        
        javafx.scene.control.Label formTitle = new javafx.scene.control.Label("Server Connection");
        formTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #ecf0f1;");
        
        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(10);
        grid.setAlignment(javafx.geometry.Pos.CENTER);
        
        TextField hostField = new TextField();
        hostField.setPromptText("localhost");
        hostField.setPrefWidth(200);
        hostField.setStyle("-fx-background-color: #34495e; -fx-text-fill: #ecf0f1; -fx-border-color: #3498db; -fx-border-radius: 3px;");
        
        TextField portField = new TextField();
        portField.setPromptText("2222");
        portField.setPrefWidth(200);
        portField.setStyle("-fx-background-color: #34495e; -fx-text-fill: #ecf0f1; -fx-border-color: #3498db; -fx-border-radius: 3px;");
        
        javafx.scene.control.Label hostLabel = new javafx.scene.control.Label("Host:");
        hostLabel.setStyle("-fx-text-fill: #ecf0f1; -fx-font-weight: bold;");
        
        javafx.scene.control.Label portLabel = new javafx.scene.control.Label("Port:");
        portLabel.setStyle("-fx-text-fill: #ecf0f1; -fx-font-weight: bold;");
        
        grid.add(hostLabel, 0, 0);
        grid.add(hostField, 1, 0);
        grid.add(portLabel, 0, 1);
        grid.add(portField, 1, 1);
        
        // Connect button
        javafx.scene.control.Button connectButton = new javafx.scene.control.Button("Connect");
        connectButton.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 5px; -fx-padding: 10 20;");
        connectButton.setPrefWidth(120);
        
        // Cancel button
        javafx.scene.control.Button cancelButton = new javafx.scene.control.Button("Cancel");
        cancelButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 5px; -fx-padding: 10 20;");
        cancelButton.setPrefWidth(120);
        
        javafx.scene.layout.HBox buttonBox = new javafx.scene.layout.HBox(15);
        buttonBox.setAlignment(javafx.geometry.Pos.CENTER);
        buttonBox.getChildren().addAll(cancelButton, connectButton);
        
        connectionForm.getChildren().addAll(formTitle, grid, buttonBox);
        
        // Username selection form (initially hidden)
        VBox usernameForm = new VBox(15);
        usernameForm.setVisible(false);
        usernameForm.setManaged(false);
        usernameForm.setAlignment(javafx.geometry.Pos.CENTER);
        
        ComboBox<String> usernameComboBox = new ComboBox<>();
        usernameComboBox.setPrefWidth(200);
        usernameComboBox.setStyle("-fx-background-color: #34495e; -fx-text-fill: #ecf0f1; -fx-border-color: #3498db; -fx-border-radius: 3px;");
        
        // Back button for username form
        javafx.scene.control.Button backButton = new javafx.scene.control.Button("Back");
        backButton.setStyle("-fx-background-color: #95a5a6; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 5px; -fx-padding: 10 20;");
        backButton.setPrefWidth(120);
        
        // Login button
        javafx.scene.control.Button loginButton = new javafx.scene.control.Button("Login");
        loginButton.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 5px; -fx-padding: 10 20;");
        loginButton.setPrefWidth(120);
        
        javafx.scene.layout.HBox usernameButtonBox = new javafx.scene.layout.HBox(15);
        usernameButtonBox.setAlignment(javafx.geometry.Pos.CENTER);
        usernameButtonBox.getChildren().addAll(backButton, loginButton);
        
        usernameForm.getChildren().addAll(usernameComboBox, usernameButtonBox);
        
        // Add all elements to root
        root.getChildren().addAll(titleLabel, subtitleLabel, progressIndicator, statusLabel, connectionForm, usernameForm);
        
        // Set up the scene
        this.primaryStage.setScene(new javafx.scene.Scene(root, 450, 400));
        this.primaryStage.setMinWidth(450);
        this.primaryStage.setMinHeight(400);
        this.primaryStage.setResizable(false);
        
        // Store references for later use
        this.startupStatusLabel = statusLabel;
        this.startupProgressIndicator = progressIndicator;
        this.startupConnectionForm = connectionForm;
        this.startupUsernameForm = usernameForm;
        this.startupHostField = hostField;
        this.startupPortField = portField;
        this.startupConnectButton = connectButton;
        this.startupCancelButton = cancelButton;
        this.startupUsernameComboBox = usernameComboBox;
        this.startupLoginButton = loginButton;
        
        // Set up button actions
        connectButton.setOnAction(e -> handleConnect());
        cancelButton.setOnAction(e -> Platform.exit());
        loginButton.setOnAction(e -> handleLogin());
        backButton.setOnAction(e -> showConnectionForm());
        
        // Start the animation sequence
        startStartupAnimation();
    }
    
    private void startStartupAnimation() {
        // Animate the progress indicator and status messages
        Timeline timeline = new Timeline();
        
        timeline.getKeyFrames().addAll(
            new KeyFrame(Duration.seconds(0), e -> {
                startupStatusLabel.setText("Initializing SSH Client...");
                startupProgressIndicator.setProgress(0.1);
            }),
            new KeyFrame(Duration.seconds(1), e -> {
                startupStatusLabel.setText("Loading configuration...");
                startupProgressIndicator.setProgress(0.3);
            }),
            new KeyFrame(Duration.seconds(2), e -> {
                startupStatusLabel.setText("Preparing connection...");
                startupProgressIndicator.setProgress(0.6);
            }),
            new KeyFrame(Duration.seconds(3), e -> {
                startupStatusLabel.setText("Ready to connect");
                startupProgressIndicator.setProgress(1.0);
            }),
            new KeyFrame(Duration.seconds(3.5), e -> {
                // Show connection form
                startupConnectionForm.setVisible(true);
                startupConnectionForm.setManaged(true);
                startupStatusLabel.setText("Enter server details to connect");
                startupProgressIndicator.setVisible(false);
                startupProgressIndicator.setManaged(false);
                
                // Focus on host field
                Platform.runLater(startupHostField::requestFocus);
            })
        );
        
        timeline.play();
    }
    
    private void handleConnect() {
        String host = startupHostField.getText().isEmpty() ? "localhost" : startupHostField.getText();
        int port;
        
        try {
            port = startupPortField.getText().isEmpty() ? 2222 : Integer.parseInt(startupPortField.getText());
        } catch (NumberFormatException e) {
            showError("Invalid port number. Please enter a valid number.");
            return;
        }
        
        // Create ServerInfo and store it for later use
        this.pendingServerInfo = new ServerInfo(host, port, null);
        
        // Show username selection form
        showUsernameForm();
    }
    
    private void showUsernameForm() {
        // Load available users
        try {
            CredentialsManager credentialsManager = new CredentialsManager("config/credentials.properties");
            this.availableUsers = credentialsManager.getAvailableUsers();
            
            // Populate username combo box
            startupUsernameComboBox.getItems().clear();
            for (String user : availableUsers) {
                startupUsernameComboBox.getItems().add(user);
            }
            if (availableUsers.length > 0) {
                startupUsernameComboBox.setValue(availableUsers[0]);
            }
            
            // Hide connection form and show username form
            startupConnectionForm.setVisible(false);
            startupConnectionForm.setManaged(false);
            startupUsernameForm.setVisible(true);
            startupUsernameForm.setManaged(true);
            
            startupStatusLabel.setText("Select your username to connect");
            
            // Focus on username combo box
            Platform.runLater(startupUsernameComboBox::requestFocus);
            
        } catch (Exception e) {
            showError("Failed to load user credentials: " + e.getMessage());
        }
    }
    
    private void showConnectionForm() {
        // Hide username form and show connection form
        startupUsernameForm.setVisible(false);
        startupUsernameForm.setManaged(false);
        startupConnectionForm.setVisible(true);
        startupConnectionForm.setManaged(true);
        
        startupStatusLabel.setText("Enter server details to connect");
        
        // Focus on host field
        Platform.runLater(startupHostField::requestFocus);
    }
    
    private void handleLogin() {
        String selectedUser = startupUsernameComboBox.getValue();
        if (selectedUser == null || selectedUser.trim().isEmpty()) {
            showError("Please select a username.");
            return;
        }
        
        // Update UI to show connecting state
        startupStatusLabel.setText("Connecting to " + pendingServerInfo.getHost() + ":" + pendingServerInfo.getPort() + " as " + selectedUser + "...");
        startupUsernameForm.setVisible(false);
        startupUsernameForm.setManaged(false);
        startupProgressIndicator.setVisible(true);
        startupProgressIndicator.setManaged(true);
        startupProgressIndicator.setProgress(-1); // Indeterminate progress
        
        // Set the username in the pending server info
        pendingServerInfo.setUsername(selectedUser);
        
        // Start the connection process in a background thread
        new Thread(() -> {
            try {
                client.startConnection();
            } catch (Exception e) {
                Platform.runLater(() -> {
                    showError("Connection failed: " + e.getMessage());
                    // Reset to username form on error
                    startupProgressIndicator.setVisible(false);
                    startupProgressIndicator.setManaged(false);
                    startupUsernameForm.setVisible(true);
                    startupUsernameForm.setManaged(true);
                });
            }
        }).start();
    }

    public void setClient(SSHClient client) {
        this.client = client;
        
        // Initialize working directory if client has a connection
        if (client != null) {
            // Initialize with the user's home directory
            String homeDir = System.getProperty("user.home");
            updateWorkingDirectory(homeDir);
        }
    }

    public void showMainWindow() {
        System.out.println("DEBUG: showMainWindow() called");
        VBox root = new VBox(10);
        root.setPadding(new Insets(15));
        root.setStyle("-fx-background-color: #2c3e50;");

        // Create a header
        javafx.scene.control.Label headerLabel = new javafx.scene.control.Label("SSH Client Terminal");
        headerLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #ecf0f1;");
        
        // Create status indicator
        javafx.scene.control.Label statusLabel = new javafx.scene.control.Label("Connected to " + (client != null ? "server" : "unknown"));
        statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #2ecc71;");

        // Create button bar
        javafx.scene.layout.HBox buttonBar = new javafx.scene.layout.HBox(10);
        buttonBar.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        
        javafx.scene.control.Button createUserButton = new javafx.scene.control.Button("Manage SSH Users");
        createUserButton.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 5px;");
        createUserButton.setOnAction(e -> handleManageSSHUsers());
        
        javafx.scene.control.Button fileTransferButton = new javafx.scene.control.Button("File Transfer");
        fileTransferButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 5px;");
        fileTransferButton.setOnAction(e -> handleFileTransfer());
        
        javafx.scene.control.Button disconnectButton = new javafx.scene.control.Button("Disconnect");
        disconnectButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 5px;");
        disconnectButton.setOnAction(e -> handleDisconnect());
        
        buttonBar.getChildren().addAll(createUserButton, fileTransferButton, disconnectButton);

        terminalOutput = new TextArea();
        terminalOutput.setEditable(false);
        terminalOutput.setWrapText(true);
        terminalOutput.setPrefHeight(400);
        terminalOutput.setStyle("-fx-font-family: 'Monaco', 'Menlo', 'Consolas', monospace; -fx-font-size: 12px; -fx-background-color: #1a1a1a; -fx-text-fill: #00ff00; -fx-control-inner-background: #1a1a1a;");
        terminalOutput.setPromptText("Terminal output will appear here...");

        commandInput = new ShellInputField();
        commandInput.setStyle("-fx-font-family: 'Monaco', 'Menlo', 'Consolas', monospace; -fx-font-size: 12px; -fx-background-color: #34495e; -fx-text-fill: #ecf0f1; -fx-border-color: #3498db; -fx-border-radius: 3px;");
        commandInput.setPrompt(formatShellPrompt(currentWorkingDirectory));
        commandInput.setOnAction(e -> {
            String command = commandInput.getCommand();
            if (client != null && !command.trim().isEmpty()) {
                // Display the command in the terminal output
                String prompt = formatShellPrompt(currentWorkingDirectory);
                displayShellOutput(prompt + command);
                
                // Send the command to the server
                client.sendShellCommand(command);
                commandInput.clearCommand();
            }
        });

        // Create a simple container for the command input
        javafx.scene.layout.HBox commandBox = new javafx.scene.layout.HBox(5);
        commandBox.getChildren().addAll(commandInput);
        javafx.scene.layout.HBox.setHgrow(commandInput, javafx.scene.layout.Priority.ALWAYS);

        root.getChildren().addAll(headerLabel, statusLabel, buttonBar, terminalOutput, commandBox);

        System.out.println("DEBUG: Created UI components, setting up scene");
        
        // Create a new scene if one doesn't exist
        if (primaryStage.getScene() == null) {
            System.out.println("DEBUG: Creating new scene");
            primaryStage.setScene(new javafx.scene.Scene(root, 700, 600));
        } else {
            System.out.println("DEBUG: Updating existing scene");
            primaryStage.getScene().setRoot(root);
        }
        
        // Ensure the stage is configured properly
        primaryStage.setTitle("SSH Client - Connected");
        primaryStage.setMinWidth(600);
        primaryStage.setMinHeight(500);
        
        System.out.println("DEBUG: Stage configured, checking visibility");
        
        // Show the stage if it's not already visible
        if (!primaryStage.isShowing()) {
            System.out.println("DEBUG: Stage not showing, calling show()");
            primaryStage.show();
        } else {
            System.out.println("DEBUG: Stage is already showing");
        }
        
        // Request focus on the command input
        Platform.runLater(() -> {
            System.out.println("DEBUG: Requesting focus on command input");
            commandInput.requestFocus();
        });
        
        System.out.println("DEBUG: showMainWindow() completed");
    }

    /**
     * Update the working directory display
     */
    public void updateWorkingDirectory(String newWorkingDirectory) {
        this.currentWorkingDirectory = newWorkingDirectory;
        if (commandInput != null) {
            Platform.runLater(() -> {
                // Update the prompt to show the new working directory
                commandInput.setPrompt(formatShellPrompt(newWorkingDirectory));
            });
        }
    }
    
    /**
     * Format the shell prompt with working directory
     */
    private String formatShellPrompt(String workingDirectory) {
        String displayPath = formatWorkingDirectory(workingDirectory);
        return displayPath + " $ ";
    }

    /**
     * Format the working directory for display
     */
    private String formatWorkingDirectory(String path) {
        if (path == null || path.isEmpty()) {
            return "~";
        }
        
        // Replace home directory with ~
        String homeDir = System.getProperty("user.home");
        if (path.equals(homeDir) || path.startsWith(homeDir + "/")) {
            if (path.equals(homeDir)) {
                return "~";
            } else {
                return "~" + path.substring(homeDir.length());
            }
        }
        
        // For other paths, show the full path but limit length
        if (path.length() > 30) {
            // Truncate long paths
            return "..." + path.substring(path.length() - 27);
        }
        
        return path;
    }

    @Override
    public ServerInfo getServerInfoFromUser() {
        // Return the pending server info that was set during the startup process
        if (pendingServerInfo != null) {
            ServerInfo info = pendingServerInfo;
            pendingServerInfo = null; // Clear it after use
            return info;
        }
        
        // Fallback: if somehow we don't have pending info, return default
        return new ServerInfo("localhost", 2222, null);
    }

    @Override
    public void displayMessage(String message) {
        // Show message in a dialog or append to log area
        System.out.println("GUI_INFO: " + message);
        Platform.runLater(() -> {
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
            alert.setTitle("SSH Client");
            alert.setHeaderText("Information");
            alert.setContentText(message);
            // Only set owner if stage has a scene
            if (primaryStage != null && primaryStage.getScene() != null) {
                alert.initOwner(primaryStage);
            }
            alert.showAndWait();
        });
    }

    @Override
    public void displayError(String error) {
        // Show an error dialog
        System.out.println("GUI_ERROR: " + error);
        Platform.runLater(() -> {
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
            alert.setTitle("SSH Client Error");
            alert.setHeaderText("An error occurred");
            alert.setContentText(error);
            // Only set owner if stage has a scene
            if (primaryStage != null && primaryStage.getScene() != null) {
                alert.initOwner(primaryStage);
            }
            alert.showAndWait();
        });
    }

    @Override
    public String getInput(String prompt) {
        // Would show an input dialog
        return "placeholder_input";
    }

    @Override
    public String getPassword(String prompt) {
        // Would show a password dialog
        return "placeholder_password";
    }

    @Override
    public void showConnectionStatus(boolean connected) {
        // Would update a status indicator in the GUI
        System.out.println("GUI_STATUS: Connected=" + connected);
    }

    @Override
    public void showAuthenticationResult(boolean success, String message) {
        System.out.println("GUI_AUTH: Success=" + success + ", " + message);
        if (success) {
            System.out.println("DEBUG: Authentication successful, showing main window");
            
            // Use Platform.runLater but ensure it executes
            if (Platform.isFxApplicationThread()) {
                System.out.println("DEBUG: Already on FX thread, showing window directly");
                showMainWindowDirectly();
            } else {
                System.out.println("DEBUG: Not on FX thread, using Platform.runLater");
                Platform.runLater(this::showMainWindowDirectly);
            }
        } else {
            // Show error but don't close the application
            Platform.runLater(() -> {
                displayError("Authentication failed: " + message);
                // Optionally show the authentication dialog again or provide options
            });
        }
    }
    
    private void showMainWindowDirectly() {
        try {
            System.out.println("DEBUG: Creating main window on FX thread");
            showMainWindow();
            System.out.println("DEBUG: Main window created, ensuring stage is visible");
            
            // Ensure the stage is visible and bring to front
            if (primaryStage != null) {
                if (!primaryStage.isShowing()) {
                    System.out.println("DEBUG: Stage not showing, calling show()");
                    primaryStage.show();
                } else {
                    System.out.println("DEBUG: Stage is already showing");
                }
                // Bring to front
                primaryStage.toFront();
                primaryStage.requestFocus();
            } else {
                System.out.println("DEBUG: ERROR - primaryStage is null!");
            }
            System.out.println("DEBUG: Main window setup complete");
        } catch (Exception e) {
            System.out.println("DEBUG: Error showing main window: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void displayShellOutput(String output) {
        if (terminalOutput != null) {
            Platform.runLater(() -> terminalOutput.appendText(output + "\n"));
        } else {
            // Fallback if UI not ready
            System.out.println("GUI_SHELL_OUTPUT: " + output);
        }
    }

    @Override
    public void showFileTransferProgress(String filename, int percentage) {
        // Update progress bar if we have an active progress dialog
        Platform.runLater(() -> {
            System.out.println("GUI_FTP: " + filename + " - " + percentage + "%");
            
            if (activeProgressBar != null) {
                activeProgressBar.setProgress(percentage / 100.0);
            }
            
            if (activeStatusLabel != null) {
                activeStatusLabel.setText("Transferring: " + percentage + "%");
            }
        });
    }

    @Override
    public String selectService() {
        // Would use buttons or a menu
        return "shell";
    }

    @Override
    public ServerInfo getServerInfo() {
        // Should not be called in the GUI flow
        return null;
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
            if (startupStatusLabel != null) {
                startupStatusLabel.setText(step);
            }
            System.out.println("GUI_PROGRESS: " + step);
        });
    }

    @Override
    public boolean shouldContinue() {
        // The GUI's lifecycle is managed by the user closing the window
        return true;
    }

    private void handleFileTransfer() {
        System.out.println("DEBUG: File Transfer button clicked");
        
        // Initialize reusable dialog if not already done
        if (reusableDialog == null) {
            initializeReusableDialog();
        }
        
        // Update dialog for file transfer selection
        updateDialogForFileTransferSelection();
        reusableDialog.showAndWait();
    }
    
    private void initializeReusableDialog() {
        reusableDialog = new Dialog<>();
        reusableDialog.setTitle("File Transfer");
        
        // Remove default buttons - we'll add them dynamically
        reusableDialog.getDialogPane().getButtonTypes().clear();
        
        reusableContent = new VBox(15);
        reusableContent.setPadding(new Insets(20));
        
        reusableDialog.getDialogPane().setContent(reusableContent);
        reusableDialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        if (primaryStage != null && primaryStage.getScene() != null) {
            reusableDialog.initOwner(primaryStage);
        }
        reusableDialog.setResizable(false);
        reusableDialog.getDialogPane().setPrefWidth(450);
        reusableDialog.getDialogPane().setPrefHeight(200);
    }
    
    private void updateDialogForFileTransferSelection() {
        reusableDialog.setHeaderText("Choose a file transfer operation");
        
        // Clear existing content
        reusableContent.getChildren().clear();
        
        // Clear existing buttons
        reusableDialog.getDialogPane().getButtonTypes().clear();
        
        ButtonType uploadButtonType = new ButtonType("Upload File", ButtonData.LEFT);
        ButtonType downloadButtonType = new ButtonType("Download File", ButtonData.OTHER);
        ButtonType cancelButtonType = new ButtonType("Cancel", ButtonData.CANCEL_CLOSE);
        
        reusableDialog.getDialogPane().getButtonTypes().addAll(
            uploadButtonType, downloadButtonType, cancelButtonType
        );
        
        Label infoLabel = new Label("Select a file transfer operation:");
        infoLabel.setWrapText(true);
        infoLabel.setStyle("-fx-font-size: 12px;");
        
        reusableContent.getChildren().add(infoLabel);
        
        reusableDialog.setResultConverter(dialogButton -> {
            if (dialogButton == uploadButtonType) {
                handleFileUpload();
                return null;
            } else if (dialogButton == downloadButtonType) {
                handleFileDownload();
                return null;
            } else if (dialogButton == cancelButtonType) {
                return null;
            }
            return null;
        });
    }
    
    private void handleFileUpload() {
        System.out.println("DEBUG: File Upload selected");
        
        // Create file chooser for local file selection
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Select File to Upload");
        fileChooser.getExtensionFilters().addAll(
            new javafx.stage.FileChooser.ExtensionFilter("All Files", "*.*"),
            new javafx.stage.FileChooser.ExtensionFilter("Text Files", "*.txt", "*.log", "*.md"),
            new javafx.stage.FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif"),
            new javafx.stage.FileChooser.ExtensionFilter("Document Files", "*.pdf", "*.doc", "*.docx")
        );
        
        java.io.File selectedFile = fileChooser.showOpenDialog(primaryStage);
        if (selectedFile == null) {
            return; // User cancelled
        }
        
        // Update dialog for remote path input
        updateDialogForUploadPath(selectedFile);
        reusableDialog.showAndWait();
    }
    
    private void updateDialogForUploadPath(java.io.File selectedFile) {
        reusableDialog.setHeaderText("Upload: " + selectedFile.getName());
        
        // Clear existing content
        reusableContent.getChildren().clear();
        
        // Clear existing buttons
        reusableDialog.getDialogPane().getButtonTypes().clear();
        
        ButtonType uploadButtonType = new ButtonType("Upload", ButtonData.OK_DONE);
        ButtonType backButtonType = new ButtonType("Back", ButtonData.OTHER);
        ButtonType cancelButtonType = new ButtonType("Cancel", ButtonData.CANCEL_CLOSE);
        
        reusableDialog.getDialogPane().getButtonTypes().addAll(
            uploadButtonType, backButtonType, cancelButtonType
        );
        
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));
        
        TextField remotePathField = new TextField();
        remotePathField.setPromptText("Remote filename (optional, defaults to original name)");
        remotePathField.setText(selectedFile.getName());
        
        Label infoLabel = new Label("File: " + selectedFile.getName() + " (" + formatFileSize(selectedFile.length()) + ")");
        infoLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");
        
        grid.add(new Label("Remote Path:"), 0, 0);
        grid.add(remotePathField, 1, 0);
        grid.add(infoLabel, 0, 1, 2, 1);
        
        reusableContent.getChildren().add(grid);
        
        Platform.runLater(remotePathField::requestFocus);
        
        reusableDialog.setResultConverter(dialogButton -> {
            if (dialogButton == uploadButtonType) {
                String remotePath = remotePathField.getText().trim();
                performFileUpload(selectedFile, remotePath);
                return null;
            } else if (dialogButton == backButtonType) {
                updateDialogForFileTransferSelection();
                reusableDialog.showAndWait();
                return null;
            }
            return null;
        });
    }
    
    private void performFileUpload(java.io.File selectedFile, String remotePath) {
        // Update dialog for progress
        updateDialogForProgress("Uploading File", selectedFile.getName());
        reusableDialog.show();
        
        // Run upload in background thread
        new Thread(() -> {
            try {
                String finalRemotePath = remotePath.isEmpty() ? selectedFile.getName() : remotePath;
                client.getConnection().uploadFile(selectedFile.getAbsolutePath(), finalRemotePath);
                
                // Show success message on FX thread
                Platform.runLater(() -> {
                    updateDialogForSuccess("Upload Complete", 
                        "File '" + selectedFile.getName() + "' has been uploaded successfully!\n\n" +
                        "Remote path: " + finalRemotePath + "\n" +
                        "File size: " + formatFileSize(selectedFile.length()));
                    reusableDialog.showAndWait();
                });
                
            } catch (Exception e) {
                // Show error message on FX thread
                Platform.runLater(() -> {
                    updateDialogForError("Upload failed: " + e.getMessage());
                    reusableDialog.showAndWait();
                });
            }
        }).start();
    }
    
    private void updateDialogForProgress(String title, String filename) {
        reusableDialog.setTitle(title);
        reusableDialog.setHeaderText("Transferring: " + filename);
        
        // Clear existing content
        reusableContent.getChildren().clear();
        
        // Clear existing buttons
        reusableDialog.getDialogPane().getButtonTypes().clear();
        
        javafx.scene.control.ProgressBar progressBar = new javafx.scene.control.ProgressBar();
        progressBar.setPrefWidth(300);
        progressBar.setProgress(0.0);
        
        Label statusLabel = new Label("Preparing transfer...");
        statusLabel.setStyle("-fx-font-size: 12px;");
        
        reusableContent.getChildren().addAll(progressBar, statusLabel);
        
        // Store references for progress updates
        this.activeProgressBar = progressBar;
        this.activeStatusLabel = statusLabel;
    }
    
    private void updateDialogForSuccess(String title, String message) {
        reusableDialog.setTitle(title);
        reusableDialog.setHeaderText("Success");
        
        // Clear existing content
        reusableContent.getChildren().clear();
        
        // Clear existing buttons
        reusableDialog.getDialogPane().getButtonTypes().clear();
        
        ButtonType okButtonType = new ButtonType("OK", ButtonData.OK_DONE);
        reusableDialog.getDialogPane().getButtonTypes().add(okButtonType);
        
        Label messageLabel = new Label(message);
        messageLabel.setWrapText(true);
        messageLabel.setStyle("-fx-font-size: 12px;");
        
        reusableContent.getChildren().add(messageLabel);
        
        // Clear progress references
        this.activeProgressBar = null;
        this.activeStatusLabel = null;
    }
    
    private void updateDialogForError(String errorMessage) {
        reusableDialog.setTitle("Error");
        reusableDialog.setHeaderText("Error");
        
        // Clear existing content
        reusableContent.getChildren().clear();
        
        // Clear existing buttons
        reusableDialog.getDialogPane().getButtonTypes().clear();
        
        ButtonType okButtonType = new ButtonType("OK", ButtonData.OK_DONE);
        reusableDialog.getDialogPane().getButtonTypes().add(okButtonType);
        
        Label errorLabel = new Label(errorMessage);
        errorLabel.setWrapText(true);
        errorLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #d32f2f;");
        
        reusableContent.getChildren().add(errorLabel);
        
        // Clear progress references
        this.activeProgressBar = null;
        this.activeStatusLabel = null;
    }
    
    private void handleFileDownload() {
        System.out.println("DEBUG: File Download selected");
        
        // Update dialog for remote path input
        updateDialogForDownloadPath();
        reusableDialog.showAndWait();
    }
    
    private void updateDialogForDownloadPath() {
        reusableDialog.setHeaderText("Enter the remote file path to download");
        
        // Clear existing content
        reusableContent.getChildren().clear();
        
        // Clear existing buttons
        reusableDialog.getDialogPane().getButtonTypes().clear();
        
        ButtonType downloadButtonType = new ButtonType("Download", ButtonData.OK_DONE);
        ButtonType backButtonType = new ButtonType("Back", ButtonData.OTHER);
        ButtonType cancelButtonType = new ButtonType("Cancel", ButtonData.CANCEL_CLOSE);
        
        reusableDialog.getDialogPane().getButtonTypes().addAll(
            downloadButtonType, backButtonType, cancelButtonType
        );
        
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));
        
        TextField remotePathField = new TextField();
        remotePathField.setPromptText("e.g., myfile.txt or path/to/file.txt");
        
        grid.add(new Label("Remote File Path:"), 0, 0);
        grid.add(remotePathField, 1, 0);
        
        reusableContent.getChildren().add(grid);
        
        Platform.runLater(remotePathField::requestFocus);
        
        reusableDialog.setResultConverter(dialogButton -> {
            if (dialogButton == downloadButtonType) {
                String remotePath = remotePathField.getText().trim();
                if (remotePath.isEmpty()) {
                    updateDialogForError("Please enter a remote file path.");
                    reusableDialog.showAndWait();
                    return null;
                }
                performFileDownload(remotePath);
                return null;
            } else if (dialogButton == backButtonType) {
                updateDialogForFileTransferSelection();
                reusableDialog.showAndWait();
                return null;
            }
            return null;
        });
    }
    
    private void performFileDownload(String remotePath) {
        // Create file chooser for local save location
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Save File As");
        fileChooser.setInitialFileName(new java.io.File(remotePath).getName());
        
        java.io.File saveFile = fileChooser.showSaveDialog(primaryStage);
        if (saveFile == null) {
            return; // User cancelled
        }
        
        // Update dialog for progress
        updateDialogForProgress("Downloading File", new java.io.File(remotePath).getName());
        reusableDialog.show();
        
        // Run download in background thread
        new Thread(() -> {
            try {
                client.getConnection().downloadFile(remotePath, saveFile.getAbsolutePath());
                
                // Show success message on FX thread
                Platform.runLater(() -> {
                    updateDialogForSuccess("Download Complete", 
                        "File has been downloaded successfully!\n\n" +
                        "Remote path: " + remotePath + "\n" +
                        "Local path: " + saveFile.getAbsolutePath() + "\n" +
                        "File size: " + formatFileSize(saveFile.length()));
                    reusableDialog.showAndWait();
                });
                
            } catch (Exception e) {
                // Show error message on FX thread
                Platform.runLater(() -> {
                    updateDialogForError("Download failed: " + e.getMessage());
                    reusableDialog.showAndWait();
                });
            }
        }).start();
    }
    
    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
    
    private void handleDisconnect() {
        if (client != null) {
            // Send proper disconnect message to server
            try {
                client.sendDisconnect();
            } catch (Exception e) {
                System.out.println("DEBUG: Error sending disconnect message: " + e.getMessage());
            }
            client.stop();
        }
        Platform.exit();
    }

    private void handleManageSSHUsers() {
        System.out.println("DEBUG: Manage SSH Users button clicked");
        
        // Create the main management dialog
        Dialog<Void> managementDialog = new Dialog<>();
        managementDialog.setTitle("Manage SSH Users");
        managementDialog.setHeaderText("Choose an action to manage SSH users");
        
        ButtonType createButtonType = new ButtonType("Create New User", ButtonData.LEFT);
        ButtonType deleteButtonType = new ButtonType("Delete User", ButtonData.OTHER);
        ButtonType viewButtonType = new ButtonType("View Users", ButtonData.OTHER);
        ButtonType cancelButtonType = new ButtonType("Abbrechen", ButtonData.CANCEL_CLOSE);
        
        // Clear existing buttons and add them in the desired order
        managementDialog.getDialogPane().getButtonTypes().clear();
        managementDialog.getDialogPane().getButtonTypes().addAll(
            createButtonType, deleteButtonType, viewButtonType, cancelButtonType
        );
        
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        
        Label infoLabel = new Label("Select an action to manage SSH users on both client and server:");
        infoLabel.setWrapText(true);
        infoLabel.setStyle("-fx-font-size: 12px;");
        
        content.getChildren().add(infoLabel);
        
        managementDialog.getDialogPane().setContent(content);
        managementDialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        if (primaryStage != null && primaryStage.getScene() != null) {
            managementDialog.initOwner(primaryStage);
        }
        
        managementDialog.setResultConverter(dialogButton -> {
            if (dialogButton == createButtonType) {
                handleCreateNewUser();
                return null;
            } else if (dialogButton == deleteButtonType) {
                handleDeleteUser();
                return null;
            } else if (dialogButton == viewButtonType) {
                handleViewUsers();
                return null;
            } else if (dialogButton == cancelButtonType) {
                return null;
            }
            return null;
        });
        
        managementDialog.showAndWait();
    }
    
    private void handleCreateNewUser() {
        System.out.println("DEBUG: Create New User selected");
        
        // Create a dialog to get user information
        Dialog<java.util.Map<String, String>> userDialog = new Dialog<>();
        userDialog.setTitle("Create New Verified User");
        userDialog.setHeaderText("Enter details for the new user");
        
        ButtonType createButtonType = new ButtonType("Create User", ButtonData.OK_DONE);
        userDialog.getDialogPane().getButtonTypes().addAll(createButtonType, ButtonType.CANCEL);
        
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));
        
        TextField usernameField = new TextField();
        usernameField.setPromptText("e.g., newuser");
        
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Enter password");
        
        PasswordField confirmPasswordField = new PasswordField();
        confirmPasswordField.setPromptText("Confirm password");
        
        grid.add(new Label("Username:"), 0, 0);
        grid.add(usernameField, 1, 0);
        grid.add(new Label("Password:"), 0, 1);
        grid.add(passwordField, 1, 1);
        grid.add(new Label("Confirm Password:"), 0, 2);
        grid.add(confirmPasswordField, 1, 2);
        
        userDialog.getDialogPane().setContent(grid);
        userDialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        if (primaryStage != null && primaryStage.getScene() != null) {
            userDialog.initOwner(primaryStage);
        }
        
        Platform.runLater(usernameField::requestFocus);
        
        userDialog.setResultConverter(dialogButton -> {
            if (dialogButton == createButtonType) {
                String username = usernameField.getText().trim();
                String password = passwordField.getText();
                String confirmPassword = confirmPasswordField.getText();
                
                // Validate input
                if (username.isEmpty()) {
                    showError("Username cannot be empty");
                    return null;
                }
                
                if (password.isEmpty()) {
                    showError("Password cannot be empty");
                    return null;
                }
                
                if (!password.equals(confirmPassword)) {
                    showError("Passwords do not match");
                    return null;
                }
                
                java.util.Map<String, String> result = new java.util.HashMap<>();
                result.put("username", username);
                result.put("password", password);
                return result;
            }
            return null;
        });
        
        Optional<java.util.Map<String, String>> result = userDialog.showAndWait();
        result.ifPresent(userData -> {
            String username = userData.get("username");
            String password = userData.get("password");
            
            // Show progress dialog
            javafx.scene.control.Alert progressAlert = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.INFORMATION);
            progressAlert.setTitle("Creating User");
            progressAlert.setHeaderText("Creating new verified user: " + username);
            progressAlert.setContentText("Please wait while the user is being created...");
            progressAlert.initOwner(primaryStage);
            progressAlert.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            
            // Set dialog size
            progressAlert.setResizable(false);
            progressAlert.getDialogPane().setPrefWidth(400);
            progressAlert.getDialogPane().setPrefHeight(150);
            
            // Run the user creation in a background thread
            new Thread(() -> {
                try {
                    ssh.utils.CreateVerifiedUser.createUser(username, password, client);
                    
                    // Show success message on FX thread
                    Platform.runLater(() -> {
                        progressAlert.close();
                        
                        // Create a properly formatted success dialog
                        javafx.scene.control.Alert successAlert = new javafx.scene.control.Alert(
                            javafx.scene.control.Alert.AlertType.INFORMATION);
                        successAlert.setTitle("User Created Successfully");
                        successAlert.setHeaderText("New Verified User Created");
                        successAlert.setContentText("User '" + username + "' has been created successfully!\n\n" +
                                                   "The user is now available for authentication with:\n" +
                                                   "• Username: " + username + "\n" +
                                                   "• Password: " + password + "\n" +
                                                   "• Public key authentication enabled\n\n" +
                                                   "The server's user database has been automatically reloaded.\n" +
                                                   "You can now use the new user immediately without restarting the server.");
                        
                        // Set dialog properties
                        successAlert.initOwner(primaryStage);
                        successAlert.initModality(javafx.stage.Modality.APPLICATION_MODAL);
                        
                        // Set dialog size
                        successAlert.setResizable(true);
                        successAlert.getDialogPane().setPrefWidth(500);
                        successAlert.getDialogPane().setPrefHeight(300);
                        
                        // Show the dialog
                        successAlert.showAndWait();
                    });
                    
                } catch (Exception e) {
                    // Show error message on FX thread
                    Platform.runLater(() -> {
                        progressAlert.close();
                        showError("Failed to create user: " + e.getMessage());
                    });
                }
            }).start();
            
            progressAlert.showAndWait();
        });
    }
    
    private void handleDeleteUser() {
        System.out.println("DEBUG: Delete User selected");
        
        try {
            // Get available users
            CredentialsManager credentialsManager = new CredentialsManager("config/credentials.properties");
            String[] availableUsers = credentialsManager.getAvailableUsers();
            
            if (availableUsers.length == 0) {
                showError("No users found to delete.");
                return;
            }
            
            // Create delete user dialog
            Dialog<String> deleteDialog = new Dialog<>();
            deleteDialog.setTitle("Delete SSH User");
            deleteDialog.setHeaderText("Select a user to delete");
            
            ButtonType deleteButtonType = new ButtonType("Delete User", ButtonData.OK_DONE);
            deleteDialog.getDialogPane().getButtonTypes().addAll(deleteButtonType, ButtonType.CANCEL);
            
            VBox content = new VBox(10);
            content.setPadding(new Insets(20));
            
            Label infoLabel = new Label("Select a user to delete from both client and server:");
            infoLabel.setWrapText(true);
            infoLabel.setStyle("-fx-font-size: 12px;");
            
            ComboBox<String> userComboBox = new ComboBox<>();
            userComboBox.getItems().addAll(availableUsers);
            userComboBox.setPromptText("Select a user...");
            
            content.getChildren().addAll(infoLabel, userComboBox);
            
            deleteDialog.getDialogPane().setContent(content);
            deleteDialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            if (primaryStage != null && primaryStage.getScene() != null) {
                deleteDialog.initOwner(primaryStage);
            }
            
            Platform.runLater(userComboBox::requestFocus);
            
            deleteDialog.setResultConverter(dialogButton -> {
                if (dialogButton == deleteButtonType) {
                    return userComboBox.getValue();
                }
                return null;
            });
            
            Optional<String> result = deleteDialog.showAndWait();
            result.ifPresent(username -> {
                if (username == null || username.trim().isEmpty()) {
                    showError("Please select a user to delete.");
                    return;
                }
                
                // Show confirmation dialog
                javafx.scene.control.Alert confirmDialog = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.CONFIRMATION);
                confirmDialog.setTitle("Confirm Deletion");
                confirmDialog.setHeaderText("Delete User: " + username);
                confirmDialog.setContentText("Are you sure you want to delete user '" + username + "'?\n\n" +
                                           "This will:\n" +
                                           "• Remove the user from the server database\n" +
                                           "• Remove the user from client credentials\n" +
                                           "• Delete the user's SSH keys\n" +
                                           "• Remove authorized keys from the server\n\n" +
                                           "This action cannot be undone!");
                confirmDialog.initOwner(primaryStage);
                confirmDialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);
                
                Optional<javafx.scene.control.ButtonType> confirmResult = confirmDialog.showAndWait();
                if (confirmResult.isPresent() && confirmResult.get() == javafx.scene.control.ButtonType.OK) {
                    // Show progress dialog
                    javafx.scene.control.Alert progressAlert = new javafx.scene.control.Alert(
                        javafx.scene.control.Alert.AlertType.INFORMATION);
                    progressAlert.setTitle("Deleting User");
                    progressAlert.setHeaderText("Deleting user: " + username);
                    progressAlert.setContentText("Please wait while the user is being deleted...");
                    progressAlert.initOwner(primaryStage);
                    progressAlert.initModality(javafx.stage.Modality.APPLICATION_MODAL);
                    progressAlert.setResizable(false);
                    progressAlert.getDialogPane().setPrefWidth(400);
                    progressAlert.getDialogPane().setPrefHeight(150);
                    
                    // Run the user deletion in a background thread
                    new Thread(() -> {
                        try {
                            ssh.utils.DeleteVerifiedUser.deleteUser(username, client);
                            
                            // Show success message on FX thread
                            Platform.runLater(() -> {
                                progressAlert.close();
                                
                                javafx.scene.control.Alert successAlert = new javafx.scene.control.Alert(
                                    javafx.scene.control.Alert.AlertType.INFORMATION);
                                successAlert.setTitle("User Deleted Successfully");
                                successAlert.setHeaderText("User Deleted");
                                successAlert.setContentText("User '" + username + "' has been deleted successfully!\n\n" +
                                                           "The following actions were completed:\n" +
                                                           "• User removed from server database\n" +
                                                           "• User removed from client credentials\n" +
                                                           "• SSH keys deleted\n" +
                                                           "• Authorized keys removed from server\n\n" +
                                                           "The server's user database has been automatically reloaded.");
                                
                                successAlert.initOwner(primaryStage);
                                successAlert.initModality(javafx.stage.Modality.APPLICATION_MODAL);
                                successAlert.setResizable(true);
                                successAlert.getDialogPane().setPrefWidth(500);
                                successAlert.getDialogPane().setPrefHeight(300);
                                
                                successAlert.showAndWait();
                            });
                            
                        } catch (Exception e) {
                            // Show error message on FX thread
                            Platform.runLater(() -> {
                                progressAlert.close();
                                showError("Failed to delete user: " + e.getMessage());
                            });
                        }
                    }).start();
                    
                    progressAlert.showAndWait();
                }
            });
            
        } catch (Exception e) {
            showError("Failed to load users: " + e.getMessage());
        }
    }
    
    private void handleViewUsers() {
        System.out.println("DEBUG: View Users selected");
        
        try {
            // Get available users
            CredentialsManager credentialsManager = new CredentialsManager("config/credentials.properties");
            String[] availableUsers = credentialsManager.getAvailableUsers();
            
            // Create view users dialog
            javafx.scene.control.Alert viewDialog = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.INFORMATION);
            viewDialog.setTitle("SSH Users");
            viewDialog.setHeaderText("Current SSH Users");
            
            if (availableUsers.length == 0) {
                viewDialog.setContentText("No users found.");
            } else {
                StringBuilder content = new StringBuilder("The following users are configured:\n\n");
                for (String user : availableUsers) {
                    content.append("• ").append(user).append("\n");
                }
                content.append("\nTotal users: ").append(availableUsers.length);
                viewDialog.setContentText(content.toString());
            }
            
            viewDialog.initOwner(primaryStage);
            viewDialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            viewDialog.setResizable(true);
            viewDialog.getDialogPane().setPrefWidth(400);
            viewDialog.getDialogPane().setPrefHeight(300);
            
            viewDialog.showAndWait();
            
        } catch (Exception e) {
            showError("Failed to load users: " + e.getMessage());
        }
    }
    
    private void showError(String message) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
            javafx.scene.control.Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText("Error");
        alert.setContentText(message);
        alert.initOwner(primaryStage);
        alert.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        
        // Set dialog size
        alert.setResizable(true);
        alert.getDialogPane().setPrefWidth(450);
        alert.getDialogPane().setPrefHeight(200);
        
        alert.showAndWait();
    }
} 