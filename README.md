# SSH Client-Server Implementation

A Java-based SSH client-server implementation with support for both console and GUI clients, featuring **dual authentication** (password + public key).

## Features

- **Dual Authentication**: Requires both password AND public key authentication for enhanced security
- **Multiple Client Interfaces**: Console and JavaFX GUI clients
- **File Transfer**: Secure file upload/download capabilities
- **Shell Access**: Interactive command execution
- **Key Management**: RSA key generation and management utilities
- **Cross-platform**: Works on Windows, macOS, and Linux

## Authentication System

This SSH implementation uses **dual authentication** for enhanced security:

1. **Password Authentication**: Traditional username/password verification
2. **Public Key Authentication**: RSA key pair verification
3. **Dual Authentication**: BOTH password AND public key must be valid

### Authentication Flow

1. User selects username from available users
2. Client loads both password and key information for the selected user
3. Client sends authentication request with both credentials
4. Server validates both password AND public key
5. Connection is established only if BOTH authentications succeed

## Quick Start

### 1. Setup Dual Authentication

First, set up the dual authentication system:

```bash
# Generate key pairs and configure server
java -cp app/build/classes/java/main ssh.utils.SetupDualAuth
```

This will:
- Generate RSA key pairs for all users (admin, test, user1)
- Add public keys to server's authorized keys
- Validate all key pairs

### 2. Start the Server

```bash
# Start SSH server on port 2222
java -cp app/build/classes/java/main ssh.server.SSHServer
```

### 3. Connect with Client

#### GUI Client (Recommended)
```bash
./gradlew run
```

#### Console Client
```bash
java -cp app/build/classes/java/main ssh.client.SSHClientMain
```

## Configuration

### User Credentials (`config/credentials.properties`)

```properties
# Dual authentication configuration
default.username=admin
default.password=admin
default.auth.type=dual
default.privateKey=data/client/client_keys/admin_rsa
default.publicKey=data/client/client_keys/admin_rsa.pub

user1.username=test
user1.password=test
user1.auth.type=dual
user1.privateKey=data/client/client_keys/test_rsa
user1.publicKey=data/client/client_keys/test_rsa.pub

# Server connection
server.host=localhost
server.port=2222
```

### Authentication Types

- `password`: Only password authentication
- `publickey`: Only public key authentication  
- `dual`: Both password AND public key required (recommended)

## GUI Client Features

The JavaFX GUI client provides:

- **User Selection**: Dropdown to choose from configured users
- **Authentication Type**: Support for password, public key, or dual authentication
- **Key Management**: Generate new key pairs or browse existing keys
- **Interactive Shell**: Command input with working directory display
- **File Transfer**: Upload/download files with progress tracking
- **Connection Management**: Connect/disconnect with status display

### GUI Authentication Dialog

When using dual authentication:
1. Select username from dropdown
2. Choose "dual" authentication type
3. Enter password
4. Select or generate key pair
5. Click "Login" to authenticate

## Console Client Features

The console client provides:

- **User Selection**: Numbered list of available users
- **Interactive Shell**: Command execution with output display
- **Service Selection**: Choose between shell, file transfer, or exit
- **Error Handling**: Graceful handling of connection issues

## Key Management

### Generate New Key Pairs

```bash
# Using the GUI client
# Click "Generate New Keys" in the authentication dialog

# Using the console utility
java -cp app/build/classes/java/main ssh.utils.KeyManager generate admin_rsa
```

### Add Keys to Server

```bash
# Using the setup utility (automatic)
java -cp app/build/classes/java/main ssh.utils.SetupDualAuth

# Using the key manager
java -cp app/build/classes/java/main ssh.utils.KeyManager add-key admin data/client/client_keys/admin_rsa.pub
```

### Validate Key Pairs

```bash
java -cp app/build/classes/java/main ssh.utils.KeyManager validate data/client/client_keys/admin_rsa data/client/client_keys/admin_rsa.pub
```

## File Structure

```
ssh/
├── app/
│   ├── src/main/java/ssh/
│   │   ├── auth/           # Authentication system
│   │   ├── client/         # Client implementations
│   │   │   ├── gui/        # JavaFX GUI client
│   │   │   └── ui/         # Client UI interfaces
│   │   ├── crypto/         # Cryptographic utilities
│   │   ├── protocol/       # SSH protocol implementation
│   │   ├── server/         # Server implementation
│   │   ├── shell/          # Shell command execution
│   │   └── utils/          # Utility classes
│   └── build.gradle
├── config/
│   └── credentials.properties  # User credentials
├── data/
│   ├── client/
│   │   └── client_keys/    # Client private/public keys
│   └── server/
│       ├── authorized_keys/ # Server authorized keys
│       ├── files/          # Server file storage
│       └── users.properties # Server user database
└── README.md
```

## Security Features

- **Dual Authentication**: Requires both password and public key
- **RSA Key Pairs**: 2048-bit RSA encryption
- **Session Management**: Secure session establishment
- **File Transfer Security**: Encrypted file transfers
- **Input Validation**: Comprehensive input sanitization

## Troubleshooting

### Authentication Issues

1. **Dual Auth Required**: Ensure both password and key are configured
2. **Key Validation**: Run key validation to check key pairs
3. **User Configuration**: Verify user exists in both client and server configs

### Connection Issues

1. **Server Running**: Ensure server is started on correct port
2. **Firewall**: Check if port 2222 is accessible
3. **Network**: Verify localhost connectivity

### GUI Issues

1. **JavaFX**: Ensure JavaFX is available
2. **Permissions**: Check file permissions for key files
3. **Display**: Verify display settings for GUI

## Development

### Building

```bash
./gradlew build
```

### Testing

```bash
./gradlew test
```

### Running

```bash
# Server
./gradlew runServer

# Client
./gradlew run
```

## License

This project is for educational purposes. Use at your own risk in production environments. 