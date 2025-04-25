package com.mojang.minecraft.util.math;

import java.text.CharacterIterator;
import java.text.StringCharacterIterator;

public class MathUtils {

    public static int ceilFloor(float value) {
        if (value > 0) {
            return (int) value;
        } else {
            return (int) (value - 1);
        }
    }

    @SuppressWarnings("ManualMinMaxCalculation")
    public static int clamp(int value, int min, int max) {
        return value < min ? min : value > max ? max : value;
    }

    public static int log2(int value) {
        int a = 0;
        while (value > 1) {
            value = value >> 1;
            a++;
        }
        return a;
    }

    public static String humanReadableByteCountSI(long bytes) {
        if (-1000 < bytes && bytes < 1000) {
            return bytes + " B";
        }
        CharacterIterator ci = new StringCharacterIterator("kMGTPE");
        while (bytes <= -999_950 || bytes >= 999_950) {
            bytes /= 1000;
            ci.next();
        }
        return String.format("%.1f %cB", bytes / 1000.0, ci.current());
    }
}
