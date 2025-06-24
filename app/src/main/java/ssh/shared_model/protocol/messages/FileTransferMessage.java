package ssh.shared_model.protocol.messages;

import com.fasterxml.jackson.annotation.JsonIgnore;

import ssh.shared_model.protocol.Message;
import ssh.shared_model.protocol.MessageType;

import java.util.Base64;

/**
 * Message for handling file transfer operations.
 */
public class FileTransferMessage extends Message {
    private String filename;
    private long fileSize;
    private String targetPath;
    private int sequenceNumber;
    private String data;
    private boolean isLast;
    private String status;
    private String message;

    public FileTransferMessage() {
        super(MessageType.FILE_UPLOAD_REQUEST);
    }

    public FileTransferMessage(MessageType type) {
        super(type);
    }

    @Override
    public byte[] serialize() {
        return super.serialize();
    }

    @Override
    public void deserialize(byte[] data) {
        super.deserialize(data);
    }

    // Getters and setters
    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getTargetPath() {
        return targetPath;
    }

    public void setTargetPath(String targetPath) {
        this.targetPath = targetPath;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public void setData(byte[] dataBytes) {
        this.data = Base64.getEncoder().encodeToString(dataBytes);
    }

    @JsonIgnore
    public byte[] getDataBytes() {
        return Base64.getDecoder().decode(data);
    }

    public boolean isLast() {
        return isLast;
    }

    public void setLast(boolean last) {
        isLast = last;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
} 