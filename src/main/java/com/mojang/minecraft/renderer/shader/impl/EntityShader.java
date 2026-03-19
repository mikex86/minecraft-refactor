package com.mojang.minecraft.renderer.shader.impl;

import com.mojang.minecraft.renderer.shader.DelegatingShader;
import com.mojang.minecraft.renderer.shader.Shader;

import java.io.IOException;

public class EntityShader extends DelegatingShader implements FogShader {

    public EntityShader() throws IOException {
        super(Shader.fromPrecompiledBinaries("/shaders/entity.vert.spv", "/shaders/entity.frag.spv"));
    }

    @Override
    public void setFogUniforms(float density, float start, float end, float r, float g, float b, float a) {
        setUniform("fogDensity", density);
        setUniform("fogStart", start);
        setUniform("fogEnd", end);
        setUniform("fogColor", r, g, b, a);
    }
}
