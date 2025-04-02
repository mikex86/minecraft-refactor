package com.mojang.minecraft.renderer.shader.impl;

import com.mojang.minecraft.renderer.shader.Shader;

import java.io.IOException;

public class HudNoTexShader extends Shader {

    public HudNoTexShader() throws IOException {
        super("/shaders/hud_notexture.vert", "/shaders/hud_notexture.frag");
    }
}
