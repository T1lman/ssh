package ssh.client.gui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import ssh.client.ClientConnection;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ButtonBar.ButtonData;

import java.io.File;

/**
 * Handles file transfer operations with a reusable dialog.
 * Manages upload and download operations with progress tracking.
 */
public class FileTransferDialog {
    private final Stage primaryStage;
    private final ClientConnection connection;
    
    // Reusable dialog and content
    private Dialog<Void> reusableDialog;
    private VBox reusableContent;
    private ProgressBar activeProgressBar;
    private Label activeStatusLabel;
    
    public FileTransferDialog(Stage primaryStage, ClientConnection connection) {
        this.primaryStage = primaryStage;
        this.connection = connection;
        initializeReusableDialog();
    }
    
    private void initializeReusableDialog() {
        reusableDialog = new Dialog<>();
        reusableDialog.setTitle("File Transfer");
        reusableDialog.getDialogPane().getButtonTypes().clear();
        reusableContent = new VBox(15);
        reusableContent.setPadding(new Insets(20));
        reusableDialog.getDialogPane().setContent(reusableContent);
        reusableDialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        reusableDialog.initOwner(primaryStage);
        reusableDialog.setResizable(false);
        reusableDialog.getDialogPane().setPrefWidth(450);
        reusableDialog.getDialogPane().setPrefHeight(200);
    }
    
    public void show() {
        // Step 1: Operation selection
        Dialog<ButtonType> dialog = createOperationDialog();
        ButtonType opResult = dialog.showAndWait().orElse(ButtonType.CANCEL);
        if (opResult == null || opResult.getButtonData() == ButtonBar.ButtonData.CANCEL_CLOSE) return;
        if (opResult.getText().equals("Upload File")) {
            handleFileUpload(dialog);
        } else if (opResult.getText().equals("Download File")) {
            handleFileDownload(dialog);
        }
    }
    
    private Dialog<ButtonType> createOperationDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("File Transfer");
        dialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        dialog.initOwner(primaryStage);
        dialog.setResizable(false);
        dialog.getDialogPane().setPrefWidth(450);
        dialog.getDialogPane().setPrefHeight(200);
        dialog.setHeaderText("Choose a file transfer operation");
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        Label infoLabel = new Label("Select a file transfer operation:");
        infoLabel.setWrapText(true);
        infoLabel.setStyle("-fx-font-size: 12px;");
        content.getChildren().add(infoLabel);
        dialog.getDialogPane().setContent(content);
        ButtonType uploadButtonType = new ButtonType("Upload File", ButtonBar.ButtonData.LEFT);
        ButtonType downloadButtonType = new ButtonType("Download File", ButtonBar.ButtonData.OTHER);
        ButtonType cancelButtonType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(uploadButtonType, downloadButtonType, cancelButtonType);
        return dialog;
    }
    
    private void handleFileUpload(Dialog<ButtonType> dialog) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select File to Upload");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("All Files", "*.*"),
            new FileChooser.ExtensionFilter("Text Files", "*.txt", "*.log", "*.md"),
            new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif"),
            new FileChooser.ExtensionFilter("Document Files", "*.pdf", "*.doc", "*.docx")
        );
        File selectedFile = fileChooser.showOpenDialog(primaryStage);
        if (selectedFile == null) return;
        // Step 2: Remote path input
        updateDialogForUploadPath(dialog, selectedFile);
        ButtonType pathResult = dialog.showAndWait().orElse(ButtonType.CANCEL);
        if (pathResult == null || pathResult.getButtonData() == ButtonBar.ButtonData.CANCEL_CLOSE) return;
        if (pathResult.getText().equals("Back")) {
            show(); // restart flow
            return;
        }
        TextField remotePathField = (TextField) ((GridPane)((VBox)dialog.getDialogPane().getContent()).getChildren().get(0)).getChildren().get(1);
        String remotePath = remotePathField.getText().trim();
        performFileUpload(dialog, selectedFile, remotePath);
    }
    
    private void updateDialogForUploadPath(Dialog<ButtonType> dialog, File selectedFile) {
        dialog.setTitle("File Transfer");
        dialog.setHeaderText("Upload: " + selectedFile.getName());
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));
        TextField remotePathField = new TextField();
        remotePathField.setPromptText("Remote filename (optional, defaults to original name)");
        remotePathField.setText(selectedFile.getName());
        Label infoLabel = new Label("File: " + selectedFile.getName() + " (" + UIUtils.formatFileSize(selectedFile.length()) + ")");
        infoLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");
        grid.add(new Label("Remote Path:"), 0, 0);
        grid.add(remotePathField, 1, 0);
        grid.add(infoLabel, 0, 1, 2, 1);
        content.getChildren().add(grid);
        dialog.getDialogPane().setContent(content);
        Platform.runLater(remotePathField::requestFocus);
        ButtonType uploadButtonType = new ButtonType("Upload", ButtonBar.ButtonData.OK_DONE);
        ButtonType backButtonType = new ButtonType("Back", ButtonBar.ButtonData.OTHER);
        ButtonType cancelButtonType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().setAll(uploadButtonType, backButtonType, cancelButtonType);
    }
    
    private void performFileUpload(Dialog<ButtonType> dialog, File selectedFile, String remotePath) {
        // Update dialog for progress
        updateDialogForProgress(dialog, "Uploading File", selectedFile.getName());
        // Remove all buttons while transferring
        dialog.getDialogPane().getButtonTypes().clear();
        new Thread(() -> {
            try {
                String finalRemotePath = remotePath.isEmpty() ? selectedFile.getName() : remotePath;
                connection.uploadFile(selectedFile.getAbsolutePath(), finalRemotePath);
                Platform.runLater(() -> {
                    appendResultToDialog(dialog, true, "File '" + selectedFile.getName() + "' has been uploaded successfully!\n\n" +
                        "Remote path: " + finalRemotePath + "\n" +
                        "File size: " + UIUtils.formatFileSize(selectedFile.length()));
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    appendResultToDialog(dialog, false, "Upload failed: " + e.getMessage());
                });
            }
        }).start();
        dialog.showAndWait(); // Wait for user to acknowledge result
    }
    
    private void updateDialogForProgress(Dialog<ButtonType> dialog, String title, String filename) {
        dialog.setTitle(title);
        dialog.setHeaderText("Transferring: " + filename);
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        ProgressBar progressBar = new ProgressBar();
        progressBar.setPrefWidth(300);
        progressBar.setProgress(0.0);
        Label statusLabel = new Label("Preparing transfer...");
        statusLabel.setStyle("-fx-font-size: 12px;");
        content.getChildren().addAll(progressBar, statusLabel);
        dialog.getDialogPane().setContent(content);
        this.activeProgressBar = progressBar;
        this.activeStatusLabel = statusLabel;
    }
    
    private void appendResultToDialog(Dialog<ButtonType> dialog, boolean success, String message) {
        VBox content = (VBox) dialog.getDialogPane().getContent();
        Label resultLabel = new Label(message);
        resultLabel.setWrapText(true);
        resultLabel.setStyle("-fx-font-size: 12px; " + (success ? "-fx-text-fill: #27ae60;" : "-fx-text-fill: #e74c3c;"));
        content.getChildren().add(resultLabel);
        ButtonType okButtonType = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(okButtonType);
    }
    
    private void handleFileDownload(Dialog<ButtonType> dialog) {
        updateDialogForDownloadPath(dialog);
        ButtonType pathResult = dialog.showAndWait().orElse(ButtonType.CANCEL);
        if (pathResult == null || pathResult.getButtonData() == ButtonBar.ButtonData.CANCEL_CLOSE) return;
        if (pathResult.getText().equals("Back")) {
            show(); // restart flow
            return;
        }
        TextField remotePathField = (TextField) ((GridPane)((VBox)dialog.getDialogPane().getContent()).getChildren().get(0)).getChildren().get(1);
        String remotePath = remotePathField.getText().trim();
        if (remotePath.isEmpty()) {
            appendResultToDialog(dialog, false, "Please enter a remote file path.");
            handleFileDownload(dialog);
            return;
        }
        performFileDownload(dialog, remotePath);
    }
    
    private void updateDialogForDownloadPath(Dialog<ButtonType> dialog) {
        dialog.setTitle("File Transfer");
        dialog.setHeaderText("Download File");
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));
        TextField remotePathField = new TextField();
        remotePathField.setPromptText("Remote file path (e.g., /path/to/file.txt)");
        grid.add(new Label("Remote Path:"), 0, 0);
        grid.add(remotePathField, 1, 0);
        content.getChildren().add(grid);
        dialog.getDialogPane().setContent(content);
        Platform.runLater(remotePathField::requestFocus);
        ButtonType downloadButtonType = new ButtonType("Download", ButtonBar.ButtonData.OK_DONE);
        ButtonType backButtonType = new ButtonType("Back", ButtonBar.ButtonData.OTHER);
        ButtonType cancelButtonType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().setAll(downloadButtonType, backButtonType, cancelButtonType);
    }
    
    private void performFileDownload(Dialog<ButtonType> dialog, String remotePath) {
        String localPath = "test_downloads/" + new File(remotePath).getName();
        updateDialogForProgress(dialog, "Downloading File", remotePath);
        dialog.getDialogPane().getButtonTypes().clear();
        new Thread(() -> {
            try {
                connection.downloadFile(remotePath, localPath);
                Platform.runLater(() -> {
                    appendResultToDialog(dialog, true, "File '" + remotePath + "' has been downloaded successfully!\n\n" +
                        "Local path: " + localPath);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    appendResultToDialog(dialog, false, "Download failed: " + e.getMessage());
                });
            }
        }).start();
        dialog.showAndWait();
    }
    
    public void updateProgress(String filename, int percentage) {
        if (activeProgressBar != null && activeStatusLabel != null) {
            Platform.runLater(() -> {
                activeProgressBar.setProgress(percentage / 100.0);
                activeStatusLabel.setText("Transferring " + filename + ": " + percentage + "%");
            });
        }
    }
} 