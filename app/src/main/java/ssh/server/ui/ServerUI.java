package ssh.server.ui;

import ssh.server.ui.ServerConfig;

/**
 * Interface for server user interface implementations.
 */
public interface ServerUI {
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
     * Show server status information.
     */
    void showServerStatus(String status);

    /**
     * Show connection information.
     */
    void showConnectionInfo(String clientAddress, int clientPort);

    /**
     * Show authentication result.
     */
    void showAuthenticationResult(String username, boolean success, String message);

    /**
     * Show service request information.
     */
    void showServiceRequest(String username, String serviceType);

    /**
     * Show file transfer progress.
     */
    void showFileTransferProgress(String filename, long bytesTransferred, long totalBytes);

    /**
     * Show shell command execution.
     */
    void showShellCommand(String username, String command);

    /**
     * Show server startup information.
     */
    void showServerStartup(int port, String host);

    /**
     * Show server shutdown information.
     */
    void showServerShutdown();

    /**
     * Check if the server should continue running.
     */
    boolean shouldContinue();

    /**
     * Get server configuration details from the user.
     * This can be implemented with a terminal prompt or a GUI dialog.
     * @return A ServerConfig object with the user-provided details.
     */
    ServerConfig getConfigFromUser();
} 