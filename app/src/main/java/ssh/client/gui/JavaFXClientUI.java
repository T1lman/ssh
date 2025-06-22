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

public class JavaFXClientUI implements ClientUI {

    private Stage primaryStage;
    private TextArea terminalOutput;
    private ShellInputField commandInput;
    private SSHClient client;
    private String currentWorkingDirectory = "~";

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
        
        // Initialize with a better looking scene to prevent null pointer exceptions
        VBox root = new VBox(20);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: #2c3e50; -fx-alignment: center;");
        
        javafx.scene.control.Label titleLabel = new javafx.scene.control.Label("SSH Client");
        titleLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #ecf0f1;");
        
        javafx.scene.control.Label statusLabel = new javafx.scene.control.Label("Initializing connection...");
        statusLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #bdc3c7;");
        
        javafx.scene.control.ProgressIndicator progressIndicator = new javafx.scene.control.ProgressIndicator();
        progressIndicator.setStyle("-fx-progress-color: #3498db;");
        
        root.getChildren().addAll(titleLabel, progressIndicator, statusLabel);
        
        this.primaryStage.setScene(new javafx.scene.Scene(root, 400, 300));
        this.primaryStage.setMinWidth(400);
        this.primaryStage.setMinHeight(300);
        
        // We don't show the stage until a connection is attempted
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
        
        javafx.scene.control.Button createUserButton = new javafx.scene.control.Button("Create New Verified User");
        createUserButton.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 5px;");
        createUserButton.setOnAction(e -> handleCreateNewUser());
        
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
        Dialog<ServerInfo> dialog = new Dialog<>();
        dialog.setTitle("Connect to Server");
        dialog.setHeaderText("Enter the server's connection details.");

        ButtonType connectButtonType = new ButtonType("Connect", ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(connectButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField hostField = new TextField();
        hostField.setPromptText("localhost");
        TextField portField = new TextField();
        portField.setPromptText("2222");

        grid.add(new Label("Host:"), 0, 0);
        grid.add(hostField, 1, 0);
        grid.add(new Label("Port:"), 0, 1);
        grid.add(portField, 1, 1);

        dialog.getDialogPane().setContent(grid);

        Platform.runLater(hostField::requestFocus);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == connectButtonType) {
                String host = hostField.getText().isEmpty() ? "localhost" : hostField.getText();
                int port = portField.getText().isEmpty() ? 2222 : Integer.parseInt(portField.getText());
                return new ServerInfo(host, port, null); // Username set later
            }
            return null;
        });

        Optional<ServerInfo> result = dialog.showAndWait();
        return result.orElse(null);
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
        // Would update a progress bar
        System.out.println("GUI_FTP: " + filename + " - " + percentage + "%");
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
        
        // Ensure this runs on the JavaFX application thread
        if (!Platform.isFxApplicationThread()) {
            System.out.println("DEBUG: Not on FX thread, using Platform.runLater");
            // If we're not on the FX thread, we need to handle this differently
            // For now, we'll use a simple approach with Platform.runLater
            final AuthCredentials[] result = new AuthCredentials[1];
            final CountDownLatch latch = new CountDownLatch(1);
            
            Platform.runLater(() -> {
                System.out.println("DEBUG: Creating auth dialog on FX thread");
                result[0] = createAuthDialog(availableUsers);
                latch.countDown();
            });
            
            try {
                latch.await();
                System.out.println("DEBUG: Auth dialog result: " + (result[0] != null ? "success" : "cancelled"));
                return result[0];
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("DEBUG: Auth dialog interrupted");
                return null;
            }
        } else {
            System.out.println("DEBUG: Already on FX thread, creating dialog directly");
            AuthCredentials result = createAuthDialog(availableUsers);
            System.out.println("DEBUG: Auth dialog result: " + (result != null ? "success" : "cancelled"));
            return result;
        }
    }

    private AuthCredentials createAuthDialog(String[] availableUsers) {
        System.out.println("DEBUG: Creating authentication dialog");
        Dialog<AuthCredentials> dialog = new Dialog<>();
        dialog.setTitle("Authentication");
        dialog.setHeaderText("Please enter your credentials.");

        ButtonType loginButtonType = new ButtonType("Login", ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(loginButtonType, ButtonType.CANCEL);

        // Username selection
        ComboBox<String> userComboBox = new ComboBox<>();
        for (String user : availableUsers) {
            userComboBox.getItems().add(user);
        }
        userComboBox.setValue(availableUsers.length > 0 ? availableUsers[0] : "");

        // (Optional) Authentication type selection (hidden or fixed to 'dual')
        // ComboBox<String> authTypeComboBox = new ComboBox<>();
        // authTypeComboBox.getItems().addAll("dual");
        // authTypeComboBox.setValue("dual");

        // Layout
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));
        grid.add(new Label("Username:"), 0, 0);
        grid.add(userComboBox, 1, 0);
        // grid.add(new Label("Auth Type:"), 0, 1);
        // grid.add(authTypeComboBox, 1, 1);

        dialog.getDialogPane().setContent(grid);

        // Result converter: load credentials for selected user
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == loginButtonType) {
                String selectedUser = userComboBox.getValue();
                CredentialsManager credentialsManager = new CredentialsManager("config/credentials.properties");
                return credentialsManager.getAuthCredentials(selectedUser);
            } else {
                return null;
            }
        });

        // Set the dialog to be modal and block the application
        dialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        
        // Only set the owner if the stage has a scene
        if (primaryStage != null && primaryStage.getScene() != null) {
            dialog.initOwner(primaryStage);
        }

        Platform.runLater(userComboBox::requestFocus);

        System.out.println("DEBUG: Showing authentication dialog");
        Optional<AuthCredentials> result = dialog.showAndWait();
        System.out.println("DEBUG: Dialog closed, result: " + (result.isPresent() ? "present" : "empty"));
        return result.orElse(null);
    }

    @Override
    public void showConnectionProgress(String step) {
        // We could show a progress indicator dialog here in the future
        System.out.println("GUI_PROGRESS: " + step);
    }

    @Override
    public boolean shouldContinue() {
        // The GUI's lifecycle is managed by the user closing the window
        return true;
    }

    private void handleFileTransfer() {
        // Show file transfer dialog
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
        alert.setTitle("File Transfer");
        alert.setHeaderText("File Transfer Feature");
        alert.setContentText("File transfer functionality is available but not yet implemented in the GUI.\n\nUse the console client for file transfer operations.");
        alert.initOwner(primaryStage);
        alert.showAndWait();
    }
    
    private void handleDisconnect() {
        // Show confirmation dialog
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION);
        alert.setTitle("Disconnect");
        alert.setHeaderText("Disconnect from Server");
        alert.setContentText("Are you sure you want to disconnect from the server?");
        alert.initOwner(primaryStage);
        
        Optional<javafx.scene.control.ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == javafx.scene.control.ButtonType.OK) {
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
    }

    private void handleCreateNewUser() {
        System.out.println("DEBUG: Create New Verified User button clicked");
        
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
            
            // Run the user creation in a background thread
            new Thread(() -> {
                try {
                    ssh.utils.CreateVerifiedUser.createUser(username, password, client);
                    
                    // Show success message on FX thread
                    Platform.runLater(() -> {
                        progressAlert.close();
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
                        successAlert.initOwner(primaryStage);
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
    
    private void showError(String message) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
            javafx.scene.control.Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText("Error");
        alert.setContentText(message);
        alert.initOwner(primaryStage);
        alert.showAndWait();
    }
} 