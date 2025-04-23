package com.mojang.minecraft.gui.scaling;

public class ScaledResolution {

    public static float getScaledWidth(int width, int height) {
        return (width * 240f) / (float) height;
    }

    public static float getScaledHeight(int height) {
        return (height * 240f) / (float) height;
    }
}
