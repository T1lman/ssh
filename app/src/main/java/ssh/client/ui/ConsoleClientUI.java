package ssh.client.ui;

import ssh.utils.CredentialsManager;

import java.io.Console;
import java.util.Scanner;

/**
 * Console-based implementation of ClientUI.
 */
public class ConsoleClientUI implements ClientUI {
    private Scanner scanner;
    private Console console;
    private boolean running;
    private CredentialsManager credentialsManager;
    private boolean interactive;

    public ConsoleClientUI() {
        this.scanner = new Scanner(System.in);
        this.console = System.console();
        this.running = true;
        this.credentialsManager = new CredentialsManager();
        this.interactive = checkInteractive();
    }

    public ConsoleClientUI(String credentialsFile) {
        this.scanner = new Scanner(System.in);
        this.console = System.console();
        this.running = true;
        this.credentialsManager = new CredentialsManager(credentialsFile);
        this.interactive = checkInteractive();
    }

    /**
     * Check if the environment is interactive.
     */
    private boolean checkInteractive() {
        if (console != null) {
            return true;
        }
        // For non-console environments, assume interactive unless we can't read from stdin
        try {
            // Just check if stdin is available, don't read from it
            return System.in.available() >= 0;
        } catch (Exception e) {
            return false;
        }
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
        if (!interactive) {
            // In non-interactive mode, return empty string to use defaults
            System.out.println(prompt + ": [using default]");
            return "";
        }
        
        System.out.print(prompt + ": ");
        try {
            return scanner.nextLine();
        } catch (Exception e) {
            // If we can't read from stdin, return empty string to use defaults
            System.out.println("[using default]");
            return "";
        }
    }

    @Override
    public String getPassword(String prompt) {
        if (!interactive) {
            // In non-interactive mode, return empty string to use defaults
            System.out.println(prompt + ": [using default]");
            return "";
        }
        
        if (console != null) {
            char[] passwordChars = console.readPassword(prompt + ": ");
            return new String(passwordChars);
        } else {
            // Fallback for environments without console (like IDEs)
            System.out.print(prompt + " (input will be visible): ");
            try {
                return scanner.nextLine();
            } catch (Exception e) {
                System.out.println("[using default]");
                return "";
            }
        }
    }

    @Override
    public void showConnectionStatus(boolean connected) {
        String status = connected ? "CONNECTED" : "DISCONNECTED";
        System.out.println("[CONNECTION] " + status);
    }

    @Override
    public void showAuthenticationResult(boolean success, String message) {
        String status = success ? "SUCCESS" : "FAILED";
        System.out.println("[AUTH] " + status + ": " + message);
    }

    @Override
    public void displayShellOutput(String output) {
        System.out.println("[SHELL] " + output);
    }

    @Override
    public void showFileTransferProgress(String filename, int percentage) {
        System.out.println("[FILE] " + filename + " - " + percentage + "%");
    }

    @Override
    public String selectService() {
        if (!interactive) {
            // In non-interactive mode, default to shell
            System.out.println("Available services:");
            System.out.println("1. Shell");
            System.out.println("2. File Transfer");
            System.out.println("3. Exit");
            System.out.println("Select service (1-3): [using default: shell]");
            return "shell";
        }
        
        System.out.println("\nAvailable services:");
        System.out.println("1. Shell");
        System.out.println("2. File Transfer");
        System.out.println("3. Exit");
        
        String choice = getInput("Select service (1-3)");
        
        switch (choice.trim()) {
            case "1":
                return "shell";
            case "2":
                return "file-transfer";
            case "3":
                running = false;
                return "exit";
            default:
                displayError("Invalid choice, defaulting to shell");
                return "shell";
        }
    }

    @Override
    public ServerInfo getServerInfo() {
        // Check if user wants to select a different user
        String[] availableUsers = credentialsManager.getAvailableUsers();
        
        if (availableUsers.length > 1 && interactive) {
            System.out.println("\nAvailable users:");
            for (int i = 0; i < availableUsers.length; i++) {
                System.out.println((i + 1) + ". " + availableUsers[i]);
            }
            
            String choice = getInput("Select user (1-" + availableUsers.length + ") or press Enter for default");
            
            if (!choice.trim().isEmpty()) {
                try {
                    int userIndex = Integer.parseInt(choice.trim()) - 1;
                    if (userIndex >= 0 && userIndex < availableUsers.length) {
                        return credentialsManager.getServerInfo(availableUsers[userIndex]);
                    }
                } catch (NumberFormatException e) {
                    displayError("Invalid user selection, using default");
                }
            }
        } else if (availableUsers.length > 1 && !interactive) {
            System.out.println("\nAvailable users:");
            for (int i = 0; i < availableUsers.length; i++) {
                System.out.println((i + 1) + ". " + availableUsers[i]);
            }
            System.out.println("Select user (1-" + availableUsers.length + "): [using default]");
        }
        
        // Return default server info from credentials file
        return credentialsManager.getServerInfo();
    }

    @Override
    public AuthCredentials getAuthCredentials() {
        // Get the username from server info to determine which credentials to use
        ServerInfo serverInfo = credentialsManager.getServerInfo();
        String username = serverInfo.getUsername();
        
        // Find the user key that matches this username
        String userKey = "default";
        String[] availableUsers = credentialsManager.getAvailableUsers();
        
        for (String user : availableUsers) {
            if (username.equals(credentialsManager.getAuthCredentials(user).getUsername())) {
                userKey = user;
                break;
            }
        }
        
        return credentialsManager.getAuthCredentials(userKey);
    }

    @Override
    public void showConnectionProgress(String step) {
        System.out.println("[CONNECTING] " + step);
    }

    @Override
    public boolean shouldContinue() {
        return running;
    }

    /**
     * Stop the client.
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