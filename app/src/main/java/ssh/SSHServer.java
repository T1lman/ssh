package ssh;

import ssh.auth.AuthenticationManager;
import ssh.auth.UserStore;
import ssh.crypto.RSAKeyGenerator;
import ssh.server.ServerConnection;
import ssh.server.ui.ConsoleServerUI;
import ssh.server.ui.ServerConfig;
import ssh.server.ui.ServerUI;

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
        // Parse command line arguments
        ServerConfig config = parseCommandLineArgs(args);
        
        // Create UI
        ServerUI ui = new ConsoleServerUI();
        
        // Create and start server
        SSHServer server = new SSHServer(config, ui);
        
        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            ui.displayMessage("Shutdown signal received");
            server.stop();
        }));
        
        // Start the server
        server.start();
    }

    /**
     * Parse command line arguments.
     */
    private static ServerConfig parseCommandLineArgs(String[] args) {
        ServerConfig config = new ServerConfig();
        
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-p":
                case "--port":
                    if (i + 1 < args.length) {
                        try {
                            config.setPort(Integer.parseInt(args[++i]));
                        } catch (NumberFormatException e) {
                            System.err.println("Invalid port number: " + args[i]);
                        }
                    }
                    break;
                case "-h":
                case "--host":
                    if (i + 1 < args.length) {
                        config.setHost(args[++i]);
                    }
                    break;
                case "-k":
                case "--key-dir":
                    if (i + 1 < args.length) {
                        config.setKeyDirectory(args[++i]);
                    }
                    break;
                case "-u":
                case "--users":
                    if (i + 1 < args.length) {
                        config.setUsersFile(args[++i]);
                    }
                    break;
                case "-a":
                case "--auth-keys":
                    if (i + 1 < args.length) {
                        config.setAuthorizedKeysDir(args[++i]);
                    }
                    break;
                case "-m":
                case "--max-connections":
                    if (i + 1 < args.length) {
                        try {
                            config.setMaxConnections(Integer.parseInt(args[++i]));
                        } catch (NumberFormatException e) {
                            System.err.println("Invalid max connections: " + args[i]);
                        }
                    }
                    break;
                case "-t":
                case "--timeout":
                    if (i + 1 < args.length) {
                        try {
                            int minutes = Integer.parseInt(args[++i]);
                            config.setSessionTimeout(minutes * 60 * 1000L);
                        } catch (NumberFormatException e) {
                            System.err.println("Invalid timeout: " + args[i]);
                        }
                    }
                    break;
                case "-l":
                case "--log-level":
                    if (i + 1 < args.length) {
                        config.setLogLevel(args[++i].toUpperCase());
                    }
                    break;
                case "--help":
                    printUsage();
                    System.exit(0);
                    break;
                default:
                    System.err.println("Unknown option: " + args[i]);
                    printUsage();
                    System.exit(1);
            }
        }
        
        return config;
    }

    /**
     * Print usage information.
     */
    private static void printUsage() {
        System.out.println("SSH Server Usage:");
        System.out.println("  -p, --port PORT              Server port (default: 2222)");
        System.out.println("  -h, --host HOST              Server host (default: localhost)");
        System.out.println("  -k, --key-dir DIR            Key directory (default: data/server/server_keys)");
        System.out.println("  -u, --users FILE             Users file (default: data/server/users.properties)");
        System.out.println("  -a, --auth-keys DIR          Authorized keys directory (default: data/server/authorized_keys)");
        System.out.println("  -m, --max-connections NUM    Max connections (default: 10)");
        System.out.println("  -t, --timeout MINUTES        Session timeout in minutes (default: 30)");
        System.out.println("  -l, --log-level LEVEL        Log level (default: INFO)");
        System.out.println("  --help                       Show this help message");
    }
} 