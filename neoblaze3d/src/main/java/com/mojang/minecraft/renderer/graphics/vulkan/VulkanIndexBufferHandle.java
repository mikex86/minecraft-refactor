package com.mojang.minecraft.renderer.graphics.vulkan;

interface VulkanIndexBufferHandle {
    long getVkBufferHandle();

    long getVkBufferOffset();
}
