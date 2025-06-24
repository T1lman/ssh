package ssh.shared_model.shell;

import java.io.File;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Executes shell commands and returns results, maintaining a persistent working directory for a session.
 */
public class ShellExecutor {
    private Path currentDirectory;

    /**
     * Constructor initializes the executor in the user's home directory.
     */
    public ShellExecutor() {
        this.currentDirectory = Paths.get(System.getProperty("user.home"));
    }
    
    /**
     * Execute a command. If the command is 'cd', the directory is changed.
     * Otherwise, the command is executed in the current working directory.
     */
    public CommandResult execute(String command) {
        if (command == null || command.trim().isEmpty()) {
            return new CommandResult(0, "", "", 0);
        }

        String[] parts = command.trim().split("\\s+");
        
        if (parts[0].equals("cd")) {
            if (parts.length > 1) {
                return changeWorkingDirectory(parts[1]);
            } else {
                return changeWorkingDirectory(System.getProperty("user.home"));
            }
        }
        
        return executeProcess(command);
    }
    
    /**
     * Executes the given command in a separate process.
     */
    private CommandResult executeProcess(String command) {
        long startTime = System.currentTimeMillis();
        
        try {
            ProcessBuilder processBuilder = new ProcessBuilder();
            
            if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                processBuilder.command("cmd", "/c", command);
            } else {
                processBuilder.command("sh", "-c", command);
            }
            
            processBuilder.directory(currentDirectory.toFile());
            processBuilder.redirectErrorStream(true); // Combine stdout and stderr
            
            Process process = processBuilder.start();
            
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            int exitCode = process.waitFor();
            long executionTime = System.currentTimeMillis() - startTime;
            
            // Since we redirected error stream, output contains both stdout and stderr
            return new CommandResult(exitCode, output.toString(), "", executionTime);
            
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            return new CommandResult(-1, "", "Error executing command: " + e.getMessage(), executionTime);
        }
    }
    
    /**
     * Change the current working directory.
     */
    private CommandResult changeWorkingDirectory(String path) {
        Path newPath;
        if (path.equals("~")) {
            newPath = Paths.get(System.getProperty("user.home"));
        } else {
            newPath = currentDirectory.resolve(path).normalize();
        }

        File newDir = newPath.toFile();
        if (newDir.exists() && newDir.isDirectory()) {
            this.currentDirectory = newPath;
            return new CommandResult(0, "", "", 0);
        } else {
            return new CommandResult(1, "", "cd: no such file or directory: " + path + "\n", 0);
        }
    }
    
    /**
     * Get the current working directory as a string.
     */
    public String getCurrentWorkingDirectory() {
        return currentDirectory.toString();
    }
    
    /**
     * Check if a command is safe to execute (basic security check).
     * This is a placeholder and should be replaced with a more robust implementation.
     */
    public boolean isCommandSafe(String command) {
        // This is a simplistic check and should be improved for a real-world application.
        return true;
    }
} 