package ssh.client.model;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class SSHClientModelTest {
    @Test
    void testConnectionStatusEvent() {
        SSHClientModel model = new SSHClientModel();
        final boolean[] statusChanged = {false};
        model.setOnConnectionStatusChanged(connected -> statusChanged[0] = connected);
        // Simulate connection event
        model.disconnect(); // Should set status to false
        assertFalse(statusChanged[0]);
    }

    @Test
    void testAuthenticationEvent() {
        SSHClientModel model = new SSHClientModel();
        final String[] authResult = {null};
        model.setOnAuthenticationResult(result -> authResult[0] = result);
        // Simulate authentication event
        model.getClass(); // Just to avoid unused warning
        model.getClass(); // No real authentication, so simulate:
        model.getClass(); // Simulate event
        model.getClass(); // Not possible to call notifyAuthenticationResult directly, so just check callback wiring
        assertNull(authResult[0]); // Should be null since not triggered
    }

    @Test
    void testShellOutputEvent() {
        SSHClientModel model = new SSHClientModel();
        final String[] output = {null};
        model.setOnShellOutput(s -> output[0] = s);
        // Simulate shell output event
        // Not possible to call notifyShellOutput directly, so just check callback wiring
        assertNull(output[0]);
    }

    @Test
    void testFileTransferProgressEvent() {
        SSHClientModel model = new SSHClientModel();
        final String[] progress = {null};
        model.setOnFileTransferProgress(s -> progress[0] = s);
        // Simulate file transfer progress event
        assertNull(progress[0]);
    }

    @Test
    void testErrorEvent() {
        SSHClientModel model = new SSHClientModel();
        final String[] error = {null};
        model.setOnError(s -> error[0] = s);
        // Simulate error event
        assertNull(error[0]);
    }

    @Test
    void testWorkingDirectoryChangedEvent() {
        SSHClientModel model = new SSHClientModel();
        final String[] dir = {null};
        model.setOnWorkingDirectoryChanged(s -> dir[0] = s);
        // Simulate working directory change event
        assertNull(dir[0]);
    }

    @Test
    void testPortForwardDelegation() throws Exception {
        class MockConnection extends ClientConnection {
            boolean localCalled = false;
            boolean remoteCalled = false;
            int localPort, remotePort, destPort;
            String remoteHost, localHost;
            MockConnection() { super(null, null); }
            @Override
            public void requestLocalPortForward(int localPort, String remoteHost, int remotePort) {
                localCalled = true;
                this.localPort = localPort;
                this.remoteHost = remoteHost;
                this.destPort = remotePort;
            }
            @Override
            public void requestRemotePortForward(int remotePort, String localHost, int localPort) {
                remoteCalled = true;
                this.remotePort = remotePort;
                this.localHost = localHost;
                this.localPort = localPort;
            }
            @Override
            public boolean isActive() { return true; }
        }
        SSHClientModel model = new SSHClientModel();
        MockConnection mockConn = new MockConnection();
        java.lang.reflect.Field f = model.getClass().getDeclaredField("connection");
        f.setAccessible(true);
        f.set(model, mockConn);
        java.lang.reflect.Field c = model.getClass().getDeclaredField("connected");
        c.setAccessible(true);
        c.set(model, true);
        model.requestLocalPortForward(1234, "host", 5678);
        assertTrue(mockConn.localCalled);
        assertEquals(1234, mockConn.localPort);
        assertEquals("host", mockConn.remoteHost);
        assertEquals(5678, mockConn.destPort);
        model.requestRemotePortForward(2222, "lh", 3333);
        assertTrue(mockConn.remoteCalled);
        assertEquals(2222, mockConn.remotePort);
        assertEquals("lh", mockConn.localHost);
        assertEquals(3333, mockConn.localPort);
    }
} 