package ssh.client.model;

/**
 * Information about the server to connect to.
 */
public class ServerInfo {
    private String host;
    private int port;
    private String username;

    public ServerInfo() {
        // Default values
        this.host = "localhost";
        this.port = 2222;
        this.username = "admin";
    }

    public ServerInfo(String host, int port, String username) {
        this.host = host;
        this.port = port;
        this.username = username;
    }

    // Getters and setters
    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    @Override
    public String toString() {
        return "ServerInfo{" +
                "host='" + host + '\'' +
                ", port=" + port +
                ", username='" + username + '\'' +
                '}';
    }
} 