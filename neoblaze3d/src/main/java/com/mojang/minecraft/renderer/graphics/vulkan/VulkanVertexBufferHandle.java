package com.mojang.minecraft.renderer.graphics.vulkan;

interface VulkanVertexBufferHandle {
    long getVkBufferHandle();

    long getVkBufferOffset();
}
