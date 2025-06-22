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
        MessageType type = getType();

        if (filename != null) {
            sb.append("filename:").append(filename).append(";");
        }
        if (fileSize > 0) {
            sb.append("fileSize:").append(fileSize).append(";");
        }
        if (targetPath != null) {
            sb.append("targetPath:").append(targetPath).append(";");
        }
        if (type == MessageType.FILE_DATA || type == MessageType.FILE_ACK) {
            sb.append("sequenceNumber:").append(sequenceNumber).append(";");
        }
        if (data != null) {
            sb.append("data:").append(data).append(";");
        }
        if (type == MessageType.FILE_DATA) {
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
    public void deserialize(byte[] dataBytes) {
        String dataStr = new String(dataBytes);
        int lastIndex = 0;
        while (lastIndex < dataStr.length()) {
            int keyEnd = dataStr.indexOf(':', lastIndex);
            if (keyEnd == -1) {
                break; // No more keys
            }

            int valueEnd = dataStr.indexOf(';', keyEnd);
            if (valueEnd == -1) {
                valueEnd = dataStr.length(); // Last value
            }

            String key = dataStr.substring(lastIndex, keyEnd);
            String value = dataStr.substring(keyEnd + 1, valueEnd);

            switch (key) {
                case "filename":
                    this.filename = value;
                    break;
                case "fileSize":
                    this.fileSize = Long.parseLong(value);
                    break;
                case "targetPath":
                    this.targetPath = value;
                    break;
                case "sequenceNumber":
                    this.sequenceNumber = Integer.parseInt(value);
                    break;
                case "data":
                    this.data = value;
                    break;
                case "isLast":
                    this.isLast = Boolean.parseBoolean(value);
                    break;
                case "status":
                    this.status = value;
                    break;
                case "message":
                    this.message = value;
                    break;
                default:
                    // Unknown key, just ignore it
                    break;
            }

            lastIndex = valueEnd + 1;
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