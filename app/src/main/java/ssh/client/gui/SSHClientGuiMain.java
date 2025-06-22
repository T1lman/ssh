package ssh.client.gui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import ssh.client.SSHClient;
import ssh.client.ui.AuthCredentials;
import ssh.client.ui.ClientUI;
import ssh.client.ui.ServerInfo;
import ssh.utils.CredentialsManager;
import ssh.utils.Logger;

public class SSHClientGuiMain extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        Logger.initialize("logs/client_gui.log");

        try {
            // Create the GUI implementation of the UI
            JavaFXClientUI gui = new JavaFXClientUI(primaryStage);
            
            // Show the initial stage immediately to keep the application alive
            System.out.println("DEBUG: Showing initial stage to keep application alive");
            primaryStage.show();
            
            // Get server information from user
            ServerInfo serverInfo = gui.getServerInfoFromUser();
            if (serverInfo == null) {
                // User cancelled the dialog
                Logger.info("User cancelled server info dialog");
                Platform.exit();
                return;
            }

            // Create SSH client with null credentials - they will be requested in start()
            SSHClient client = new SSHClient(serverInfo, null, gui);
            gui.setClient(client);
            
            // Set up stage close handler to prevent premature exit
            primaryStage.setOnCloseRequest(event -> {
                Logger.info("Main window closing");
                if (client != null) {
                    client.stop();
                }
                Logger.close();
            });
            
            // Running the client logic in a separate thread to not block the UI
            Thread clientThread = new Thread(() -> {
                try {
                    System.out.println("DEBUG: Starting SSH client in background thread");
                    Logger.info("Starting SSH client in background thread");
                    client.start();
                } catch (Exception e) {
                    System.out.println("DEBUG: SSH client failed with exception: " + e.getMessage());
                    e.printStackTrace();
                    Logger.error("SSH client failed", e);
                    
                    // Show error on JavaFX thread and keep application alive
                    Platform.runLater(() -> {
                        gui.displayError("SSH client failed: " + e.getMessage());
                        // Don't exit here - let user decide when to close
                    });
                }
            });
            clientThread.setDaemon(false); // Keep the thread alive
            clientThread.start();

        } catch (Exception e) {
            Logger.error("Failed to start GUI client", e);
            System.out.println("DEBUG: Failed to start GUI client: " + e.getMessage());
            e.printStackTrace();
            Platform.exit();
        }
    }
    
    @Override
    public void stop() {
        Logger.info("GUI application stopping");
        Logger.close();
    }
} 