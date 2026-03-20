package com.mojang.minecraft.renderer;

import com.mojang.minecraft.renderer.graphics.GraphicsAPI;

class HeadlessTriangleRenderVulkanTest extends AbstractHeadlessTriangleRenderTest {
    @Override
    protected GraphicsAPI.Backend requestedBackend() {
        return GraphicsAPI.Backend.VULKAN;
    }
}
