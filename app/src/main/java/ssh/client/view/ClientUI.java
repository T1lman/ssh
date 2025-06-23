package ssh.client.view;

import ssh.client.model.ServerInfo;
import ssh.client.model.AuthCredentials;

import java.util.List;

/**
 * Interface for client user interface implementations.
 */
public interface ClientUI {
    /**
     * Display a message to the user.
     */
    void displayMessage(String message);

    /**
     * Display an error message to the user.
     */
    void displayError(String error);

    /**
     * Get input from the user.
     */
    String getInput(String prompt);

    /**
     * Get password input from the user (should not echo).
     */
    String getPassword(String prompt);

    /**
     * Show connection status.
     */
    void showConnectionStatus(boolean connected);

    /**
     * Show authentication result.
     */
    void showAuthenticationResult(boolean success, String message);

    /**
     * Display shell output.
     */
    void displayShellOutput(String output);

    /**
     * Show file transfer progress.
     */
    void showFileTransferProgress(String filename, int percentage);

    /**
     * Select a service (shell or file-transfer).
     */
    String selectService();

    /**
     * Get server connection information.
     */
    ServerInfo getServerInfo();

    /**
     * Get authentication credentials.
     * @param availableUsers An array of usernames for the user to select from.
     * @return The credentials chosen by the user, or null if the action is cancelled.
     */
    AuthCredentials getAuthCredentials(String[] availableUsers);

    /**
     * Show connection progress.
     */
    void showConnectionProgress(String step);

    /**
     * Check if the client should continue running.
     */
    boolean shouldContinue();

    /**
     * Get server connection details from the user.
     * @return A ServerInfo object containing host and port.
     */
    ServerInfo getServerInfoFromUser();
} 