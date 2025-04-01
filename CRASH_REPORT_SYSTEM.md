# Minecraft Crash Reporting System

## Overview

This document describes the crash reporting system implemented in this Minecraft fork. The system handles exceptions in a centralized way, providing detailed crash reports and avoiding the scattered `printStackTrace()` calls that were present in the original code.

## Key Components

### CrashReport Class

The `CrashReport` class represents a crash report that contains information about an exception, including:

- Description of what happened when the crash occurred
- Stack trace of the exception
- Additional information about the game state
- System details (OS, Java version, memory usage)

Each crash report can be saved to a file in the `crash-reports` directory with a timestamp and description.

### CrashReporter Class

The `CrashReporter` class provides static methods for handling exceptions:

- `handleCrash`: For critical exceptions that prevent the game from continuing
- `handleError`: For non-critical exceptions that allow the game to continue
- `logException`: For logging exceptions to the logger
- `generateCrashReport`: For generating a crash report from an exception

The class also sets up an uncaught exception handler and redirects standard error output to catch any direct `printStackTrace()` calls that might have been missed during refactoring.

#### Platform-agnostic Crash Notification

The crash reporting system uses a platform-agnostic approach to displaying crash notifications:

1. First tries to show a simple JOptionPane dialog without parent frames to avoid Look and Feel issues
2. If that fails, falls back to a prominent console message
3. Attempts to play a system beep to alert the user when UI elements fail
4. Forces application exit after ensuring the user has been notified

This approach avoids issues with Swing's Metal Look and Feel initialization, which can hang the application when creating new JFrame or JDialog components in error conditions.

## Usage

### For Critical Exceptions

```java
try {
    // Code that might throw an exception
} catch (Exception e) {
    CrashReporter.handleCrash("Failed to initialize game", e);
    // Game will exit after showing a crash dialog
}
```

### For Non-Critical Exceptions

```java
try {
    // Code that might throw an exception
} catch (Exception e) {
    CrashReporter.handleError("Failed to load resource", e, true);
    // Game will continue running, optionally showing a dialog
}
```

## Testing the Crash System

### Using Command Line Arguments

The game includes command line arguments for testing the crash reporting system:

```
// Test a simple crash
java -jar minecraft.jar --test-crash

// Test a nested exception
java -jar minecraft.jar --test-crash-nested

// Test a crash in a background thread
java -jar minecraft.jar --test-crash-background

// Test a non-critical error
java -jar minecraft.jar --test-error
```

These options allow you to verify that the crash reporting system is working correctly without needing to modify the code.

### Using the CrashTester Class

The `CrashTester` utility class provides methods for testing different crash scenarios:

```java
// Cause a simple crash
CrashTester.causeSimpleCrash();

// Cause a nested exception
CrashTester.causeNestedCrash();

// Cause an OutOfMemoryError
CrashTester.causeOutOfMemoryError();

// Cause a crash in a background thread
CrashTester.causeBackgroundThreadCrash();

// Cause a non-critical error
CrashTester.causeNonCriticalError(true);
```

## Crash Report Format

Crash reports are saved in the following format:

```
---- Minecraft Crash Report ----
// [Random comment]

Time: [Timestamp]
Description: [Description]

-- Stack Trace --
[Exception stack trace]

-- Additional Information --
[Any additional information added]

-- System Details --
Minecraft Version: c0.0.11a
Operating System: [OS details]
Java Version: [Java details]
Java VM Version: [JVM details]
Memory: [Memory usage]
```

## Implementation Details

The crash reporting system is initialized in the `Minecraft.main()` method before anything else runs. It sets up:

1. The crash-reports directory
2. File-based logging
3. An uncaught exception handler
4. Redirected error output

All `printStackTrace()` calls in the original code have been replaced with calls to the appropriate methods in the `CrashReporter` class.

### Thread Safety

The crash reporting system is designed to be thread-safe:

- Dialog boxes are always displayed on the Event Dispatch Thread when available
- For crashes in non-UI threads, `SwingUtilities.invokeAndWait()` is used
- Fallback mechanisms are in place if the EDT is not accessible
- System output streams are flushed before showing dialogs
- Critical operations use appropriate synchronization

### Platform Issues and Mitigations

The system is designed to handle several platform-specific issues:

- **Metal Look and Feel Initialization**: The system avoids creating new heavyweight components during crash handling as this can hang the application
- **OpenGL Context**: Avoids UI operations that might require access to the OpenGL context which may be in an invalid state during a crash
- **Focus Management**: Uses multiple fallback mechanisms to ensure the user is notified even if normal UI methods fail
- **Clean Exit**: Forces a system exit after crash notification to prevent zombie processes

## Future Improvements

Future improvements to the crash reporting system could include:

1. Additional game state information in crash reports
2. Better categorization of crashes
3. Automatic reporting to a server
4. Recovery mechanisms for non-critical crashes
5. Option to restart the game automatically after a crash 