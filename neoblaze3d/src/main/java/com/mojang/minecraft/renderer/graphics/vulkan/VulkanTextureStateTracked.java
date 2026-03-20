package com.mojang.minecraft.renderer.graphics.vulkan;

import com.mojang.minecraft.renderer.graphics.ResourceState;

interface VulkanTextureStateTracked {
    ResourceState.TextureAccess getTextureAccess();

    void setTextureAccess(ResourceState.TextureAccess access);
}
