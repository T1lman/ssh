package ssh.server.controller;

import ssh.model.auth.AuthenticationManager;
import ssh.model.auth.UserStore;
import ssh.model.crypto.RSAKeyGenerator;
import ssh.model.utils.Logger;
import ssh.server.model.ServerConnection;
import ssh.server.view.ConsoleServerUI;
import ssh.server.view.ServerConfig;
import ssh.server.view.ServerUI;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyPair;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Main SSH server class.
 */
public class SSHServer {
    private ServerConfig config;
    private ServerUI ui;
    private ServerSocket serverSocket;
    private ExecutorService threadPool;
    private AuthenticationManager authManager;
    private KeyPair serverKeyPair;
    private boolean running;

    public SSHServer(ServerConfig config, ServerUI ui) {
        this.config = config;
        this.ui = ui;
        this.threadPool = Executors.newFixedThreadPool(config.getMaxConnections());
        this.running = false;
    }

    /**
     * Start the SSH server.
     */
    public void start() {
        try {
            // Initialize server
            initializeServer();
            
            // Show startup information
            ui.showServerStartup(config.getPort(), config.getHost());
            
            // Start listening for connections
            startListening();
            
        } catch (Exception e) {
            ui.displayError("Failed to start server: " + e.getMessage());
            e.printStackTrace();
        } finally {
            cleanup();
        }
    }

    /**
     * Initialize the server components.
     */
    private void initializeServer() throws Exception {
        // Initialize user store and authentication manager
        UserStore userStore = new UserStore(config.getUsersFile(), config.getAuthorizedKeysDir());
        authManager = new AuthenticationManager(userStore);
        
        // Generate or load server key pair
        initializeServerKeys();
        
        // Create server socket
        serverSocket = new ServerSocket(config.getPort());
        serverSocket.setReuseAddress(true);
        
        running = true;
        
        ui.displayMessage("Server initialized successfully");
    }

    /**
     * Initialize server RSA key pair.
     */
    private void initializeServerKeys() throws Exception {
        String privateKeyPath = config.getKeyDirectory() + "/server_rsa_key";
        String publicKeyPath = config.getKeyDirectory() + "/server_rsa_key.pub";
        
        try {
            // Try to load existing keys
            serverKeyPair = RSAKeyGenerator.loadKeyPair(privateKeyPath, publicKeyPath);
            ui.displayMessage("Loaded existing server keys");
        } catch (Exception e) {
            // Generate new keys if they don't exist
            ui.displayMessage("Generating new server keys...");
            serverKeyPair = RSAKeyGenerator.generateKeyPair();
            
            // Create directory if it doesn't exist
            java.io.File keyDir = new java.io.File(config.getKeyDirectory());
            keyDir.mkdirs();
            
            // Save the keys
            RSAKeyGenerator.saveKeyPair(serverKeyPair, privateKeyPath, publicKeyPath);
            ui.displayMessage("Server keys generated and saved");
        }
    }

    /**
     * Start listening for client connections.
     */
    private void startListening() {
        ui.showServerStatus("Listening for connections on " + config.getHost() + ":" + config.getPort());
        
        while (running && ui.shouldContinue()) {
            try {
                // Accept client connection
                Socket clientSocket = serverSocket.accept();
                
                // Get client information
                String clientAddress = clientSocket.getInetAddress().getHostAddress();
                int clientPort = clientSocket.getPort();
                ui.showConnectionInfo(clientAddress, clientPort);
                
                // Handle client in separate thread
                ServerConnection connection = new ServerConnection(
                    clientSocket, authManager, serverKeyPair, ui, config
                );
                threadPool.submit(connection);
                
            } catch (IOException e) {
                if (running) {
                    ui.displayError("Error accepting connection: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Stop the server.
     */
    public void stop() {
        running = false;
        ui.showServerShutdown();
        
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                ui.displayError("Error closing server socket: " + e.getMessage());
            }
        }
        
        if (threadPool != null) {
            threadPool.shutdown();
        }
    }

    /**
     * Clean up resources.
     */
    private void cleanup() {
        stop();
        if (ui instanceof ConsoleServerUI) {
            ((ConsoleServerUI) ui).close();
        }
    }

    /**
     * Main method - entry point for the SSH server.
     */
    public static void main(String[] args) {
        // Initialize logger
        Logger.initialize("logs/server.log");
        
        // Create UI
        ServerUI ui = new ConsoleServerUI();
        
        // Get configuration from user
        ServerConfig config = ui.getConfigFromUser();
        
        // Create and start server
        SSHServer server = new SSHServer(config, ui);
        
        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Logger.info("Shutdown signal received");
            server.stop();
            Logger.close();
        }));
        
        // Show startup header
        Logger.info("SSH Server Starting");
        Logger.info("Log file: " + Logger.getLogFile());
        
        // Start the server
        server.start();
    }
} 