package com.mojang.minecraft.renderer.shader.impl;

import com.mojang.minecraft.renderer.shader.Shader;

import java.io.IOException;

public class HudShader extends Shader {

    public HudShader() throws IOException {
        super("/shaders/hud.vert", "/shaders/hud.frag");
    }
}
