package com.mojang.minecraft.renderer.shader.impl;

import com.mojang.minecraft.renderer.shader.Shader;

import java.io.IOException;

public class EntityShader extends Shader implements FogShader {

    public EntityShader() throws IOException {
        super("/shaders/entity.vert", "/shaders/entity.frag");
    }
}
