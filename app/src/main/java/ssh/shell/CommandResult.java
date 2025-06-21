package ssh.shell;

/**
 * Represents the result of a command execution.
 */
public class CommandResult {
    private int exitCode;
    private String stdout;
    private String stderr;
    private long executionTime;

    public CommandResult(int exitCode, String stdout, String stderr) {
        this.exitCode = exitCode;
        this.stdout = stdout;
        this.stderr = stderr;
        this.executionTime = System.currentTimeMillis();
    }

    public CommandResult(int exitCode, String stdout, String stderr, long executionTime) {
        this.exitCode = exitCode;
        this.stdout = stdout;
        this.stderr = stderr;
        this.executionTime = executionTime;
    }

    /**
     * Check if the command executed successfully.
     */
    public boolean isSuccess() {
        return exitCode == 0;
    }

    /**
     * Get the exit code.
     */
    public int getExitCode() {
        return exitCode;
    }

    public void setExitCode(int exitCode) {
        this.exitCode = exitCode;
    }

    /**
     * Get the standard output.
     */
    public String getStdout() {
        return stdout;
    }

    public void setStdout(String stdout) {
        this.stdout = stdout;
    }

    /**
     * Get the standard error.
     */
    public String getStderr() {
        return stderr;
    }

    public void setStderr(String stderr) {
        this.stderr = stderr;
    }

    /**
     * Get the execution time in milliseconds.
     */
    public long getExecutionTime() {
        return executionTime;
    }

    public void setExecutionTime(long executionTime) {
        this.executionTime = executionTime;
    }

    /**
     * Get the combined output (stdout + stderr).
     */
    public String getCombinedOutput() {
        StringBuilder sb = new StringBuilder();
        if (stdout != null && !stdout.isEmpty()) {
            sb.append(stdout);
        }
        if (stderr != null && !stderr.isEmpty()) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(stderr);
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "CommandResult{" +
                "exitCode=" + exitCode +
                ", stdout='" + stdout + '\'' +
                ", stderr='" + stderr + '\'' +
                ", executionTime=" + executionTime +
                '}';
    }
} 