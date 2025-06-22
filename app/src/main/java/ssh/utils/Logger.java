package ssh.utils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Enhanced logging utility that writes to files and provides clean console output.
 */
public class Logger {
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static LogLevel currentLevel = LogLevel.INFO;
    private static PrintWriter logWriter;
    private static boolean initialized = false;
    private static String logFile = "ssh.log";

    public enum LogLevel {
        DEBUG, INFO, WARN, ERROR
    }

    /**
     * Initialize the logger with a specific log file.
     */
    public static void initialize(String filename) {
        try {
            logFile = filename;
            Path logPath = Paths.get(logFile);
            
            // Create parent directories if they don't exist
            if (logPath.getParent() != null) {
                Files.createDirectories(logPath.getParent());
            }
            
            logWriter = new PrintWriter(new FileWriter(logFile, true), true);
            initialized = true;
            
            // Log initialization
            log(LogLevel.INFO, "Logger initialized - logging to: " + logFile);
        } catch (IOException e) {
            System.err.println("Failed to initialize logger: " + e.getMessage());
            // Fallback to console logging
            initialized = false;
        }
    }

    /**
     * Initialize the logger with default settings.
     */
    public static void initialize() {
        initialize("ssh.log");
    }

    /**
     * Set the current log level.
     */
    public static void setLogLevel(LogLevel level) {
        currentLevel = level;
    }

    /**
     * Log a debug message.
     */
    public static void debug(String message) {
        log(LogLevel.DEBUG, message);
    }

    /**
     * Log an info message.
     */
    public static void info(String message) {
        log(LogLevel.INFO, message);
    }

    /**
     * Log a warning message.
     */
    public static void warn(String message) {
        log(LogLevel.WARN, message);
    }

    /**
     * Log an error message.
     */
    public static void error(String message) {
        log(LogLevel.ERROR, message);
        // Show error messages on console for visibility
        System.err.println("ERROR: " + message);
    }

    /**
     * Log an error message with exception.
     */
    public static void error(String message, Throwable throwable) {
        log(LogLevel.ERROR, message);
        if (throwable != null) {
            log(LogLevel.ERROR, "Exception: " + throwable.getMessage());
            // Log full stack trace to file
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            throwable.printStackTrace(pw);
            log(LogLevel.ERROR, "Stack trace: " + sw.toString());
        }
        // Show error messages on console for visibility
        System.err.println("ERROR: " + message);
        if (throwable != null) {
            System.err.println("Exception: " + throwable.getMessage());
        }
    }

    /**
     * Internal logging method.
     */
    private static void log(LogLevel level, String message) {
        if (!initialized) {
            // If not initialized, just print to console as a fallback.
            // This prevents creating log files in unexpected locations.
            System.err.println("Logger not initialized. Message: " + message);
            return;
        }
        
        if (level.ordinal() >= currentLevel.ordinal()) {
            String timestamp = LocalDateTime.now().format(formatter);
            String threadName = Thread.currentThread().getName();
            String logEntry = String.format("[%s] [%s] [%s] %s", timestamp, level, threadName, message);
            
            // Write to file
            if (logWriter != null) {
                logWriter.println(logEntry);
            }
        }
    }

    /**
     * Get current log level.
     */
    public static LogLevel getCurrentLevel() {
        return currentLevel;
    }

    /**
     * Close the logger and flush any pending writes.
     */
    public static void close() {
        if (logWriter != null) {
            logWriter.close();
            initialized = false;
        }
    }

    /**
     * Get the current log file path.
     */
    public static String getLogFile() {
        return logFile;
    }
} 