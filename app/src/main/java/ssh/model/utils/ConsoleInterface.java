package ssh.model.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import ssh.model.utils.Logger;

/**
 * Clean console interface for user-friendly output.
 */
public class ConsoleInterface {
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static boolean verbose = false;

    /**
     * Set verbose mode for detailed output.
     */
    public static void setVerbose(boolean verbose) {
        ConsoleInterface.verbose = verbose;
    }

    /**
     * Print a clean status message.
     */
    public static void status(String message) {
        String timestamp = LocalDateTime.now().format(formatter);
        System.out.printf("[%s] %s%n", timestamp, message);
    }

    /**
     * Print a success message.
     */
    public static void success(String message) {
        String timestamp = LocalDateTime.now().format(formatter);
        System.out.printf("[%s] ✓ %s%n", timestamp, message);
    }

    /**
     * Print an error message.
     */
    public static void error(String message) {
        String timestamp = LocalDateTime.now().format(formatter);
        System.err.printf("[%s] ✗ %s%n", timestamp, message);
    }

    /**
     * Print a warning message.
     */
    public static void warning(String message) {
        String timestamp = LocalDateTime.now().format(formatter);
        System.out.printf("[%s] ⚠ %s%n", timestamp, message);
    }

    /**
     * Print an info message.
     */
    public static void info(String message) {
        String timestamp = LocalDateTime.now().format(formatter);
        System.out.printf("[%s] ℹ %s%n", timestamp, message);
    }

    /**
     * Print a debug message (only in verbose mode).
     */
    public static void debug(String message) {
        if (verbose) {
            String timestamp = LocalDateTime.now().format(formatter);
            System.out.printf("[%s] 🔍 %s%n", timestamp, message);
        }
    }

    /**
     * Print a connection status.
     */
    public static void connection(String status, String details) {
        String timestamp = LocalDateTime.now().format(formatter);
        String icon = "CONNECTED".equals(status) ? "🔗" : "🔌";
        System.out.printf("[%s] %s %s: %s%n", timestamp, icon, status, details);
    }

    /**
     * Print authentication status.
     */
    public static void auth(String username, boolean success) {
        String timestamp = LocalDateTime.now().format(formatter);
        String icon = success ? "🔐" : "❌";
        String status = success ? "SUCCESS" : "FAILED";
        System.out.printf("[%s] %s Authentication %s for user: %s%n", timestamp, icon, status, username);
    }

    /**
     * Print shell command execution.
     */
    public static void shell(String username, String command) {
        String timestamp = LocalDateTime.now().format(formatter);
        System.out.printf("[%s] 💻 %s executed: %s%n", timestamp, username, command);
    }

    /**
     * Display shell output with formatting.
     */
    public static void shellOutput(String output) {
        System.out.println("📄 Output:");
        System.out.println(output);
    }

    /**
     * Display a separator line.
     */
    public static void separator() {
        System.out.println("─".repeat(50));
    }

    /**
     * Display a header with title.
     */
    public static void header(String title) {
        System.out.println("🚀 " + title);
    }

    /**
     * Display a footer with message.
     */
    public static void footer(String message) {
        System.out.println("🏁 " + message);
    }

    /**
     * Display progress with dots.
     */
    public static void progress(String message) {
        System.out.print(message + "... ");
    }

    /**
     * Complete progress indicator.
     */
    public static void progressComplete() {
        System.out.println("done");
    }

    /**
     * Display a prompt.
     */
    public static void prompt(String message) {
        System.out.print(message + ": ");
    }
} 