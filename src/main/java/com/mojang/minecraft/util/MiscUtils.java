package com.mojang.minecraft.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Miscellaneous utility methods that don't fit into other utility categories.
 */
public class MiscUtils {

    /**
     * Ensures a directory exists, creating it if necessary.
     *
     * @param path The directory path to check/create
     * @return true if the directory exists or was created, false otherwise
     */
    public static boolean ensureDirectoryExists(String path) {
        File dir = new File(path);
        if (!dir.exists()) {
            return dir.mkdirs();
        }
        return dir.isDirectory();
    }

    /**
     * Extracts a resource from the JAR to a file.
     *
     * @param resourcePath The resource path inside the JAR
     * @param outputPath   The output file path
     * @return true if extraction succeeded, false otherwise
     */
    public static boolean extractResource(String resourcePath, String outputPath) {
        try (InputStream in = MiscUtils.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                return false;
            }

            Path outPath = Paths.get(outputPath);
            Files.createDirectories(outPath.getParent());
            Files.copy(in, outPath, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Formats a float value with specified precision.
     *
     * @param value     The float value to format
     * @param precision The number of decimal places
     * @return The formatted string
     */
    public static String formatFloat(float value, int precision) {
        return String.format("%." + precision + "f", value);
    }

    /**
     * Clamps a value between a minimum and maximum.
     *
     * @param value The value to clamp
     * @param min   The minimum allowed value
     * @param max   The maximum allowed value
     * @return The clamped value
     */
    public static float clamp(float value, float min, float max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    /**
     * Clamps a value between a minimum and maximum.
     *
     * @param value The value to clamp
     * @param min   The minimum allowed value
     * @param max   The maximum allowed value
     * @return The clamped value
     */
    public static int clamp(int value, int min, int max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    /**
     * Linear interpolation between two values.
     *
     * @param a First value
     * @param b Second value
     * @param t Interpolation factor (0.0-1.0)
     * @return Interpolated value
     */
    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /**
     * Converts degrees to radians.
     *
     * @param degrees Angle in degrees
     * @return Angle in radians
     */
    public static float toRadians(float degrees) {
        return degrees * (float) Math.PI / 180.0f;
    }

    /**
     * Converts radians to degrees.
     *
     * @param radians Angle in radians
     * @return Angle in degrees
     */
    public static float toDegrees(float radians) {
        return radians * 180.0f / (float) Math.PI;
    }
} 