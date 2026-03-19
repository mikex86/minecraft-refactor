package com.mojang.minecraft.renderer.graphics.opengl;

import com.mojang.minecraft.renderer.graphics.allocator.BufferAllocation;

/**
 * OpenGL-specific allocation metadata.
 * <p>
 * Can represent a pooled region inside a larger GL buffer or a dedicated GL buffer.
 */
public interface OpenGLBufferAllocation extends BufferAllocation {

    /**
     * @return Byte offset within the OpenGL buffer backing this allocation.
     */
    long getOffset();

    int getBufferId();

    int getBufferType();

}
