package ssh.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Simple logging utility.
 */
public class Logger {
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static LogLevel currentLevel = LogLevel.INFO;

    public enum LogLevel {
        DEBUG, INFO, WARN, ERROR
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
    }

    /**
     * Log an error message with exception.
     */
    public static void error(String message, Throwable throwable) {
        log(LogLevel.ERROR, message);
        if (throwable != null) {
            log(LogLevel.ERROR, "Exception: " + throwable.getMessage());
            throwable.printStackTrace();
        }
    }

    /**
     * Internal logging method.
     */
    private static void log(LogLevel level, String message) {
        if (level.ordinal() >= currentLevel.ordinal()) {
            String timestamp = LocalDateTime.now().format(formatter);
            String threadName = Thread.currentThread().getName();
            System.out.printf("[%s] [%s] [%s] %s%n", timestamp, level, threadName, message);
        }
    }

    /**
     * Get current log level.
     */
    public static LogLevel getCurrentLevel() {
        return currentLevel;
    }
} 