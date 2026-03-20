package com.mojang.minecraft.renderer.graphics.vulkan;

import com.mojang.minecraft.renderer.graphics.ResourceState;

interface VulkanBufferStateTracked {
    ResourceState.BufferAccess getBufferAccess();

    void setBufferAccess(ResourceState.BufferAccess access);
}
