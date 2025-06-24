package ssh.client;

import ssh.client.controller.SSHClientController;
import ssh.client.view.ConsoleClientUI;
import ssh.utils.Logger;
import ssh.client.view.ClientUI;

public class SSHClientMain {
    public static void main(String[] args) {
        Logger.initialize("logs/client.log");
        try {
            // Create the console UI
            ConsoleClientUI ui = new ConsoleClientUI();
            
            // Create the MVC controller
            SSHClientController controller = new SSHClientController(ui);
            
            // Set the controller in the UI for business logic operations
            ui.setController(controller);
            
            // Start the client
            controller.start();
        } catch (Exception e) {
            Logger.error("Failed to start console client: " + e.getMessage());
            e.printStackTrace();
        } finally {
            Logger.close();
        }
    }
} 