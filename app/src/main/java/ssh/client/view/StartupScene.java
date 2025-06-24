package ssh.client.view;

import javafx.application.Platform;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import ssh.client.model.ServerInfo;
import ssh.model.utils.CredentialsManager;

import java.util.function.Consumer;

/**
 * Handles the startup scene with animation and connection setup.
 * Manages the initial connection form and username selection.
 */
public class StartupScene {
    private final Stage primaryStage;
    private final VBox root;
    
    // UI Components
    private Label statusLabel;
    private ProgressIndicator progressIndicator;
    private VBox connectionForm;
    private VBox usernameForm;
    private TextField hostField;
    private TextField portField;
    private Button connectButton;
    private Button cancelButton;
    private ComboBox<String> usernameComboBox;
    private Button loginButton;
    private Button backButton;
    
    // State
    private ServerInfo pendingServerInfo;
    private String[] availableUsers;
    
    // Controller reference for business logic
    private ssh.client.controller.SSHClientController controller;
    
    // Callbacks
    private Consumer<ServerInfo> onConnectionRequested;
    private Consumer<ServerInfo> onLoginRequested;
    private Runnable onCancelRequested;
    
    public StartupScene(Stage primaryStage, ssh.client.controller.SSHClientController controller) {
        this.primaryStage = primaryStage;
        this.controller = controller;
        this.root = new VBox(20);
        this.root.setAlignment(javafx.geometry.Pos.CENTER);
        this.root.setStyle("-fx-background-color: linear-gradient(to bottom right, #232526, #414345); -fx-padding: 40px;");
        
        createUI();
        startAnimation();
    }
    
    private void createUI() {
        // Title and subtitle
        Label titleLabel = UIUtils.createTitleLabel("SSH Client");
        Label subtitleLabel = UIUtils.createSubtitleLabel("Secure Shell Connection");
        
        // Progress indicator and status
        progressIndicator = UIUtils.createProgressIndicator();
        statusLabel = UIUtils.createLabel("Initializing...");
        
        // Create forms
        createConnectionForm();
        createUsernameForm();
        
        // Add all elements to root
        root.getChildren().addAll(titleLabel, subtitleLabel, progressIndicator, statusLabel, connectionForm, usernameForm);
    }
    
    private void createConnectionForm() {
        connectionForm = new VBox(15);
        connectionForm.setVisible(false);
        connectionForm.setManaged(false);
        connectionForm.setAlignment(javafx.geometry.Pos.CENTER);
        
        Label formTitle = UIUtils.createTitleLabel("Server Connection");
        formTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #ecf0f1;");
        formTitle.setAlignment(javafx.geometry.Pos.CENTER);
        
        // Host input row
        Label hostLabel = UIUtils.createLabel("Server Host:");
        hostLabel.setPrefWidth(100);
        hostField = UIUtils.createTextField("localhost");
        hostField.setPrefWidth(200);
        
        HBox hostRow = new HBox(10);
        hostRow.setAlignment(javafx.geometry.Pos.CENTER);
        hostRow.getChildren().addAll(hostLabel, hostField);
        
        // Port input row
        Label portLabel = UIUtils.createLabel("Port:");
        portLabel.setPrefWidth(100);
        portField = UIUtils.createTextField("2222");
        portField.setPrefWidth(200);
        
        HBox portRow = new HBox(10);
        portRow.setAlignment(javafx.geometry.Pos.CENTER);
        portRow.getChildren().addAll(portLabel, portField);
        
        // Buttons
        cancelButton = UIUtils.createRedButton("Cancel");
        connectButton = UIUtils.createPrimaryButton("Connect");
        
        HBox buttonBox = UIUtils.createHBox(15);
        buttonBox.getChildren().addAll(cancelButton, connectButton);
        
        connectionForm.getChildren().addAll(formTitle, hostRow, portRow, buttonBox);
        
        // Set up button actions
        connectButton.setOnAction(e -> handleConnect());
        cancelButton.setOnAction(e -> {
            if (onCancelRequested != null) {
                onCancelRequested.run();
            }
        });
    }
    
    private void createUsernameForm() {
        usernameForm = new VBox(15);
        usernameForm.setVisible(false);
        usernameForm.setManaged(false);
        usernameForm.setAlignment(javafx.geometry.Pos.CENTER);
        
        Label usernameLabel = UIUtils.createLabel("Select your username:");
        usernameLabel.setStyle("-fx-text-fill: #ecf0f1; -fx-font-weight: bold; -fx-font-size: 14px;");
        usernameLabel.setAlignment(javafx.geometry.Pos.CENTER);
        
        usernameComboBox = UIUtils.createComboBox();
        usernameComboBox.setPrefWidth(200);
        
        backButton = UIUtils.createRedButton("Back");
        loginButton = UIUtils.createPrimaryButton("Login");
        
        HBox usernameButtonBox = UIUtils.createHBox(15);
        usernameButtonBox.getChildren().addAll(backButton, loginButton);
        
        usernameForm.getChildren().addAll(usernameLabel, usernameComboBox, usernameButtonBox);
        
        // Set up button actions
        loginButton.setOnAction(e -> handleLogin());
        backButton.setOnAction(e -> showConnectionForm());
    }
    
    private void startAnimation() {
        showConnectionForm();
        Platform.runLater(hostField::requestFocus);
    }
    
    private void handleConnect() {
        String host = hostField.getText().isEmpty() ? "localhost" : hostField.getText();
        int port;
        
        try {
            port = portField.getText().isEmpty() ? 2222 : Integer.parseInt(portField.getText());
        } catch (NumberFormatException e) {
            showError("Invalid port number. Please enter a valid number.");
            return;
        }
        
        pendingServerInfo = new ServerInfo(host, port, null);
        showUsernameForm();
    }
    
    private void showUsernameForm() {
        try {
            // Get available users from the controller (MVC compliance)
            String[] availableUsers = controller != null ? controller.getAvailableUsers() : new String[0];
            
            usernameComboBox.getItems().clear();
            for (String user : availableUsers) {
                usernameComboBox.getItems().add(user);
            }
            if (availableUsers.length > 0) {
                usernameComboBox.setValue(availableUsers[0]);
            }
            
            connectionForm.setVisible(false);
            connectionForm.setManaged(false);
            usernameForm.setVisible(true);
            usernameForm.setManaged(true);
            
            statusLabel.setText("Select your username to connect");
            Platform.runLater(usernameComboBox::requestFocus);
            
        } catch (Exception e) {
            showError("Failed to load user credentials: " + e.getMessage());
        }
    }
    
    private void showConnectionForm() {
        usernameForm.setVisible(false);
        usernameForm.setManaged(false);
        connectionForm.setVisible(true);
        connectionForm.setManaged(true);
        
        statusLabel.setText("Enter server details to connect");
        Platform.runLater(hostField::requestFocus);
    }
    
    private void handleLogin() {
        String selectedUser = usernameComboBox.getValue();
        if (selectedUser == null || selectedUser.trim().isEmpty()) {
            showError("Please select a username.");
            return;
        }
        
        if (onLoginRequested != null) {
            pendingServerInfo.setUsername(selectedUser);
            onLoginRequested.accept(pendingServerInfo);
        }
    }
    
    public void showConnectingState(String message) {
        statusLabel.setText(message);
        usernameForm.setVisible(false);
        usernameForm.setManaged(false);
        progressIndicator.setVisible(true);
        progressIndicator.setManaged(true);
        progressIndicator.setProgress(-1); // Indeterminate progress
    }
    
    public void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText("Connection Error");
        alert.setContentText(message);
        alert.initOwner(primaryStage);
        alert.showAndWait();
        // Exit application after error dialog is acknowledged
        javafx.application.Platform.exit();
        System.exit(1);
    }
    
    public VBox getRoot() {
        return root;
    }
    
    public ServerInfo getPendingServerInfo() {
        return pendingServerInfo;
    }
    
    // Callback setters
    public void setOnConnectionRequested(Consumer<ServerInfo> callback) {
        this.onConnectionRequested = callback;
    }
    
    public void setOnLoginRequested(Consumer<ServerInfo> callback) {
        this.onLoginRequested = callback;
    }
    
    public void setOnCancelRequested(Runnable callback) {
        this.onCancelRequested = callback;
    }
} 