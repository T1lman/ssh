package ssh.server.controller;

import ssh.server.model.SSHServerModel;
import ssh.server.view.ServerUI;
import ssh.utils.Logger;
import ssh.config.ServerConfig;

/**
 * True MVC Controller for SSH Server - only coordinates between Model and View.
 * Contains no business logic, only orchestration.
 */
public class SSHServerController {
    private final SSHServerModel model;
    private final ServerUI view;
    private boolean running = true;

    public SSHServerController(ServerUI view) {
        this.view = view;
        this.model = new SSHServerModel();
        setupEventHandlers();
    }

    /**
     * Setup event handlers to properly mediate between view and model.
     */
    private void setupEventHandlers() {
        // Model events -> View updates
        model.setOnServerStatusChanged(view::showServerStatus);
        model.setOnConnectionInfo(this::handleConnectionInfo);
        model.setOnAuthenticationResult(this::handleAuthenticationResult);
        model.setOnServiceRequest(this::handleServiceRequest);
        model.setOnFileTransferProgress(this::handleFileTransferProgress);
        model.setOnShellCommand(this::handleShellCommand);
        model.setOnError(view::displayError);
        model.setOnMessage(view::displayMessage);
    }

    /**
     * Handle connection info from model and convert to view format.
     */
    private void handleConnectionInfo(String connectionInfo) {
        // Format: "clientAddress:clientPort"
        String[] parts = connectionInfo.split(":");
        if (parts.length >= 2) {
            String clientAddress = parts[0];
            try {
                int clientPort = Integer.parseInt(parts[1]);
                view.showConnectionInfo(clientAddress, clientPort);
            } catch (NumberFormatException e) {
                view.showConnectionInfo(connectionInfo, 0);
            }
        } else {
            view.showConnectionInfo(connectionInfo, 0);
        }
    }

    /**
     * Handle authentication result from model and convert to view format.
     */
    private void handleAuthenticationResult(String result) {
        // Format: "username - SUCCESS/FAILED: message"
        String[] parts = result.split(" - ");
        if (parts.length >= 2) {
            String username = parts[0];
            String statusPart = parts[1];
            boolean success = statusPart.startsWith("SUCCESS");
            String message = statusPart.substring(statusPart.indexOf(":") + 1).trim();
            view.showAuthenticationResult(username, success, message);
        } else {
            view.showAuthenticationResult("unknown", false, result);
        }
    }

    /**
     * Handle service request from model and convert to view format.
     */
    private void handleServiceRequest(String result) {
        // Format: "username requested serviceType"
        String[] parts = result.split(" requested ");
        if (parts.length >= 2) {
            String username = parts[0];
            String serviceType = parts[1];
            view.showServiceRequest(username, serviceType);
        } else {
            view.showServiceRequest("unknown", result);
        }
    }

    /**
     * Handle file transfer progress from model and convert to view format.
     */
    private void handleFileTransferProgress(String progress) {
        // Format: "filename - percentage%"
        String[] parts = progress.split(" - ");
        if (parts.length >= 2) {
            String filename = parts[0];
            String percentageStr = parts[1].replace("%", "");
            try {
                int percentage = Integer.parseInt(percentageStr);
                // Estimate bytes based on percentage (this is a simplification)
                long totalBytes = 1000; // Default total
                long bytesTransferred = (percentage * totalBytes) / 100;
                view.showFileTransferProgress(filename, bytesTransferred, totalBytes);
            } catch (NumberFormatException e) {
                view.showFileTransferProgress(progress, 0, 0);
            }
        } else {
            view.showFileTransferProgress(progress, 0, 0);
        }
    }

    /**
     * Handle shell command from model and convert to view format.
     */
    private void handleShellCommand(String result) {
        // Format: "username executed: command"
        String[] parts = result.split(" executed: ");
        if (parts.length >= 2) {
            String username = parts[0];
            String command = parts[1];
            view.showShellCommand(username, command);
        } else {
            view.showShellCommand("unknown", result);
        }
    }

    /**
     * Start the server application.
     */
    public void start() {
        try {
            Logger.info("SSHServerController: Starting server");
            
            // Get configuration from view
            ServerConfig config = view.getConfigFromUser();
            
            // Delegate to model for server logic
            model.start(config);
            
        } catch (Exception e) {
            Logger.error("SSHServerController: Failed to start server: " + e.getMessage());
            view.displayError("Failed to start server: " + e.getMessage());
        }
    }

    /**
     * Stop the server.
     */
    public void stop() {
        Logger.info("SSHServerController: Stopping server");
        running = false;
        model.stop();
    }

    /**
     * Check if the server should continue running.
     */
    public boolean shouldContinue() {
        return running && view.shouldContinue();
    }
} 