package ssh.client;

import ssh.client.ui.AuthCredentials;
import ssh.client.ui.ClientUI;
import ssh.client.ui.ConsoleClientUI;
import ssh.client.ui.ServerInfo;
import ssh.utils.ConsoleInterface;
import ssh.utils.CredentialsManager;
import ssh.utils.Logger;

/**
 * Main class for SSH Client application.
 */
public class SSHClientMain {
    public static void main(String[] args) {
        // Initialize logger
        Logger.initialize("logs/client.log");
        
        ConsoleInterface.header("SSH Client Starting");
        ConsoleInterface.info("Log file: " + Logger.getLogFile());
        
        try {
            ConsoleInterface.progress("Creating client interface");
            ConsoleClientUI ui = new ConsoleClientUI();
            ConsoleInterface.progressComplete();
            
            ConsoleInterface.progress("Initializing SSH client");
            CredentialsManager credentialsManager = new CredentialsManager("config/credentials.properties");
            
            // Get Server Info from user
            ServerInfo serverInfo = ui.getServerInfoFromUser();
            
            // Get Auth Credentials from user
            AuthCredentials authCredentials = ui.getAuthCredentials(credentialsManager);
            
            // Set username in serverInfo
            if (authCredentials != null) {
                serverInfo.setUsername(authCredentials.getUsername());
            }

            SSHClient client = new SSHClient(serverInfo, authCredentials, ui);
            ConsoleInterface.progressComplete();
            
            ConsoleInterface.progress("Starting SSH client");
            client.start();
            ConsoleInterface.progressComplete();
            
        } catch (OutOfMemoryError e) {
            ConsoleInterface.error("OutOfMemoryError occurred: " + e.getMessage());
            Logger.error("OOM error", e);
            System.exit(1);
        } catch (Exception e) {
            ConsoleInterface.error("Failed to start SSH client: " + e.getMessage());
            Logger.error("Failed to start client", e);
            System.exit(1);
        } finally {
            Logger.close();
        }
    }
}  