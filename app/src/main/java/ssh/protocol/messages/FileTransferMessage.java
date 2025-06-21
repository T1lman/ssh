package ssh.protocol.messages;

import ssh.protocol.Message;
import ssh.protocol.MessageType;

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
        StringBuilder sb = new StringBuilder();
        if (filename != null) {
            sb.append("filename:").append(filename).append(";");
        }
        if (fileSize > 0) {
            sb.append("fileSize:").append(fileSize).append(";");
        }
        if (targetPath != null) {
            sb.append("targetPath:").append(targetPath).append(";");
        }
        if (sequenceNumber > 0) {
            sb.append("sequenceNumber:").append(sequenceNumber).append(";");
        }
        if (data != null) {
            sb.append("data:").append(data).append(";");
        }
        if (getType() == MessageType.FILE_DATA) {
            sb.append("isLast:").append(isLast).append(";");
        }
        if (status != null) {
            sb.append("status:").append(status).append(";");
        }
        if (message != null) {
            sb.append("message:").append(message).append(";");
        }
        return sb.toString().getBytes();
    }

    @Override
    public void deserialize(byte[] data) {
        String dataStr = new String(data);
        String[] parts = dataStr.split(";");
        
        for (String part : parts) {
            if (part.startsWith("filename:")) {
                this.filename = part.substring(9);
            } else if (part.startsWith("fileSize:")) {
                this.fileSize = Long.parseLong(part.substring(9));
            } else if (part.startsWith("targetPath:")) {
                this.targetPath = part.substring(11);
            } else if (part.startsWith("sequenceNumber:")) {
                this.sequenceNumber = Integer.parseInt(part.substring(14));
            } else if (part.startsWith("data:")) {
                this.data = part.substring(5);
            } else if (part.startsWith("isLast:")) {
                this.isLast = Boolean.parseBoolean(part.substring(7));
            } else if (part.startsWith("status:")) {
                this.status = part.substring(7);
            } else if (part.startsWith("message:")) {
                this.message = part.substring(8);
            }
        }
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