package com.mojang.minecraft.renderer.shader.impl;

import com.mojang.minecraft.renderer.shader.Shader;

import java.io.IOException;

public class WorldShader extends Shader implements FogShader {

    public WorldShader() throws IOException {
        super("/shaders/world.vert", "/shaders/world.frag");
    }

}
