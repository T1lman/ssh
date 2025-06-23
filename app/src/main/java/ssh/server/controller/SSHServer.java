package ssh.server.controller;

import ssh.server.view.ConsoleServerUI;
import ssh.server.view.ServerUI;
import ssh.model.utils.Logger;

/**
 * Main entry point for the SSH server application.
 * This class serves as a pure entry point and delegates all functionality
 * to the proper MVC architecture (SSHServerController and SSHServerModel).
 */
public class SSHServer {

    /**
     * Main method - entry point for the SSH server.
     */
    public static void main(String[] args) {
        // Initialize logger
        Logger.initialize("logs/server.log");
        
        // Create UI
        ServerUI ui = new ConsoleServerUI();
        
        // Create MVC controller
        SSHServerController controller = new SSHServerController(ui);
        
        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Logger.info("Shutdown signal received");
            controller.stop();
            Logger.close();
        }));
        
        // Show startup header
        Logger.info("SSH Server Starting");
        Logger.info("Log file: " + Logger.getLogFile());
        
        // Start the server using the MVC architecture
        controller.start();
    }
} 