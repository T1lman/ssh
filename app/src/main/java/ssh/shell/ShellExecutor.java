package ssh.shell;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * Executes shell commands and returns results.
 */
public class ShellExecutor {
    
    public ShellExecutor() {
    }

    /**
     * Execute a command in the specified working directory.
     */
    public CommandResult execute(String command, String workingDirectory) {
        long startTime = System.currentTimeMillis();
        
        try {
            ProcessBuilder processBuilder = new ProcessBuilder();
            
            // Set the command based on the operating system
            if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                processBuilder.command("cmd", "/c", command);
            } else {
                processBuilder.command("sh", "-c", command);
            }
            
            // Set working directory if specified
            if (workingDirectory != null && !workingDirectory.isEmpty()) {
                processBuilder.directory(new File(workingDirectory));
            }
            
            // Redirect error stream to output stream
            processBuilder.redirectErrorStream(false);
            
            // Start the process
            Process process = processBuilder.start();
            
            // Read output and error streams in separate threads
            StringBuffer stdoutBuffer = new StringBuffer();
            StringBuffer stderrBuffer = new StringBuffer();
            
            Thread stdoutThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stdoutBuffer.append(line).append("\n");
                    }
                } catch (IOException e) {
                    stderrBuffer.append("Error reading stdout: ").append(e.getMessage()).append("\n");
                }
            });
            
            Thread stderrThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stderrBuffer.append(line).append("\n");
                    }
                } catch (IOException e) {
                    stderrBuffer.append("Error reading stderr: ").append(e.getMessage()).append("\n");
                }
            });
            
            stdoutThread.start();
            stderrThread.start();
            
            // Wait for the process to complete
            int exitCode = process.waitFor();
            
            // Wait for output threads to complete
            stdoutThread.join();
            stderrThread.join();
            
            long executionTime = System.currentTimeMillis() - startTime;
            
            return new CommandResult(
                exitCode,
                stdoutBuffer.toString(),
                stderrBuffer.toString(),
                executionTime
            );
            
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            return new CommandResult(
                -1,
                "",
                "Error executing command: " + e.getMessage(),
                executionTime
            );
        }
    }

    /**
     * Execute a command in the current working directory.
     */
    public CommandResult execute(String command) {
        return execute(command, null);
    }

    /**
     * Check if a command is safe to execute (basic security check).
     */
    public boolean isCommandSafe(String command) {
        if (command == null || command.trim().isEmpty()) {
            return false;
        }
        
        String lowerCommand = command.toLowerCase().trim();
        
        // List of potentially dangerous commands
        String[] dangerousCommands = {
            "rm -rf", "rm -r", "rm -f", "rm -rf /", "rm -r /", "rm -f /",
            "format", "fdisk", "mkfs", "dd", "shutdown", "halt", "reboot",
            "init 0", "init 6", "poweroff", "sudo", "su", "passwd"
        };
        
        for (String dangerous : dangerousCommands) {
            if (lowerCommand.contains(dangerous)) {
                return false;
            }
        }
        
        return true;
    }

    /**
     * Get the current working directory.
     */
    public String getCurrentWorkingDirectory() {
        return System.getProperty("user.dir");
    }

    /**
     * Change the current working directory.
     */
    public boolean changeWorkingDirectory(String path) {
        try {
            File newDir = new File(path);
            if (newDir.exists() && newDir.isDirectory()) {
                System.setProperty("user.dir", newDir.getAbsolutePath());
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
} 