package com.mojang.minecraft.renderer.shader.impl;

import com.mojang.minecraft.renderer.graphics.GraphicsAPI;
import com.mojang.minecraft.renderer.shader.Shader;

import java.io.IOException;

public abstract class AbstractFogShader extends Shader implements FogShader {

    /**
     * Creates a new shader from the specified vertex and fragment shader sources.
     *
     * @param vertexPath   Path to the vertex shader source
     * @param fragmentPath Path to the fragment shader source
     * @throws IOException If shader loading fails
     */
    public AbstractFogShader(String vertexPath, String fragmentPath) throws IOException {
        super(vertexPath, fragmentPath);
    }

    @Override
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
