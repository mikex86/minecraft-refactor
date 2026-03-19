package com.mojang.minecraft.renderer.graphics.opengl;

import com.mojang.minecraft.renderer.graphics.ResourceState;

interface OpenGLTextureStateTracked {
    ResourceState.TextureAccess getTextureAccess();

    void setTextureAccess(ResourceState.TextureAccess access);
}
