package com.mojang.minecraft.renderer.shader.impl;

import com.mojang.minecraft.renderer.shader.DelegatingShader;
import com.mojang.minecraft.renderer.shader.Shader;

import java.io.IOException;


public class OutlineShader extends DelegatingShader {

    public OutlineShader() throws IOException {
        super(Shader.fromPrecompiledBinaries("/shaders/outline.vert.spv", "/shaders/outline.frag.spv"));
    }
}
