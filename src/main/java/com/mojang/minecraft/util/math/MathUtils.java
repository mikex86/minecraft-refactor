package com.mojang.minecraft.util.math;

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
}
