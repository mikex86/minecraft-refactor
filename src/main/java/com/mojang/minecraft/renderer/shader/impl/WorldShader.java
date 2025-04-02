package com.mojang.minecraft.renderer.shader.impl;

import java.io.IOException;

public class WorldShader extends AbstractFogShader {

    public WorldShader() throws IOException {
        super("/shaders/world.vert", "/shaders/world.frag");
    }

}
