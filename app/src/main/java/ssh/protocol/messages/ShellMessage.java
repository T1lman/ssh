package ssh.protocol.messages;

import ssh.protocol.Message;
import ssh.protocol.MessageType;

/**
 * Message for handling shell commands and results.
 */
public class ShellMessage extends Message {
    private String command;
    private String workingDirectory;
    private int exitCode;
    private String stdout;
    private String stderr;

    public ShellMessage() {
        super(MessageType.SHELL_COMMAND);
    }

    public ShellMessage(MessageType type) {
        super(type);
    }

    @Override
    public byte[] serialize() {
        StringBuilder sb = new StringBuilder();
        if (command != null) {
            sb.append("command:").append(command).append(";");
        }
        if (workingDirectory != null) {
            sb.append("workingDirectory:").append(workingDirectory).append(";");
        }
        if (getType() == MessageType.SHELL_RESULT) {
            sb.append("exitCode:").append(exitCode).append(";");
            if (stdout != null) {
                sb.append("stdout:").append(stdout).append(";");
            }
            if (stderr != null) {
                sb.append("stderr:").append(stderr).append(";");
            }
        }
        return sb.toString().getBytes();
    }

    @Override
    public void deserialize(byte[] data) {
        String dataStr = new String(data);
        String[] parts = dataStr.split(";");
        
        for (String part : parts) {
            if (part.startsWith("command:")) {
                this.command = part.substring(8);
            } else if (part.startsWith("workingDirectory:")) {
                this.workingDirectory = part.substring(17);
            } else if (part.startsWith("exitCode:")) {
                this.exitCode = Integer.parseInt(part.substring(9));
            } else if (part.startsWith("stdout:")) {
                this.stdout = part.substring(7);
            } else if (part.startsWith("stderr:")) {
                this.stderr = part.substring(7);
            }
        }
    }

    // Getters and setters
    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public String getWorkingDirectory() {
        return workingDirectory;
    }

    public void setWorkingDirectory(String workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    public int getExitCode() {
        return exitCode;
    }

    public void setExitCode(int exitCode) {
        this.exitCode = exitCode;
    }

    public String getStdout() {
        return stdout;
    }

    public void setStdout(String stdout) {
        this.stdout = stdout;
    }

    public String getStderr() {
        return stderr;
    }

    public void setStderr(String stderr) {
        this.stderr = stderr;
    }
} 