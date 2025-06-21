# SSH Server and Client Implementation

A complete Java implementation of SSH server and client with secure authentication, encryption, and shell access.

## Features

- **Secure Key Exchange**: Diffie-Hellman key exchange for session key generation
- **Multiple Authentication Methods**: Password and public key authentication
- **AES-GCM Encryption**: Symmetric encryption for all communications
- **Shell Access**: Remote command execution
- **File Transfer**: Basic file upload/download functionality
- **User Management**: Configurable user accounts
- **Credentials Management**: Standard credentials file for easy configuration

## Project Structure

```
ssh/
├── app/
│   ├── build.gradle                 # Gradle build configuration
│   ├── data/                        # Data storage
│   │   ├── server/
│   │   │   ├── server_keys/         # Server RSA keys
│   │   │   └── users.properties     # User accounts
│   │   └── client/
│   │       └── client_keys/         # Client keys (for public key auth)
│   └── src/main/java/ssh/
│       ├── auth/                    # Authentication modules
│       ├── client/                  # Client implementation
│       ├── crypto/                  # Cryptographic operations
│       ├── protocol/                # SSH protocol implementation
│       ├── server/                  # Server implementation
│       ├── shell/                   # Shell command execution
│       └── utils/                   # Utility classes
├── config/
│   ├── credentials.properties       # Client credentials configuration
│   ├── client.properties           # Client configuration
│   └── server.properties           # Server configuration
└── README.md
```

## Quick Start

### Prerequisites

- Java 11 or higher
- Gradle (included in the project)

### Building the Project

```bash
./gradlew build
```

### Starting the Server

```bash
# Start server with default configuration
./gradlew run --args="server"

# Start server with custom port
./gradlew run --args="server --port 2223"

# Start server with custom configuration file
./gradlew run --args="server --config config/server.properties"
```

### Starting the Client

```bash
# Start client (uses credentials from config/credentials.properties)
./gradlew run --args="client"

# Start client with custom credentials file
./gradlew run --args="client --credentials config/my-credentials.properties"
```

## Configuration

### Credentials File (config/credentials.properties)

The client uses a standard credentials file to store authentication information. This eliminates the need for manual input of passwords and keys.

```properties
# Default user credentials
default.username=admin
default.password=admin
default.auth.type=password

# Additional users
user1.username=test
user1.password=test
user1.auth.type=password

user2.username=user1
user2.password=password
user2.auth.type=password

# Public key authentication example (commented out)
# user3.username=keyuser
# user3.auth.type=publickey
# user3.private.key.path=data/client/client_keys/id_rsa
# user3.public.key.path=data/client/client_keys/id_rsa.pub

# Server connection defaults
server.host=localhost
server.port=2222
```

### Server Configuration (config/server.properties)

```properties
# Server settings
server.port=2222
server.host=0.0.0.0
server.max.connections=10

# Security settings
server.key.size=2048
server.session.timeout=300000

# Logging
server.log.level=INFO
```

### Client Configuration (config/client.properties)

```properties
# Client settings
client.connection.timeout=30000
client.read.timeout=30000

# Security settings
client.key.size=2048

# Logging
client.log.level=INFO
```

## Usage

### Server

1. **Start the server**:
   ```bash
   ./gradlew run --args="server"
   ```

2. **Default users** (configured in `app/data/server/users.properties`):
   - Username: `admin`, Password: `admin`
   - Username: `test`, Password: `test`
   - Username: `user1`, Password: `password`

3. **Server will listen** on port 2222 by default

### Client

1. **Configure credentials** in `config/credentials.properties`

2. **Start the client**:
   ```bash
   ./gradlew run --args="client"
   ```

3. **Select user** (if multiple users are configured)

4. **Choose service**:
   - Shell: Execute remote commands
   - File Transfer: Upload/download files
   - Exit: Close connection

## Authentication Methods

### Password Authentication

The default authentication method. Users provide username and password.

```properties
default.username=admin
default.password=admin
default.auth.type=password
```

### Public Key Authentication

For enhanced security, users can use RSA key pairs.

```properties
user3.username=keyuser
user3.auth.type=publickey
user3.private.key.path=data/client/client_keys/id_rsa
user3.public.key.path=data/client/client_keys/id_rsa.pub
```

## Security Features

- **Diffie-Hellman Key Exchange**: Secure session key generation
- **AES-GCM Encryption**: Authenticated encryption for all data
- **RSA Key Pairs**: For server identity and public key authentication
- **Session Management**: Secure session handling with timeouts
- **User Isolation**: Separate user accounts and permissions

## Protocol Specification

### Message Types

- `KEY_EXCHANGE`: Diffie-Hellman key exchange
- `AUTH_REQUEST`: Authentication request
- `AUTH_RESPONSE`: Authentication response
- `SHELL_COMMAND`: Shell command execution
- `SHELL_RESPONSE`: Shell command response
- `FILE_TRANSFER`: File transfer operations
- `ERROR`: Error messages

### Connection Flow

1. **TCP Connection**: Client connects to server
2. **Key Exchange**: Diffie-Hellman key exchange
3. **Authentication**: Password or public key authentication
4. **Service Selection**: Shell or file transfer
5. **Data Exchange**: Encrypted communication
6. **Disconnection**: Clean connection termination

## Development

### Building

```bash
# Build the project
./gradlew build

# Run tests
./gradlew test

# Clean build
./gradlew clean build
```

### Project Structure Details

- **Protocol Layer**: Handles SSH protocol messages and state management
- **Crypto Layer**: Implements cryptographic operations (RSA, AES, DH)
- **Auth Layer**: Manages user authentication and authorization
- **Shell Layer**: Executes shell commands and manages output
- **UI Layer**: Provides user interface abstraction for both client and server

### Extending the Project

- **New Authentication Methods**: Implement additional auth providers
- **Additional Services**: Add new SSH services (SFTP, port forwarding)
- **Enhanced Security**: Add certificate-based authentication
- **GUI Interface**: Implement graphical user interface
- **Plugin System**: Add plugin architecture for extensibility

## Troubleshooting

### Common Issues

1. **Port already in use**:
   - Change server port in configuration
   - Check if another SSH server is running

2. **Authentication failed**:
   - Verify credentials in `config/credentials.properties`
   - Check server user configuration

3. **Connection refused**:
   - Ensure server is running
   - Check host and port configuration

4. **Key file not found**:
   - Verify key file paths in credentials
   - Generate keys if needed

### Logging

Enable debug logging by setting log level to DEBUG in configuration files:

```properties
server.log.level=DEBUG
client.log.level=DEBUG
```

## License

This project is for educational purposes. Use at your own risk in production environments.

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests
5. Submit a pull request

## Security Notes

- This is an educational implementation
- Not recommended for production use
- Missing some security features of real SSH implementations
- Always use established SSH implementations for production systems 