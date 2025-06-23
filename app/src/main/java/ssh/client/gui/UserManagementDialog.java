package ssh.client.gui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import ssh.client.SSHClient;
import ssh.utils.CredentialsManager;

import java.util.Map;
import java.util.Optional;

/**
 * Handles SSH user management operations.
 * Manages user creation, deletion, and viewing with dialogs.
 */
public class UserManagementDialog {
    private final Stage primaryStage;
    private final SSHClient client;
    
    public UserManagementDialog(Stage primaryStage, SSHClient client) {
        this.primaryStage = primaryStage;
        this.client = client;
    }
    
    public void show() {
        System.out.println("DEBUG: Manage SSH Users button clicked");
        
        Dialog<Void> managementDialog = new Dialog<>();
        managementDialog.setTitle("Manage SSH Users");
        managementDialog.setHeaderText("Choose an action to manage SSH users");
        
        ButtonType createButtonType = new ButtonType("Create New User", ButtonBar.ButtonData.LEFT);
        ButtonType deleteButtonType = new ButtonType("Delete User", ButtonBar.ButtonData.OTHER);
        ButtonType viewButtonType = new ButtonType("View Users", ButtonBar.ButtonData.OTHER);
        ButtonType cancelButtonType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        
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
        managementDialog.initOwner(primaryStage);
        
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
            }
            return null;
        });
        
        managementDialog.showAndWait();
    }
    
    private void handleCreateNewUser() {
        System.out.println("DEBUG: Create New User selected");
        
        Dialog<Map<String, String>> userDialog = new Dialog<>();
        userDialog.setTitle("Create New Verified User");
        userDialog.setHeaderText("Enter details for the new user");
        
        ButtonType createButtonType = new ButtonType("Create User", ButtonBar.ButtonData.OK_DONE);
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
        userDialog.initOwner(primaryStage);
        
        Platform.runLater(usernameField::requestFocus);
        
        userDialog.setResultConverter(dialogButton -> {
            if (dialogButton == createButtonType) {
                String username = usernameField.getText().trim();
                String password = passwordField.getText();
                String confirmPassword = confirmPasswordField.getText();
                
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
                
                Map<String, String> result = Map.of("username", username, "password", password);
                return result;
            }
            return null;
        });
        
        Optional<Map<String, String>> result = userDialog.showAndWait();
        result.ifPresent(userData -> {
            String username = userData.get("username");
            String password = userData.get("password");
            showProgressDialog("Creating User", "Creating new verified user: " + username, () -> {
                try {
                    ssh.utils.CreateVerifiedUser.createUser(username, password, client);
                    Platform.runLater(() -> showSuccessDialog("User Created Successfully", "New Verified User Created",
                        "User '" + username + "' has been created successfully!\n\n" +
                        "The user is now available for authentication with:\n" +
                        "• Username: " + username + "\n" +
                        "• Password: " + password + "\n" +
                        "• Public key authentication enabled\n\n" +
                        "The server's user database has been automatically reloaded.\n" +
                        "You can now use the new user immediately without restarting the server."));
                } catch (Exception e) {
                    Platform.runLater(() -> showError("Failed to create user: " + e.getMessage()));
                }
            });
        });
    }
    
    private void handleDeleteUser() {
        System.out.println("DEBUG: Delete User selected");
        
        try {
            CredentialsManager credentialsManager = new CredentialsManager("config/credentials.properties");
            String[] availableUsers = credentialsManager.getAvailableUsers();
            
            if (availableUsers.length == 0) {
                showError("No users found to delete.");
                return;
            }
            
            Dialog<String> deleteDialog = new Dialog<>();
            deleteDialog.setTitle("Delete SSH User");
            deleteDialog.setHeaderText("Select a user to delete");
            
            ButtonType deleteButtonType = new ButtonType("Delete", ButtonBar.ButtonData.OK_DONE);
            deleteDialog.getDialogPane().getButtonTypes().addAll(deleteButtonType, ButtonType.CANCEL);
            
            VBox content = new VBox(15);
            content.setPadding(new Insets(20));
            
            Label infoLabel = new Label("Select a user to delete from both client and server:");
            infoLabel.setWrapText(true);
            infoLabel.setStyle("-fx-font-size: 12px;");
            
            ComboBox<String> userComboBox = new ComboBox<>();
            userComboBox.getItems().addAll(availableUsers);
            if (availableUsers.length > 0) {
                userComboBox.setValue(availableUsers[0]);
            }
            
            content.getChildren().addAll(infoLabel, userComboBox);
            deleteDialog.getDialogPane().setContent(content);
            deleteDialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            deleteDialog.initOwner(primaryStage);
            
            deleteDialog.setResultConverter(dialogButton -> {
                if (dialogButton == deleteButtonType) {
                    return userComboBox.getValue();
                }
                return null;
            });
            
            Optional<String> result = deleteDialog.showAndWait();
            result.ifPresent(username -> {
                // Show confirmation dialog
                Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
                confirmDialog.setTitle("Confirm Deletion");
                confirmDialog.setHeaderText("Delete User: " + username);
                confirmDialog.setContentText("Are you sure you want to delete user '" + username + "'?\n\n" +
                    "This will:\n" +
                    "• Remove the user from the server database\n" +
                    "• Delete the user's SSH keys\n" +
                    "• Remove the user from client credentials\n" +
                    "• Reload the server's user database\n\n" +
                    "This action cannot be undone!");
                confirmDialog.initOwner(primaryStage);
                
                Optional<ButtonType> confirmResult = confirmDialog.showAndWait();
                if (confirmResult.isPresent() && confirmResult.get() == ButtonType.OK) {
                    showProgressDialog("Deleting User", "Deleting verified user: " + username, () -> {
                        try {
                            ssh.utils.DeleteVerifiedUser.deleteUser(username, client);
                            Platform.runLater(() -> showSuccessDialog("User Deleted Successfully", "User Deleted",
                                "User '" + username + "' has been deleted successfully!\n\n" +
                                "The user has been removed from:\n" +
                                "• Server user database\n" +
                                "• Client credentials\n" +
                                "• SSH key files\n\n" +
                                "The server's user database has been automatically reloaded."));
                        } catch (Exception e) {
                            Platform.runLater(() -> showError("Failed to delete user: " + e.getMessage()));
                        }
                    });
                }
            });
            
        } catch (Exception e) {
            showError("Failed to load users: " + e.getMessage());
        }
    }
    
    private void handleViewUsers() {
        System.out.println("DEBUG: View Users selected");
        
        try {
            CredentialsManager credentialsManager = new CredentialsManager("config/credentials.properties");
            String[] availableUsers = credentialsManager.getAvailableUsers();
            
            Alert viewDialog = new Alert(Alert.AlertType.INFORMATION);
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
    
    private void showProgressDialog(String title, String message, Runnable task) {
        Alert progressAlert = new Alert(Alert.AlertType.INFORMATION);
        progressAlert.setTitle(title);
        progressAlert.setHeaderText(message);
        progressAlert.setContentText("Please wait while the operation is being performed...");
        progressAlert.initOwner(primaryStage);
        progressAlert.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        progressAlert.setResizable(false);
        progressAlert.getDialogPane().setPrefWidth(400);
        progressAlert.getDialogPane().setPrefHeight(150);
        
        new Thread(() -> {
            try {
                task.run();
                Platform.runLater(progressAlert::close);
            } catch (Exception e) {
                Platform.runLater(() -> {
                    progressAlert.close();
                    showError("Operation failed: " + e.getMessage());
                });
            }
        }).start();
        
        progressAlert.showAndWait();
    }
    
    private void showSuccessDialog(String title, String header, String message) {
        Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
        successAlert.setTitle(title);
        successAlert.setHeaderText(header);
        successAlert.setContentText(message);
        successAlert.initOwner(primaryStage);
        successAlert.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        successAlert.setResizable(true);
        successAlert.getDialogPane().setPrefWidth(500);
        successAlert.getDialogPane().setPrefHeight(300);
        successAlert.showAndWait();
    }
    
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText("Operation Failed");
        alert.setContentText(message);
        alert.initOwner(primaryStage);
        alert.showAndWait();
    }
} 