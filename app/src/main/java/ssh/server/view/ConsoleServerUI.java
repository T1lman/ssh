package ssh.server.view;

import ssh.model.utils.ConsoleInterface;

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
        ConsoleInterface.info(message);
    }

    @Override
    public void displayError(String error) {
        ConsoleInterface.error(error);
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
        ConsoleInterface.status(status);
    }

    @Override
    public void showConnectionInfo(String clientAddress, int clientPort) {
        ConsoleInterface.connection("CONNECTED", clientAddress + ":" + clientPort);
    }

    @Override
    public void showAuthenticationResult(String username, boolean success, String message) {
        ConsoleInterface.auth(username, success);
    }

    @Override
    public void showServiceRequest(String username, String serviceType) {
        ConsoleInterface.info("Service request from " + username + " for " + serviceType);
    }

    @Override
    public void showFileTransferProgress(String filename, long bytesTransferred, long totalBytes) {
        int percentage = (int) ((bytesTransferred * 100) / totalBytes);
        ConsoleInterface.info("File transfer: " + filename + " - " + percentage + "% (" + 
                          bytesTransferred + "/" + totalBytes + " bytes)");
    }

    @Override
    public void showShellCommand(String username, String command) {
        ConsoleInterface.shell(username, command);
    }

    @Override
    public void showServerStartup(int port, String host) {
        ConsoleInterface.header("SSH Server Starting");
        ConsoleInterface.info("Host: " + host);
        ConsoleInterface.info("Port: " + port);
    }

    @Override
    public void showServerShutdown() {
        ConsoleInterface.footer("SSH Server Shutting Down");
    }

    @Override
    public boolean shouldContinue() {
        return running;
    }

    @Override
    public ServerConfig getConfigFromUser() {
        ConsoleInterface.header("Server Configuration");
        ServerConfig config = new ServerConfig();

        String host = getInput("Enter server host (default: localhost)");
        if (host.trim().isEmpty()) {
            host = "localhost";
        }
        config.setHost(host);

        int port = -1;
        while (port == -1) {
            try {
                String portStr = getInput("Enter server port (default: 2222)");
                if (portStr.trim().isEmpty()) {
                    port = 2222;
                } else {
                    port = Integer.parseInt(portStr.trim());
                }
            } catch (NumberFormatException e) {
                ConsoleInterface.error("Invalid port. Please enter a number.");
            }
        }
        config.setPort(port);
        
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