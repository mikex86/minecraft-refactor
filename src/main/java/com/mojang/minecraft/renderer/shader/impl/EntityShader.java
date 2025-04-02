package com.mojang.minecraft.renderer.shader.impl;

import java.io.IOException;

public class EntityShader extends AbstractFogShader {

    public EntityShader() throws IOException {
        super("/shaders/entity.vert", "/shaders/entity.frag");
    }
}
