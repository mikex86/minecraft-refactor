package com.mojang.minecraft.renderer.shader.impl;

import com.mojang.minecraft.renderer.shader.DelegatingShader;
import com.mojang.minecraft.renderer.shader.Shader;

import java.io.IOException;

public class EntityShader extends DelegatingShader implements FogShader {

    public EntityShader() throws IOException {
        super(Shader.fromPrecompiledBinaries("/shaders/entity.vert.spv", "/shaders/entity.frag.spv"));
    }
}
