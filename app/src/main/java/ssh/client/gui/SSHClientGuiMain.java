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
            
            // Set up stage close handler to prevent premature exit
            primaryStage.setOnCloseRequest(event -> {
                Logger.info("Main window closing");
                if (gui.getClient() != null) {
                    gui.getClient().stop();
                }
                Logger.close();
            });
            
            // The startup scene will handle getting server info and username
            // The connection will be initiated when the user clicks login in the startup scene
            
        } catch (Exception e) {
            Logger.error("Failed to start GUI application: " + e.getMessage());
            Platform.exit();
        }
    }

    @Override
    public void stop() {
        Logger.info("GUI application stopping");
        Logger.close();
    }
} 