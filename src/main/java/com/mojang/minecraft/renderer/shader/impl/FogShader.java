package com.mojang.minecraft.renderer.shader.impl;

import com.mojang.minecraft.renderer.graphics.GraphicsAPI;

public interface FogShader {

    void setFogUniforms(boolean enabled, GraphicsAPI.FogMode mode, float density, float start, float end, float r, float g, float b, float a);
}
