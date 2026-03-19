package com.mojang.minecraft.renderer.shader.impl;

import com.mojang.minecraft.renderer.shader.IShader;

public interface FogShader extends IShader {

    default void setFogUniforms(float density, float start, float end, float r, float g, float b, float a) {
        // Set fog parameters
        setUniform("fogDensity", density);
        setUniform("fogStart", start);
        setUniform("fogEnd", end);

        // Set fog color
        setUniform("fogColor", r, g, b, a);
    }
}
