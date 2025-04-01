package com.mojang.minecraft.crash;

import com.mojang.minecraft.util.logging.LoggingUtils;

import java.io.File;

/**
 * Central handling point for all crash reports in the game.
 * Provides methods for logging exceptions and generating crash reports.
 */
public class CrashReporter {

    /**
     * Logs an exception with a custom message.
     *
     * @param message   The message describing the error
     * @param exception The exception that occurred
     */
    public static void logException(String message, Throwable exception) {
        LoggingUtils.logException(message, exception);
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
        System.err.println("[" + LoggingUtils.getFormattedTime() + "] [ERROR] " + message);
        exception.printStackTrace(System.err);
    }
} 