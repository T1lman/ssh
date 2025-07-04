package ssh.client.view;

import ssh.client.model.AuthCredentials;
import ssh.client.model.ServerInfo;
import ssh.utils.ConsoleInterface;
import ssh.utils.CredentialsManager;

import java.io.Console;
import java.util.Scanner;
import java.util.function.Consumer;
import java.lang.Runnable;

/**
 * Console-based implementation of ClientUI.
 */
public class ConsoleClientUI implements ClientUI {
    private Scanner scanner;
    private Console console;
    private boolean running;
    private CredentialsManager credentialsManager;
    private String selectedUser;
    private Consumer<String> onCommandEntered;
    private Runnable onDisconnect;
    
    // Controller reference for business logic
    private ssh.client.controller.SSHClientController controller;

    public ConsoleClientUI() {
        this.scanner = new Scanner(System.in);
        this.console = System.console();
        this.running = true;
        this.credentialsManager = new CredentialsManager("config/credentials.properties");
    }

    public ConsoleClientUI(String credentialsFile) {
        this.scanner = new Scanner(System.in);
        this.console = System.console();
        this.running = true;
        this.credentialsManager = new CredentialsManager(credentialsFile);
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
        ConsoleInterface.prompt(prompt);
        try {
            String input = scanner.nextLine();
            if (input == null) {
                return "";
            }
            return input;
        } catch (Exception e) {
            // If we can't read from stdin, return empty string to use defaults
            ConsoleInterface.info("[using default]");
            return "";
        }
    }

    @Override
    public String getPassword(String prompt) {
        if (console != null) {
            try {
                char[] passwordChars = console.readPassword(prompt + ": ");
                return new String(passwordChars);
            } catch (Exception e) {
                ConsoleInterface.info("[using default]");
                return "";
            }
        } else {
            // Fallback for environments without console (like IDEs)
            ConsoleInterface.prompt(prompt + " (input will be visible)");
            try {
                String input = scanner.nextLine();
                if (input == null) {
                    return "";
                }
                return input;
            } catch (Exception e) {
                ConsoleInterface.info("[using default]");
                return "";
            }
        }
    }

    @Override
    public void showConnectionStatus(boolean connected) {
        String status = connected ? "CONNECTED" : "DISCONNECTED";
        ConsoleInterface.connection(status, "SSH connection");
    }

    @Override
    public void showAuthenticationResult(boolean success, String message) {
        // Use stored selected user for better display
        String username = selectedUser != null ? selectedUser : "unknown";
        ConsoleInterface.auth(username, success);
    }

    @Override
    public void displayShellOutput(String output) {
        ConsoleInterface.shellOutput(output);
    }

    @Override
    public void showFileTransferProgress(String filename, int percentage) {
        ConsoleInterface.info("File transfer: " + filename + " - " + percentage + "%");
    }

    @Override
    public String selectService() {
        ConsoleInterface.info("Available services:");
        ConsoleInterface.info("1. Shell");
        ConsoleInterface.info("2. File Transfer");
        ConsoleInterface.info("3. Port Forwarding");
        ConsoleInterface.info("4. Exit");
        try {
            String choice = getInput("Select service (1-4)");
            if (choice == null || choice.trim().isEmpty()) {
                displayError("No input received, defaulting to shell");
                return "shell";
            }
            switch (choice.trim()) {
                case "1":
                    return "shell";
                case "2":
                    return "file-transfer";
                case "3":
                    promptPortForwarding();
                    return "shell";
                case "4":
                    running = false;
                    return "exit";
                default:
                    displayError("Invalid choice, defaulting to shell");
                    return "shell";
            }
        } catch (Exception e) {
            displayError("Error reading input, defaulting to shell: " + e.getMessage());
            return "shell";
        }
    }

    private void promptPortForwarding() {
        try {
            String type = getInput("Type (local/remote)").trim().toLowerCase();
            boolean isLocal = type.startsWith("l");
            int sourcePort = Integer.parseInt(getInput("Source port"));
            String destHost = getInput("Destination host");
            int destPort = Integer.parseInt(getInput("Destination port"));
            if (controller != null) {
                controller.handlePortForwardRequest(isLocal, sourcePort, destHost, destPort);
                displayMessage((isLocal ? "Local" : "Remote") + " port forward started.");
            }
        } catch (Exception e) {
            displayError("Failed to start port forward: " + e.getMessage());
        }
    }

    @Override
    public ServerInfo getServerInfo() {
        // This method is now handled by getServerInfoFromUser,
        // but needs to exist to satisfy the interface.
        // It should not be called directly.
        return null;
    }

    @Override
    public AuthCredentials getAuthCredentials(String[] availableUsers) {
        selectedUser = selectUserFromList(availableUsers);
        if (selectedUser == null) {
            return null; // User cancelled or invalid selection
        }
        
        // Get credentials from the controller (MVC compliance)
        if (controller != null) {
            return controller.getAuthCredentials(selectedUser);
        } else {
            // Fallback to direct access if controller is not available
            return credentialsManager.getAuthCredentials(selectedUser);
        }
    }

    /**
     * Pure view method - only handles user input for user selection.
     * No business logic, just UI interaction.
     */
    private String selectUserFromList(String[] availableUsers) {
        if (availableUsers == null || availableUsers.length == 0) {
            displayError("No users configured in credentials.properties.");
            return "default";
        }
        
        ConsoleInterface.info("Available users:");
        for (int i = 0; i < availableUsers.length; i++) {
            ConsoleInterface.info((i + 1) + ". " + availableUsers[i]);
        }

        while (true) {
            String choice = getInput("Select user (1-" + availableUsers.length + ") or press Enter for default");
            if (choice.trim().isEmpty()) {
                return "default";
            } else {
                try {
                    int userIndex = Integer.parseInt(choice.trim()) - 1;
                    if (userIndex >= 0 && userIndex < availableUsers.length) {
                        return availableUsers[userIndex];
                    } else {
                        displayError("Invalid user selection.");
                    }
                } catch (NumberFormatException e) {
                    displayError("Invalid input. Please enter a number.");
                }
            }
        }
    }

    @Override
    public void showConnectionProgress(String step) {
        ConsoleInterface.progress(step);
    }

    @Override
    public boolean shouldContinue() {
        return running;
    }

    public void stop() {
        running = false;
        if (scanner != null) {
            scanner.close();
        }
    }

    public void close() {
        stop();
    }

    @Override
    public ServerInfo getServerInfoFromUser() {
        ConsoleInterface.header("Server Connection Details");

        String host = "";
        while (host.trim().isEmpty()) {
            host = getInput("Enter server host: ");
            if (host.trim().isEmpty()) {
                ConsoleInterface.error("Host cannot be empty.");
            }
        }

        int port = -1;
        while (port == -1) {
            try {
                String portStr = getInput("Enter server port: ");
                if (portStr.trim().isEmpty()) {
                    ConsoleInterface.error("Port cannot be empty.");
                } else {
                    port = Integer.parseInt(portStr.trim());
                }
            } catch (NumberFormatException e) {
                ConsoleInterface.error("Invalid port. Please enter a number.");
            }
        }
        
        return new ServerInfo(host, port, null); // Username will be set later
    }

    public void setOnCommandEntered(Consumer<String> onCommandEntered) {
        this.onCommandEntered = onCommandEntered;
    }

    public void setOnDisconnect(Runnable onDisconnect) {
        this.onDisconnect = onDisconnect;
    }

    /**
     * Set the controller for business logic operations.
     */
    public void setController(ssh.client.controller.SSHClientController controller) {
        this.controller = controller;
    }
} 