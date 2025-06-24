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
     * Initialize the logger with a log file.
     */
    public static void initialize(String logFile) {
        try {
            Logger.logFile = logFile;
            File file = new File(logFile);
            file.getParentFile().mkdirs(); // Create parent directories if they don't exist
            
            // Test write to ensure we can write to the file
            try (FileWriter writer = new FileWriter(file, true)) {
                writer.write("");
            }
            
            initialized = true;
            info("Logger initialized: " + logFile);
            
        } catch (Exception e) {
            // Use System.err only for logger initialization failures
            System.err.println("Failed to initialize logger: " + e.getMessage());
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
     * Log a debug message (only if debug mode is enabled).
     */
    public static void debug(String message) {
        if (currentLevel.ordinal() <= LogLevel.DEBUG.ordinal() && initialized) {
            try (FileWriter writer = new FileWriter(logFile, true)) {
                String timestamp = getTimestamp();
                writer.write(timestamp + " DEBUG: " + message + "\n");
            } catch (IOException e) {
                // Use System.err only for logger failures
                System.err.println("Logger not initialized. Message: " + message);
            }
        }
    }

    /**
     * Log an info message.
     */
    public static void info(String message) {
        if (initialized) {
            try (FileWriter writer = new FileWriter(logFile, true)) {
                String timestamp = getTimestamp();
                writer.write(timestamp + " INFO: " + message + "\n");
            } catch (IOException e) {
                // Use System.err only for logger failures
                System.err.println("INFO: " + message);
            }
        } else {
            // Use System.err only when logger is not initialized
            System.err.println("INFO: " + message);
        }
    }

    /**
     * Log a warning message.
     */
    public static void warn(String message) {
        if (initialized) {
            try (FileWriter writer = new FileWriter(logFile, true)) {
                String timestamp = getTimestamp();
                writer.write(timestamp + " WARN: " + message + "\n");
            } catch (IOException e) {
                // Use System.err only for logger failures
                System.err.println("WARN: " + message);
            }
        } else {
            // Use System.err only when logger is not initialized
            System.err.println("WARN: " + message);
        }
    }

    /**
     * Log an error message.
     */
    public static void error(String message) {
        if (initialized) {
            try (FileWriter writer = new FileWriter(logFile, true)) {
                String timestamp = getTimestamp();
                writer.write(timestamp + " ERROR: " + message + "\n");
            } catch (IOException e) {
                // Use System.err only for logger failures
                System.err.println("ERROR: " + message);
            }
        } else {
            // Use System.err only when logger is not initialized
            System.err.println("ERROR: " + message);
        }
    }

    /**
     * Log an error message with exception.
     */
    public static void error(String message, Throwable throwable) {
        if (initialized) {
            try (FileWriter writer = new FileWriter(logFile, true)) {
                String timestamp = getTimestamp();
                writer.write(timestamp + " ERROR: " + message + "\n");
                writer.write(timestamp + " Exception: " + throwable.getMessage() + "\n");
            } catch (IOException e) {
                // Use System.err only for logger failures
                System.err.println("ERROR: " + message);
                System.err.println("Exception: " + throwable.getMessage());
            }
        } else {
            // Use System.err only when logger is not initialized
            System.err.println("ERROR: " + message);
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

    private static String getTimestamp() {
        return LocalDateTime.now().format(formatter);
    }
} 