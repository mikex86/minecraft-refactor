package com.mojang.minecraft.renderer.shader.impl;

public interface LightingShader {
    void setLightUniforms(float lightDirX, float lightDirY, float lightDirZ, float lightColorR, float lightColorG, float lightColorB);
    void setAmbientLightUniforms(float ambientLightR, float ambientLightG, float ambientLightB);
}
