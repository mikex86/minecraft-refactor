package com.mojang.minecraft.renderer.shader.impl;

import com.mojang.minecraft.renderer.shader.DelegatingShader;
import com.mojang.minecraft.renderer.shader.Shader;

import java.io.IOException;

/**
 * Shader implementation for particle rendering.
 * Replaces fixed function particle rendering with a programmable pipeline.
 */
public class ParticleShader extends DelegatingShader {

    /**
     * Creates a new particle shader.
     *
     * @throws IOException If shader loading fails
     */
    public ParticleShader() throws IOException {
        super(Shader.fromPrecompiledBinaries("/shaders/particle.vert.spv", "/shaders/particle.frag.spv"));
    }

} 