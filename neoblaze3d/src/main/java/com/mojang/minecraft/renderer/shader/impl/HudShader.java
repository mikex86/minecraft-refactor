package com.mojang.minecraft.renderer.shader.impl;

import com.mojang.minecraft.renderer.shader.DelegatingShader;
import com.mojang.minecraft.renderer.shader.Shader;

import java.io.IOException;

public class HudShader extends DelegatingShader {

    public HudShader() throws IOException {
        super(Shader.fromPrecompiledBinaries("/shaders/hud.vert.spv", "/shaders/hud.frag.spv"));
    }
}
