package com.mojang.minecraft.renderer;

import com.mojang.minecraft.renderer.graphics.GraphicsAPI;

class HeadlessTriangleRenderOpenGLTest extends AbstractHeadlessTriangleRenderTest {
    @Override
    protected GraphicsAPI.Backend requestedBackend() {
        return GraphicsAPI.Backend.OPENGL;
    }
}
