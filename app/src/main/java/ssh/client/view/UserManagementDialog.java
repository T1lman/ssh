package ssh.client.view;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import ssh.client.controller.SSHClientController;

import java.util.function.Consumer;

/**
 * Dialog for managing SSH users (create, delete, view).
 * Pure view class - delegates all business logic to the controller.
 */
public class UserManagementDialog {
    private final Stage primaryStage;
    private Dialog<ButtonType> dialog;
    private VBox content;
    
    // Controller reference for business logic
    private SSHClientController controller;

    // Callbacks for user actions
    private Consumer<String> onUserCreateRequested;
    private Consumer<String> onUserDeleteRequested;
    private Consumer<String> onUserViewRequested;

    public UserManagementDialog(Stage primaryStage, SSHClientController controller, 
                              Consumer<String> onUserCreateRequested, Consumer<String> onUserDeleteRequested, Consumer<String> onUserViewRequested) {
        this.primaryStage = primaryStage;
        this.controller = controller;
        this.onUserCreateRequested = onUserCreateRequested;
        this.onUserDeleteRequested = onUserDeleteRequested;
        this.onUserViewRequested = onUserViewRequested;
        
        initializeDialog();
    }
    
    public void show() {
        showMainMenu(); // Always reset to main menu
        dialog.showAndWait();
    }
    
    private void initializeDialog() {
        dialog = new Dialog<>();
        dialog.setTitle("Manage SSH Users");
        dialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        dialog.initOwner(primaryStage);
        dialog.setResizable(true);
        dialog.getDialogPane().setPrefWidth(500);
        dialog.getDialogPane().setPrefHeight(350);
        content = new VBox(15);
        content.setPadding(new Insets(20));
        dialog.getDialogPane().setContent(content);
        showMainMenu();
    }
    
    private void showMainMenu() {
        dialog.setHeaderText("Choose an action to manage SSH users");
        content.getChildren().clear();
        dialog.getDialogPane().getButtonTypes().clear();
        ButtonType createButtonType = new ButtonType("Create New User", ButtonBar.ButtonData.LEFT);
        ButtonType deleteButtonType = new ButtonType("Delete User", ButtonBar.ButtonData.OTHER);
        ButtonType viewButtonType = new ButtonType("View Users", ButtonBar.ButtonData.OTHER);
        ButtonType cancelButtonType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(createButtonType, deleteButtonType, viewButtonType, cancelButtonType);
        Label infoLabel = new Label("Select an action to manage SSH users on both client and server:");
        infoLabel.setWrapText(true);
        infoLabel.setStyle("-fx-font-size: 12px;");
        content.getChildren().add(infoLabel);
        Platform.runLater(() -> {
            Button createButton = (Button) dialog.getDialogPane().lookupButton(createButtonType);
            if (createButton != null) {
                createButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
                    event.consume();
                    showCreateUserForm();
                });
            }
            Button deleteButton = (Button) dialog.getDialogPane().lookupButton(deleteButtonType);
            if (deleteButton != null) {
                deleteButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
                    event.consume();
                    showDeleteUserForm();
                });
            }
            Button viewButton = (Button) dialog.getDialogPane().lookupButton(viewButtonType);
            if (viewButton != null) {
                viewButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
                    event.consume();
                    showViewUsers();
                });
            }
        });
    }
    
    private void showCreateUserForm() {
        dialog.setHeaderText("Create New Verified User");
        content.getChildren().clear();
        dialog.getDialogPane().getButtonTypes().clear();
        ButtonType createButtonType = new ButtonType("Create User", ButtonBar.ButtonData.OK_DONE);
        ButtonType backButtonType = new ButtonType("Back", ButtonBar.ButtonData.OTHER);
        ButtonType cancelButtonType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(createButtonType, backButtonType, cancelButtonType);
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
        content.getChildren().add(grid);
        Platform.runLater(() -> {
            usernameField.requestFocus();
            Button createButton = (Button) dialog.getDialogPane().lookupButton(createButtonType);
            if (createButton != null) {
                createButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
                    event.consume();
                    String username = usernameField.getText().trim();
                    String password = passwordField.getText();
                    String confirmPassword = confirmPasswordField.getText();
                    if (username.isEmpty()) {
                        showErrorInline("Username cannot be empty");
                        return;
                    }
                    if (password.isEmpty()) {
                        showErrorInline("Password cannot be empty");
                        return;
                    }
                    if (!password.equals(confirmPassword)) {
                        showErrorInline("Passwords do not match");
                        return;
                    }
                    showProgressInline("Creating User", "Creating new verified user: " + username);
                    new Thread(() -> {
                        try {
                            controller.createUser(username, password);
                            Platform.runLater(() -> showResultInline(true, "User '" + username + "' has been created successfully!\n\n" +
                                "The user is now available for authentication with:\n" +
                                "• Username: " + username + "\n" +
                                "• Password: " + password + "\n" +
                                "• Public key authentication enabled\n\n" +
                                "The server's user database has been automatically reloaded.\n" +
                                "You can now use the new user immediately without restarting the server."));
                        } catch (Exception e) {
                            Platform.runLater(() -> showResultInline(false, "Failed to create user: " + e.getMessage()));
                        }
                    }).start();
                });
            }
            Button backButton = (Button) dialog.getDialogPane().lookupButton(backButtonType);
            if (backButton != null) {
                backButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
                    event.consume();
                    showMainMenu();
                });
            }
        });
    }
    
    private void showDeleteUserForm() {
        dialog.setHeaderText("Delete SSH User");
        content.getChildren().clear();
        dialog.getDialogPane().getButtonTypes().clear();
        ButtonType deleteButtonType = new ButtonType("Delete User", ButtonBar.ButtonData.OK_DONE);
        ButtonType backButtonType = new ButtonType("Back", ButtonBar.ButtonData.OTHER);
        ButtonType cancelButtonType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(deleteButtonType, backButtonType, cancelButtonType);
        ComboBox<String> userComboBox = new ComboBox<>();
        try {
            String[] availableUsers = controller.getAvailableUsers();
            userComboBox.getItems().addAll(availableUsers);
            if (availableUsers.length > 0) {
                userComboBox.setValue(availableUsers[0]);
            }
        } catch (Exception e) {
            showErrorInline("Failed to load users: " + e.getMessage());
        }
        VBox vbox = new VBox(10);
        vbox.getChildren().addAll(new Label("Select a user to delete from both client and server:"), userComboBox);
        content.getChildren().add(vbox);
        Platform.runLater(() -> {
            userComboBox.requestFocus();
            Button deleteButton = (Button) dialog.getDialogPane().lookupButton(deleteButtonType);
            if (deleteButton != null) {
                deleteButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
                    event.consume();
                    String username = userComboBox.getValue();
                    if (username == null || username.trim().isEmpty()) {
                        showErrorInline("Please select a user to delete.");
                        return;
                    }
                    showProgressInline("Deleting User", "Deleting verified user: " + username);
                    new Thread(() -> {
                        try {
                            controller.deleteUser(username);
                            Platform.runLater(() -> showResultInline(true, "User '" + username + "' has been deleted successfully!\n\n" +
                                "The user has been removed from:\n" +
                                "• Server user database\n" +
                                "• Client credentials\n" +
                                "• SSH key files\n\n" +
                                "The server's user database has been automatically reloaded."));
                        } catch (Exception e) {
                            Platform.runLater(() -> showResultInline(false, "Failed to delete user: " + e.getMessage()));
                        }
                    }).start();
                });
            }
            Button backButton = (Button) dialog.getDialogPane().lookupButton(backButtonType);
            if (backButton != null) {
                backButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
                    event.consume();
                    showMainMenu();
                });
            }
        });
    }
    
    private void showViewUsers() {
        dialog.setHeaderText("Current SSH Users");
        content.getChildren().clear();
        dialog.getDialogPane().getButtonTypes().clear();
        ButtonType backButtonType = new ButtonType("Back", ButtonBar.ButtonData.OTHER);
        ButtonType cancelButtonType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(backButtonType, cancelButtonType);
        try {
            String[] availableUsers = controller.getAvailableUsers();
            StringBuilder usersList = new StringBuilder();
            if (availableUsers.length == 0) {
                usersList.append("No users found.");
            } else {
                usersList.append("The following users are configured:\n\n");
                for (String user : availableUsers) {
                    usersList.append("• ").append(user).append("\n");
                }
                usersList.append("\nTotal users: ").append(availableUsers.length);
            }
            Label usersLabel = new Label(usersList.toString());
            usersLabel.setWrapText(true);
            usersLabel.setStyle("-fx-font-size: 12px;");
            content.getChildren().add(usersLabel);
        } catch (Exception e) {
            showErrorInline("Failed to load users: " + e.getMessage());
        }
        Platform.runLater(() -> {
            Button backButton = (Button) dialog.getDialogPane().lookupButton(backButtonType);
            if (backButton != null) {
                backButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
                    event.consume();
                    showMainMenu();
                });
            }
        });
    }
    
    private void showProgressInline(String title, String message) {
        dialog.setHeaderText(title);
        content.getChildren().clear();
        Label msgLabel = new Label(message);
        msgLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");
        ProgressBar progressBar = new ProgressBar();
        progressBar.setPrefWidth(300);
        progressBar.setProgress(-1);
        content.getChildren().addAll(msgLabel, progressBar);
        dialog.getDialogPane().getButtonTypes().clear();
    }
    
    private void showResultInline(boolean success, String message) {
        content.getChildren().clear();
        Label resultLabel = new Label(message);
        resultLabel.setWrapText(true);
        resultLabel.setStyle("-fx-font-size: 12px; " + (success ? "-fx-text-fill: #27ae60;" : "-fx-text-fill: #e74c3c;"));
        content.getChildren().add(resultLabel);
        ButtonType okButtonType = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(okButtonType);
    }
    
    private void showErrorInline(String message) {
        Label errorLabel = new Label(message);
        errorLabel.setWrapText(true);
        errorLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #e74c3c;");
        if (content.getChildren().isEmpty() || !(content.getChildren().get(content.getChildren().size() - 1) instanceof Label)) {
            content.getChildren().add(errorLabel);
        } else {
            content.getChildren().set(content.getChildren().size() - 1, errorLabel);
        }
    }
} 