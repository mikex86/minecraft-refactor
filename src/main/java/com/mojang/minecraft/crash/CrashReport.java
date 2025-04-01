package com.mojang.minecraft.crash;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Represents a crash report that can be saved to a file and displayed to the user.
 * Collects information about exceptions and system state when a crash occurs.
 */
public class CrashReport {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss");
    private static final String CRASH_REPORT_DIR = "crash-reports";

    private final String description;
    private final Throwable cause;
    private final Date crashTime;
    private final List<String> additionalInfo = new ArrayList<>();
    private String reportContent;

    /**
     * Creates a new crash report with the specified description and cause.
     *
     * @param description A description of what happened when the crash occurred
     * @param cause       The exception that caused the crash
     */
    public CrashReport(String description, Throwable cause) {
        this.description = description;
        this.cause = cause;
        this.crashTime = new Date();
    }

    /**
     * Adds additional information to the crash report.
     *
     * @param name  The name of the information
     * @param value The value of the information
     */
    public void addDetail(String name, String value) {
        this.additionalInfo.add(name + ": " + value);
    }

    /**
     * Gets the formatted contents of the crash report.
     *
     * @return The formatted crash report
     */
    public String getCompleteReport() {
        if (this.reportContent != null) {
            return this.reportContent;
        }

        StringBuilder report = new StringBuilder();

        report.append("---- Minecraft Crash Report ----\n");
        report.append("// ").append(getRandomComment()).append("\n\n");
        report.append("Time: ").append(DATE_FORMAT.format(this.crashTime)).append("\n");
        report.append("Description: ").append(this.description).append("\n\n");

        // Add stack trace
        report.append("-- Stack Trace --\n");
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        this.cause.printStackTrace(printWriter);
        report.append(stringWriter).append("\n");

        // Add additional information
        if (!this.additionalInfo.isEmpty()) {
            report.append("-- Additional Information --\n");
            for (String info : this.additionalInfo) {
                report.append(info).append("\n");
            }
            report.append("\n");
        }

        // Add system details
        report.append("-- System Details --\n");
        report.append("Minecraft Version: c0.0.11a\n");
        report.append("Operating System: ").append(System.getProperty("os.name")).append(" (")
                .append(System.getProperty("os.arch")).append(") version ")
                .append(System.getProperty("os.version")).append("\n");
        report.append("Java Version: ").append(System.getProperty("java.version")).append(", ")
                .append(System.getProperty("java.vendor")).append("\n");
        report.append("Java VM Version: ").append(System.getProperty("java.vm.name")).append(" (")
                .append(System.getProperty("java.vm.info")).append("), ")
                .append(System.getProperty("java.vm.vendor")).append("\n");
        report.append("Memory: ").append(Runtime.getRuntime().freeMemory() / 1024L / 1024L).append("MB / ")
                .append(Runtime.getRuntime().totalMemory() / 1024L / 1024L).append("MB up to ")
                .append(Runtime.getRuntime().maxMemory() / 1024L / 1024L).append("MB\n");

        this.reportContent = report.toString();
        return this.reportContent;
    }

    /**
     * Saves the crash report to a file in the crash-reports directory.
     *
     * @return The file where the crash report was saved, or null if it couldn't be saved
     */
    public File saveToFile() {
        try {
            File crashReportsDir = new File(CRASH_REPORT_DIR);
            if (!crashReportsDir.exists() && !crashReportsDir.mkdirs()) {
                System.err.println("Failed to create crash report directory: " + crashReportsDir.getAbsolutePath());
                return null;
            }

            String dateTime = DATE_FORMAT.format(this.crashTime);
            String safeName = this.description.replaceAll("[^a-zA-Z0-9_\\-]", "_");
            String fileName = "crash-" + dateTime + "-" + safeName + ".txt";

            File reportFile = new File(crashReportsDir, fileName);
            try (FileOutputStream stream = new FileOutputStream(reportFile);
                 PrintWriter writer = new PrintWriter(stream)) {
                writer.println(getCompleteReport());
            }

            return reportFile;
        } catch (Exception e) {
            System.err.println("Failed to save crash report to file:");
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Gets a random comment for the crash report.
     *
     * @return A random comment
     */
    private String getRandomComment() {
        String[] comments = {
                "Oh no! Something went wrong!",
                "Don't be sad, have a hug!",
                "I let you down. Sorry :(",
                "There's no place like ~",
                "Oops.",
                "Why did you do that?",
                "Surprise! Haha. Well, this is awkward.",
                "I blame Notch.",
                "This doesn't make any sense!",
                "But it worked in my world...",
                "Quite honestly, I wouldn't worry myself about that.",
                "I feel sad now :(",
                "My bad.",
                "I just don't know what went wrong :(",
                "You're mean."
        };

        return comments[(int) (Math.random() * comments.length)];
    }
} 