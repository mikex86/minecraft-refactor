package com.mojang.minecraft.renderer.shader.impl;

import com.mojang.minecraft.renderer.shader.DelegatingShader;
import com.mojang.minecraft.renderer.shader.Shader;

import java.io.IOException;

public class HudNoTexShader extends DelegatingShader {

    public HudNoTexShader() throws IOException {
        super(Shader.fromPrecompiledBinaries("/shaders/hud_notexture.vert.spv", "/shaders/hud_notexture.frag.spv"));
    }
}
