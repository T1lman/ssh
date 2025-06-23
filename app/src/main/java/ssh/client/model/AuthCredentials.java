package ssh.client.model;

/**
 * Authentication credentials for SSH client.
 */
public class AuthCredentials {
    private String authType;
    private String password;
    private String privateKeyPath;
    private String publicKeyPath;
    private String username;

    public AuthCredentials() {
        this.authType = "password"; // Default to password authentication
    }

    public AuthCredentials(String authType) {
        this.authType = authType;
    }

    // Getters and setters
    public String getAuthType() {
        return authType;
    }

    public void setAuthType(String authType) {
        this.authType = authType;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPrivateKeyPath() {
        return privateKeyPath;
    }

    public void setPrivateKeyPath(String privateKeyPath) {
        this.privateKeyPath = privateKeyPath;
    }

    public String getPublicKeyPath() {
        return publicKeyPath;
    }

    public void setPublicKeyPath(String publicKeyPath) {
        this.publicKeyPath = publicKeyPath;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * Check if this is password authentication.
     */
    public boolean isPasswordAuth() {
        return "password".equals(authType);
    }

    /**
     * Check if this is public key authentication.
     */
    public boolean isPublicKeyAuth() {
        return "publickey".equals(authType);
    }

    /**
     * Check if this is dual authentication (both password and public key).
     */
    public boolean isDualAuth() {
        return "dual".equals(authType);
    }

    /**
     * Check if this requires password authentication.
     */
    public boolean requiresPassword() {
        return isPasswordAuth() || isDualAuth();
    }

    /**
     * Check if this requires public key authentication.
     */
    public boolean requiresPublicKey() {
        return isPublicKeyAuth() || isDualAuth();
    }

    @Override
    public String toString() {
        return "AuthCredentials{" +
                "authType='" + authType + '\'' +
                ", password='" + (password != null ? "***" : "null") + '\'' +
                ", privateKeyPath='" + privateKeyPath + '\'' +
                ", publicKeyPath='" + publicKeyPath + '\'' +
                ", username='" + username + '\'' +
                '}';
    }
} 