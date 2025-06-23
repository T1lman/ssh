package ssh.client;

import ssh.client.controller.SSHClient;
import ssh.client.model.AuthCredentials;
import ssh.client.model.ServerInfo;
import ssh.client.model.ClientConnection;
import ssh.client.view.ConsoleClientUI;
import ssh.client.view.ClientUI;
import ssh.model.utils.Logger;

public class SSHClientMain {
    public static void main(String[] args) {
        Logger.initialize("logs/client.log");
        try {
            // Create the console UI
            ClientUI ui = new ConsoleClientUI();
            
            // Create the controller with just the UI - it will handle getting server info and credentials
            SSHClient client = new SSHClient(ui);
            
            // Start the client
            client.start();
        } catch (Exception e) {
            Logger.error("Failed to start console client: " + e.getMessage());
            e.printStackTrace();
        } finally {
            Logger.close();
        }
    }
} 