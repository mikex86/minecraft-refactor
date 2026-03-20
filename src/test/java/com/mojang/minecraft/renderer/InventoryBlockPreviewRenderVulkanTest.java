package com.mojang.minecraft.renderer;

import com.mojang.minecraft.renderer.graphics.GraphicsAPI;

class InventoryBlockPreviewRenderVulkanTest extends AbstractInventoryBlockPreviewRenderTest {
    @Override
    protected GraphicsAPI.Backend requestedBackend() {
        return GraphicsAPI.Backend.VULKAN;
    }
}
