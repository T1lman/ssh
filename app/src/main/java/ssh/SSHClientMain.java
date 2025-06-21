package ssh;

import ssh.client.SSHClient;
import ssh.client.ui.ConsoleClientUI;
import ssh.utils.Logger;

/**
 * Main class for SSH Client application.
 */
public class SSHClientMain {
    public static void main(String[] args) {
        Logger.info("SSH ClientMain main() starting...");
        Logger.info("SSH Client Starting...");
        
        try {
            Logger.info("Creating ConsoleClientUI...");
            // Create UI with credentials manager
            ConsoleClientUI ui = new ConsoleClientUI();
            
            Logger.info("Creating SSHClient...");
            // Create and start client
            SSHClient client = new SSHClient(ui);
            
            Logger.info("Starting SSHClient...");
            client.start();
            
        } catch (OutOfMemoryError e) {
            Logger.error("OutOfMemoryError occurred: " + e.getMessage());
            System.err.println("OutOfMemoryError: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } catch (Exception e) {
            Logger.error("Failed to start SSH client: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
} 