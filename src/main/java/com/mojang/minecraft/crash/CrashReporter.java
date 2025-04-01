package com.mojang.minecraft.crash;

import java.io.File;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Central handling point for all crash reports in the game.
 * Provides methods for logging exceptions and generating crash reports.
 */
public class CrashReporter {
    private static final Logger LOGGER = Logger.getLogger("Minecraft");
    private static final SimpleDateFormat LOG_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    /**
     * Logs an exception with a custom message.
     *
     * @param message   The message describing the error
     * @param exception The exception that occurred
     */
    public static void logException(String message, Throwable exception) {
        LOGGER.log(Level.SEVERE, message, exception);
    }

    /**
     * Generates a crash report for an exception.
     *
     * @param message   The message describing the error
     * @param exception The exception that occurred
     * @return The generated crash report
     */
    public static CrashReport generateCrashReport(String message, Throwable exception) {
        return new CrashReport(message, exception);
    }

    /**
     * Handles an exception by generating a crash report, saving it, and displaying a dialog.
     * This should be used for critical exceptions that prevent the game from continuing.
     *
     * @param message   The message describing the error
     * @param exception The exception that occurred
     */
    public static void handleCrash(String message, Throwable exception) {
        // Log the exception
        logException(message, exception);

        // Generate and save the crash report
        CrashReport report = generateCrashReport(message, exception);
        File reportFile = report.saveToFile();

        // Print the report to the console
        System.err.println("\n" + report.getCompleteReport());
        System.err.println("Crash report saved to: " + reportFile.getAbsolutePath());
    }

    /**
     * Handles a non-critical exception by logging it and optionally displaying a message.
     * This should be used for exceptions that allow the game to continue running.
     *
     * @param message   The message describing the error
     * @param exception The exception that occurred
     */
    public static void handleError(String message, Throwable exception) {
        // Log the exception
        logException(message, exception);

        // Print the exception to the console
        System.err.println("[" + LOG_DATE_FORMAT.format(new Date()) + "] [ERROR] " + message);
        exception.printStackTrace(System.err);
    }

    /**
     * Sets up an uncaught exception handler to catch any unhandled exceptions.
     * This ensures that all crashes are properly reported even if they're not caught.
     */
    public static void setupUncaughtExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler((thread, exception) -> {
            handleCrash("Uncaught exception in thread " + thread.getName(), exception);
        });
    }

    /**
     * Redirects System.err to the logger to capture any direct printStackTrace() calls that we missed.
     * This is a fallback measure and should not be relied upon for normal exception handling.
     */
    public static void redirectErrorOutput() {
        // Create a PrintStream that redirects to our logger
        PrintStream errorStream = new PrintStream(System.err) {
            @Override
            public void println(Object x) {
                super.println(x);
                if (x instanceof Throwable) {
                    LOGGER.log(Level.SEVERE, "Uncaptured exception output:", (Throwable) x);
                }
            }
        };

        System.setErr(errorStream);
    }
} 