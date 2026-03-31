package com.mojang.minecraft.renderer.graphics.vulkan;

import com.mojang.minecraft.renderer.graphics.allocator.BufferAllocation;

interface VulkanBufferAllocation extends BufferAllocation {
    long getBuffer();

    long getMemory();

    long getOffset();

    int getUsageFlags();

    int getMemoryPropertyFlags();
}
