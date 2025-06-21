package ssh.server.ui;

import java.io.Console;
import java.util.Scanner;

/**
 * Console-based implementation of ServerUI.
 */
public class ConsoleServerUI implements ServerUI {
    private Scanner scanner;
    private Console console;
    private boolean running;

    public ConsoleServerUI() {
        this.scanner = new Scanner(System.in);
        this.console = System.console();
        this.running = true;
    }

    @Override
    public void displayMessage(String message) {
        System.out.println("[INFO] " + message);
    }

    @Override
    public void displayError(String error) {
        System.err.println("[ERROR] " + error);
    }

    @Override
    public String getInput(String prompt) {
        System.out.print(prompt + ": ");
        return scanner.nextLine();
    }

    @Override
    public String getPassword(String prompt) {
        if (console != null) {
            char[] passwordChars = console.readPassword(prompt + ": ");
            return new String(passwordChars);
        } else {
            // Fallback for environments without console (like IDEs)
            System.out.print(prompt + " (input will be visible): ");
            return scanner.nextLine();
        }
    }

    @Override
    public void showServerStatus(String status) {
        System.out.println("[STATUS] " + status);
    }

    @Override
    public void showConnectionInfo(String clientAddress, int clientPort) {
        System.out.println("[CONNECTION] Client connected from " + clientAddress + ":" + clientPort);
    }

    @Override
    public void showAuthenticationResult(String username, boolean success, String message) {
        String status = success ? "SUCCESS" : "FAILED";
        System.out.println("[AUTH] " + username + " - " + status + ": " + message);
    }

    @Override
    public void showServiceRequest(String username, String serviceType) {
        System.out.println("[SERVICE] " + username + " requested " + serviceType + " service");
    }

    @Override
    public void showFileTransferProgress(String filename, long bytesTransferred, long totalBytes) {
        int percentage = (int) ((bytesTransferred * 100) / totalBytes);
        System.out.println("[FILE] " + filename + " - " + percentage + "% (" + 
                          bytesTransferred + "/" + totalBytes + " bytes)");
    }

    @Override
    public void showShellCommand(String username, String command) {
        System.out.println("[SHELL] " + username + " executed: " + command);
    }

    @Override
    public void showServerStartup(int port, String host) {
        System.out.println("========================================");
        System.out.println("SSH Server Starting...");
        System.out.println("Host: " + host);
        System.out.println("Port: " + port);
        System.out.println("========================================");
    }

    @Override
    public void showServerShutdown() {
        System.out.println("========================================");
        System.out.println("SSH Server Shutting Down...");
        System.out.println("========================================");
    }

    @Override
    public boolean shouldContinue() {
        return running;
    }

    @Override
    public ServerConfig getServerConfig() {
        ServerConfig config = new ServerConfig();
        
        System.out.println("SSH Server Configuration");
        System.out.println("========================");
        
        // Get port
        String portStr = getInput("Enter port number (default: 2222)");
        if (!portStr.trim().isEmpty()) {
            try {
                config.setPort(Integer.parseInt(portStr.trim()));
            } catch (NumberFormatException e) {
                displayError("Invalid port number, using default: 2222");
            }
        }
        
        // Get host
        String host = getInput("Enter host (default: localhost)");
        if (!host.trim().isEmpty()) {
            config.setHost(host.trim());
        }
        
        // Get key directory
        String keyDir = getInput("Enter key directory (default: data/server/server_keys)");
        if (!keyDir.trim().isEmpty()) {
            config.setKeyDirectory(keyDir.trim());
        }
        
        // Get users file
        String usersFile = getInput("Enter users file (default: data/server/users.properties)");
        if (!usersFile.trim().isEmpty()) {
            config.setUsersFile(usersFile.trim());
        }
        
        // Get authorized keys directory
        String authKeysDir = getInput("Enter authorized keys directory (default: data/server/authorized_keys)");
        if (!authKeysDir.trim().isEmpty()) {
            config.setAuthorizedKeysDir(authKeysDir.trim());
        }
        
        // Get max connections
        String maxConnStr = getInput("Enter max connections (default: 10)");
        if (!maxConnStr.trim().isEmpty()) {
            try {
                config.setMaxConnections(Integer.parseInt(maxConnStr.trim()));
            } catch (NumberFormatException e) {
                displayError("Invalid max connections, using default: 10");
            }
        }
        
        // Get session timeout
        String timeoutStr = getInput("Enter session timeout in minutes (default: 30)");
        if (!timeoutStr.trim().isEmpty()) {
            try {
                int minutes = Integer.parseInt(timeoutStr.trim());
                config.setSessionTimeout(minutes * 60 * 1000L); // Convert to milliseconds
            } catch (NumberFormatException e) {
                displayError("Invalid timeout, using default: 30 minutes");
            }
        }
        
        // Get log level
        String logLevel = getInput("Enter log level (default: INFO)");
        if (!logLevel.trim().isEmpty()) {
            config.setLogLevel(logLevel.trim().toUpperCase());
        }
        
        return config;
    }

    /**
     * Stop the server.
     */
    public void stop() {
        this.running = false;
    }

    /**
     * Close resources.
     */
    public void close() {
        if (scanner != null) {
            scanner.close();
        }
    }
} 