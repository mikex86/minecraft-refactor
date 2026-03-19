package com.mojang.minecraft.renderer.shader.impl;

import com.mojang.minecraft.renderer.shader.Shader;

import java.io.IOException;


public class OutlineShader extends Shader {

    public OutlineShader() throws IOException {
        super("/shaders/outline.vert", "/shaders/outline.frag");
    }
}
