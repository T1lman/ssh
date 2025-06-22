package ssh.client;

import ssh.client.ui.ConsoleClientUI;
import ssh.utils.ConsoleInterface;
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
            // Create UI with credentials manager
            ConsoleClientUI ui = new ConsoleClientUI();
            ConsoleInterface.progressComplete();
            
            ConsoleInterface.progress("Initializing SSH client");
            // Create and start client
            SSHClient client = new SSHClient(ui);
            ConsoleInterface.progressComplete();
            
            ConsoleInterface.progress("Starting SSH client");
            client.start();
            ConsoleInterface.progressComplete();
            
        } catch (OutOfMemoryError e) {
            ConsoleInterface.error("OutOfMemoryError occurred: " + e.getMessage());
            Logger.error("OutOfMemoryError occurred: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            ConsoleInterface.error("Failed to start SSH client: " + e.getMessage());
            Logger.error("Failed to start SSH client: " + e.getMessage());
            System.exit(1);
        } finally {
            Logger.close();
        }
    }
}  