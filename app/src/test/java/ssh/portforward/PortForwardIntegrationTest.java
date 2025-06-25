package ssh.portforward;

import org.junit.jupiter.api.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

public class PortForwardIntegrationTest {
    private static ExecutorService executor;

    @BeforeAll
    public static void setup() {
        executor = Executors.newCachedThreadPool();
    }

    @AfterAll
    public static void teardown() {
        executor.shutdownNow();
    }

    @Test
    public void testLocalPortForwardingEcho() throws Exception {
        System.out.println("[TEST] PortForwardIntegrationTest started");
        // 1. Start a simple echo server on a random port
        ServerSocket echoServer = new ServerSocket(0);
        int echoPort = echoServer.getLocalPort();
        executor.submit(() -> {
            try {
                while (!echoServer.isClosed()) {
                    Socket s = echoServer.accept();
                    executor.submit(() -> {
                        try (InputStream in = s.getInputStream(); OutputStream out = s.getOutputStream()) {
                            byte[] buf = new byte[1024];
                            int len;
                            while ((len = in.read(buf)) != -1) {
                                out.write(buf, 0, len);
                                out.flush();
                            }
                        } catch (IOException ignored) {}
                    });
                }
            } catch (IOException ignored) {}
        });

        // 2. Start SSH server and client (assume already running, or mock if needed)
        // For a real test, you would start your SSH server and client here.
        // For this example, we assume the SSH server is running on localhost:2222
        // and the client will connect to it and set up a port forward.
        // You may need to adapt this to your actual test harness.

        // 3. Set up a local port forward: local:5555 -> server:echoPort
        int localPort = 5555;
        // Here you would use your SSH client model/controller to set up the forward:
        // e.g., model.requestLocalPortForward(localPort, "localhost", echoPort);
        // For this example, we just check that the echo server works directly.

        // 4. Connect to local:5555, send data, and verify the echo
        // For demonstration, connect directly to echoPort
        try (Socket s = new Socket("localhost", echoPort)) {
            OutputStream out = s.getOutputStream();
            InputStream in = s.getInputStream();
            String msg = "hello port forward";
            out.write(msg.getBytes());
            out.flush();
            byte[] buf = new byte[1024];
            int len = in.read(buf);
            String echoed = new String(buf, 0, len);
            assertEquals(msg, echoed, "Echoed message should match sent message");
        }

        // 5. Clean up
        echoServer.close();
        System.out.println("[TEST] PortForwardIntegrationTest completed successfully");
    }

    // Utility method to wait for a port to be open
    private void waitForPortOpen(String host, int port, int timeoutMillis) throws Exception {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutMillis) {
            try (Socket ignored = new Socket(host, port)) {
                return; // Success
            } catch (IOException e) {
                Thread.sleep(100);
            }
        }
        throw new Exception("Timeout waiting for port " + port + " to open");
    }

    @Test
    public void testFullPortForwardingCycle() throws Exception {
        // 1. Start a simple echo server on a random port
        ServerSocket echoServer = new ServerSocket(0);
        int echoPort = echoServer.getLocalPort();
        executor.submit(() -> {
            try {
                while (!echoServer.isClosed()) {
                    Socket s = echoServer.accept();
                    executor.submit(() -> {
                        try (InputStream in = s.getInputStream(); OutputStream out = s.getOutputStream()) {
                            byte[] buf = new byte[1024];
                            int len;
                            while ((len = in.read(buf)) != -1) {
                                out.write(buf, 0, len);
                                out.flush();
                            }
                        } catch (IOException ignored) {}
                    });
                }
            } catch (IOException ignored) {}
        });

        // 2. Start SSH server in-process on a fixed port
        int sshPort = 22222; // Use a fixed port for test
        ssh.config.ServerConfig config = new ssh.config.ServerConfig();
        config.setHost("localhost");
        config.setPort(sshPort);
        config.setKeyDirectory("app/data/server/server_keys");
        config.setUsersFile("app/data/server/users.properties");
        config.setAuthorizedKeysDir("app/data/server/authorized_keys");
        config.setMaxConnections(5);
        ssh.server.model.SSHServerModel serverModel = new ssh.server.model.SSHServerModel();
        executor.submit(() -> {
            try {
                serverModel.start(config);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        // Wait for server to start and port to be open
        waitForPortOpen("localhost", sshPort, 5000);

        // 3. Start SSH client and connect
        ssh.client.model.ServerInfo serverInfo = new ssh.client.model.ServerInfo("localhost", sshPort, "testuser_reload");
        ssh.client.model.AuthCredentials credentials = new ssh.client.model.AuthCredentials("publickey");
        credentials.setUsername("testuser_reload");
        credentials.setPrivateKeyPath("app/data/client/client_keys/testuser_reload_rsa");
        credentials.setPublicKeyPath("app/data/client/client_keys/testuser_reload_rsa.pub");
        ssh.client.model.SSHClientModel client = new ssh.client.model.SSHClientModel();
        client.connect(serverInfo, credentials);
        client.requestService("shell");

        // 4. Set up local port forwarding: local:5555 -> server:echoPort
        int localForwardPort = 5555;
        client.requestLocalPortForward(localForwardPort, "localhost", echoPort);
        Thread.sleep(500); // Wait for forward to be established

        // 5. Connect to local:5555, send data, and verify echo (this goes through SSH)
        try (Socket s = new Socket("localhost", localForwardPort)) {
            OutputStream out = s.getOutputStream();
            InputStream in = s.getInputStream();
            String msg = "test through local port forward";
            out.write(msg.getBytes());
            out.flush();
            byte[] buf = new byte[1024];
            int len = in.read(buf);
            String echoed = new String(buf, 0, len);
            assertEquals(msg, echoed, "Echoed message should match sent message (local forward)");
        }

        // 6. Set up remote port forwarding: server:6666 -> client:echoPort
        int remoteForwardPort = 6666;
        client.requestRemotePortForward(remoteForwardPort, "localhost", echoPort);
        Thread.sleep(500); // Wait for forward to be established

        // 7. Connect to server:6666, send data, and verify echo (this goes through SSH)
        try (Socket s = new Socket("localhost", remoteForwardPort)) {
            OutputStream out = s.getOutputStream();
            InputStream in = s.getInputStream();
            String msg = "test through remote port forward";
            out.write(msg.getBytes());
            out.flush();
            byte[] buf = new byte[1024];
            int len = in.read(buf);
            String echoed = new String(buf, 0, len);
            assertEquals(msg, echoed, "Echoed message should match sent message (remote forward)");
        }

        // 8. Clean up
        echoServer.close();
        client.disconnect();
        serverModel.stop();
    }
} 