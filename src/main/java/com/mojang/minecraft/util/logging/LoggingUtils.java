package com.mojang.minecraft.util.logging;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Utility methods for logging and error handling.
 * Provides a centralized place for logging configuration and error reporting.
 */
public class LoggingUtils {
    private static final Logger LOGGER = Logger.getLogger("Minecraft");
    private static final SimpleDateFormat LOG_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static boolean initialized = false;

    /**
     * Initializes the logging system.
     * Creates necessary directories and configures log handlers.
     */
    public static void initialize() {
        if (initialized) {
            return;
        }

        // Create crash-reports directory if it doesn't exist
        try {
            File crashReportsDir = new File("crash-reports");
            if (!crashReportsDir.exists()) {
                crashReportsDir.mkdirs();
            }
        } catch (Exception e) {
            System.err.println("Failed to create crash-reports directory: " + e.getMessage());
        }

        // Configure logging
        try {
            FileHandler fileHandler = new FileHandler("minecraft.log", true);
            fileHandler.setFormatter(new SimpleFormatter());
            LOGGER.addHandler(fileHandler);
            LOGGER.setLevel(Level.ALL);
        } catch (IOException e) {
            System.err.println("Failed to set up logging: " + e.getMessage());
        }

        initialized = true;
    }

    /**
     * Logs an exception with a custom message.
     *
     * @param message   The message describing the error
     * @param exception The exception that occurred
     */
    public static void logException(String message, Throwable exception) {
        if (!initialized) {
            initialize();
        }
        LOGGER.log(Level.SEVERE, message, exception);
    }

    /**
     * Logs a message at the specified level.
     *
     * @param level   The log level
     * @param message The message to log
     */
    public static void log(Level level, String message) {
        if (!initialized) {
            initialize();
        }
        LOGGER.log(level, message);
    }

    /**
     * Sets up an uncaught exception handler to catch any unhandled exceptions.
     * This ensures that all crashes are properly reported even if they're not caught.
     *
     * @param handler The handler function to call when an uncaught exception occurs
     */
    public static void setupUncaughtExceptionHandler(UncaughtExceptionHandler handler) {
        Thread.setDefaultUncaughtExceptionHandler((thread, exception) -> {
            logException("Uncaught exception in thread " + thread.getName(), exception);
            handler.handleUncaughtException(thread, exception);
        });
    }

    /**
     * Redirects System.err to the logger to capture any direct printStackTrace() calls.
     * This is a fallback measure for exception handling.
     */
    public static void redirectErrorOutput() {
        // Create a PrintStream that redirects to our logger
        PrintStream errorStream = new PrintStream(System.err) {
            @Override
            public void println(Object x) {
                super.println(x);
                if (x instanceof Throwable) {
                    logException("Uncaptured exception output:", (Throwable) x);
                }
            }
        };

        System.setErr(errorStream);
    }

    /**
     * Formats the current date and time as a string.
     *
     * @return Formatted date and time string
     */
    public static String getFormattedTime() {
        return LOG_DATE_FORMAT.format(new Date());
    }

    /**
     * Interface for handling uncaught exceptions.
     */
    public interface UncaughtExceptionHandler {
        /**
         * Called when an uncaught exception occurs.
         *
         * @param thread    The thread where the exception occurred
         * @param exception The uncaught exception
         */
        void handleUncaughtException(Thread thread, Throwable exception);
    }
} 