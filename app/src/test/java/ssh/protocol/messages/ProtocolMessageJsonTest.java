package ssh.protocol.messages;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import ssh.model.protocol.MessageType;
import ssh.model.protocol.Message;
import ssh.model.protocol.messages.ShellMessage;
import ssh.model.protocol.messages.ServiceMessage;
import ssh.model.protocol.messages.ErrorMessage;
import ssh.model.protocol.messages.KeyExchangeMessage;
import ssh.model.protocol.messages.FileTransferMessage;
import ssh.model.protocol.messages.AuthMessage;

import static org.junit.jupiter.api.Assertions.*;

public class ProtocolMessageJsonTest {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testShellMessageJson() throws Exception {
        ShellMessage msg = new ShellMessage(MessageType.SHELL_RESULT);
        msg.setCommand("ls -l");
        msg.setWorkingDirectory("/home/test");
        msg.setExitCode(1);
        msg.setStdout("output\nwith;special:chars");
        msg.setStderr("error\nwith;special:chars");

        byte[] json = objectMapper.writeValueAsBytes(msg);
        Message base = objectMapper.readValue(json, Message.class);
        ShellMessage deserialized = (ShellMessage) base;
        assertEquals(msg.getCommand(), deserialized.getCommand());
        assertEquals(msg.getWorkingDirectory(), deserialized.getWorkingDirectory());
        assertEquals(msg.getExitCode(), deserialized.getExitCode());
        assertEquals(msg.getStdout(), deserialized.getStdout());
        assertEquals(msg.getStderr(), deserialized.getStderr());
    }

    @Test
    public void testServiceMessageJson() throws Exception {
        ServiceMessage msg = new ServiceMessage(MessageType.SERVICE_REQUEST);
        msg.setService("shell");
        byte[] json = objectMapper.writeValueAsBytes(msg);
        Message base = objectMapper.readValue(json, Message.class);
        ServiceMessage deserialized = (ServiceMessage) base;
        assertEquals(msg.getService(), deserialized.getService());
    }

    @Test
    public void testErrorMessageJson() throws Exception {
        ErrorMessage msg = new ErrorMessage();
        msg.setErrorCode("404");
        msg.setErrorMessage("Not found");
        msg.setDetails("Details;with:special=chars");
        byte[] json = objectMapper.writeValueAsBytes(msg);
        Message base = objectMapper.readValue(json, Message.class);
        ErrorMessage deserialized = (ErrorMessage) base;
        assertEquals(msg.getErrorCode(), deserialized.getErrorCode());
        assertEquals(msg.getErrorMessage(), deserialized.getErrorMessage());
        assertEquals(msg.getDetails(), deserialized.getDetails());
    }

    @Test
    public void testKeyExchangeMessageJson() throws Exception {
        KeyExchangeMessage msg = new KeyExchangeMessage(MessageType.KEY_EXCHANGE_INIT);
        msg.setDhPublicKey("pubkey");
        msg.setClientId("client1");
        msg.setServerId("server1");
        msg.setSignature("sig");
        msg.setSessionId("sess");
        byte[] json = objectMapper.writeValueAsBytes(msg);
        Message base = objectMapper.readValue(json, Message.class);
        KeyExchangeMessage deserialized = (KeyExchangeMessage) base;
        assertEquals(msg.getDhPublicKey(), deserialized.getDhPublicKey());
        assertEquals(msg.getClientId(), deserialized.getClientId());
        assertEquals(msg.getServerId(), deserialized.getServerId());
        assertEquals(msg.getSignature(), deserialized.getSignature());
        assertEquals(msg.getSessionId(), deserialized.getSessionId());
    }

    @Test
    public void testFileTransferMessageJson() throws Exception {
        FileTransferMessage msg = new FileTransferMessage(MessageType.FILE_DATA);
        msg.setFilename("file.txt");
        msg.setFileSize(12345L);
        msg.setTargetPath("/tmp/file.txt");
        msg.setSequenceNumber(2);
        msg.setData("base64data");
        msg.setLast(true);
        msg.setStatus("ok");
        msg.setMessage("done");
        byte[] json = objectMapper.writeValueAsBytes(msg);
        Message base = objectMapper.readValue(json, Message.class);
        FileTransferMessage deserialized = (FileTransferMessage) base;
        assertEquals(msg.getFilename(), deserialized.getFilename());
        assertEquals(msg.getFileSize(), deserialized.getFileSize());
        assertEquals(msg.getTargetPath(), deserialized.getTargetPath());
        assertEquals(msg.getSequenceNumber(), deserialized.getSequenceNumber());
        assertEquals(msg.getData(), deserialized.getData());
        assertEquals(msg.isLast(), deserialized.isLast());
        assertEquals(msg.getStatus(), deserialized.getStatus());
        assertEquals(msg.getMessage(), deserialized.getMessage());
    }

    @Test
    public void testAuthMessageJson() throws Exception {
        AuthMessage msg = new AuthMessage(MessageType.AUTH_REQUEST);
        msg.setUsername("user");
        msg.setAuthType("dual");
        msg.setPublicKey("pubkey");
        msg.setPassword("pw");
        msg.setSignature("sig");
        msg.setSuccess(true);
        msg.setMessage("ok");
        byte[] json = objectMapper.writeValueAsBytes(msg);
        Message base = objectMapper.readValue(json, Message.class);
        AuthMessage deserialized = (AuthMessage) base;
        assertEquals(msg.getUsername(), deserialized.getUsername());
        assertEquals(msg.getAuthType(), deserialized.getAuthType());
        assertEquals(msg.getPublicKey(), deserialized.getPublicKey());
        assertEquals(msg.getPassword(), deserialized.getPassword());
        assertEquals(msg.getSignature(), deserialized.getSignature());
        assertEquals(msg.isSuccess(), deserialized.isSuccess());
        assertEquals(msg.getMessage(), deserialized.getMessage());
    }
} 