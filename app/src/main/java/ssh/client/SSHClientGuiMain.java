package ssh.client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import ssh.client.view.JavaFXClientUI;
import ssh.utils.CredentialsManager;
import ssh.utils.Logger;
import ssh.client.controller.SSHClientController;

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
                // The controller is managed internally by JavaFXClientUI
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