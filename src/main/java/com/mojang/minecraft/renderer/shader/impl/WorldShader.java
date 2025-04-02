package com.mojang.minecraft.renderer.shader.impl;

import com.mojang.minecraft.renderer.graphics.GraphicsAPI;
import com.mojang.minecraft.renderer.shader.Shader;

import java.io.IOException;

public class WorldShader extends Shader {

    public WorldShader() throws IOException {
        super("/shaders/world.vert", "/shaders/world.frag");
    }

    public void setFogUniforms(boolean enabled, GraphicsAPI.FogMode mode, float density, float start, float end, float r, float g, float b, float a) {
        setUniform("fogEnabled", enabled);

        // Set fog mode as int (0=LINEAR, 1=EXP, 2=EXP2)
        int modeValue = 0;
        switch (mode) {
            case LINEAR:
                modeValue = 0;
                break;
            case EXP:
                modeValue = 1;
                break;
            case EXP2:
                modeValue = 2;
                break;
        }
        setUniform("fogMode", modeValue);

        // Set fog parameters
        setUniform("fogDensity", density);
        setUniform("fogStart", start);
        setUniform("fogEnd", end);

        // Set fog color
        setUniform("fogColor", r, g, b, a);
    }
}
