package com.mojang.minecraft.renderer.shader.impl;

import com.mojang.minecraft.renderer.shader.IShader;

public interface LightingShader extends IShader {

    default void setLightUniforms(float lightDirX, float lightDirY, float lightDirZ, float lightColorR, float lightColorG, float lightColorB) {
        setUniform("lightDirection", lightDirX, lightDirY, lightDirZ);
        setUniform("lightColor", lightColorR, lightColorG, lightColorB);
    }

    default void setAmbientLightUniforms(float ambientLightR, float ambientLightG, float ambientLightB) {
        setUniform("ambientColor", ambientLightR, ambientLightG, ambientLightB);
    }

}
