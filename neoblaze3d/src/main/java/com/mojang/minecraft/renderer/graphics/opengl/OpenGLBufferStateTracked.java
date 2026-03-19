package com.mojang.minecraft.renderer.graphics.opengl;

import com.mojang.minecraft.renderer.graphics.ResourceState;

interface OpenGLBufferStateTracked {
    ResourceState.BufferAccess getBufferAccess();

    void setBufferAccess(ResourceState.BufferAccess access);
}
