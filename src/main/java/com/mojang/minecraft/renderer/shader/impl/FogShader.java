package com.mojang.minecraft.renderer.shader.impl;

public interface FogShader {
    void setFogUniforms(float density, float start, float end, float r, float g, float b, float a);
}
