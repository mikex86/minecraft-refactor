package com.mojang.minecraft.renderer.shader.impl;

import com.mojang.minecraft.renderer.shader.DelegatingShader;
import com.mojang.minecraft.renderer.shader.Shader;

import java.io.IOException;

public class WorldShader extends DelegatingShader implements FogShader {

    public WorldShader() throws IOException {
        super(Shader.fromPrecompiledBinaries("/shaders/world.vert.spv", "/shaders/world.frag.spv"));
    }

    @Override
    public void setFogUniforms(float density, float start, float end, float r, float g, float b, float a) {
        setUniform("fogDensity", density);
        setUniform("fogStart", start);
        setUniform("fogEnd", end);
        setUniform("fogColor", r, g, b, a);
    }

}
