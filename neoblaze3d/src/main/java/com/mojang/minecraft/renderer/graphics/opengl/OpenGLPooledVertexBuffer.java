package com.mojang.minecraft.renderer.graphics.opengl;

import com.mojang.minecraft.renderer.graphics.VertexBuffer;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL15.*;

/**
 * OpenGL implementation of the VertexBuffer interface that uses a region
 * from a buffer pool instead of creating its own buffer.
 */
public class OpenGLPooledVertexBuffer implements VertexBuffer {
    // Buffer region from the pool
    private final OpenGLBufferAllocation region;

    // State tracking
    private boolean disposed = false;

    /**
     * Creates a new pooled OpenGL vertex buffer.
     *
     * @param region The buffer region from the pool
     */
    public OpenGLPooledVertexBuffer(OpenGLBufferAllocation region) {
        this.region = region;
    }

    @Override
    public void setData(ByteBuffer data, int sizeInBytes) {
        if (isDisposed()) {
            throw new IllegalStateException("Cannot use a disposed vertex buffer");
        }

        if (sizeInBytes > region.getSizeInBytes()) {
            throw new IllegalArgumentException("Data size exceeds buffer region size");
        }

        // Upload data to the buffer region
        glBindBuffer(GL_ARRAY_BUFFER, region.getBufferId());
        glBufferSubData(GL_ARRAY_BUFFER, region.getOffset(), data);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    @Override
    public void updateData(ByteBuffer data, int offsetInBytes, int sizeInBytes) {
        if (isDisposed()) {
            throw new IllegalStateException("Cannot use a disposed vertex buffer");
        }

        if (offsetInBytes + sizeInBytes > region.getSizeInBytes()) {
            throw new IllegalArgumentException("Update range exceeds buffer region size");
        }

        // Upload data to the buffer region
        glBindBuffer(GL_ARRAY_BUFFER, region.getBufferId());
        glBufferSubData(GL_ARRAY_BUFFER, region.getOffset() + offsetInBytes, data);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    @Override
    public long getSizeInBytes() {
        return region.getSizeInBytes();
    }

    @Override
    public void dispose() {
        if (!disposed) {
            // Free the region back to the pool
            region.free();
            disposed = true;
        }
    }

    @Override
    public boolean isDisposed() {
        return disposed || region.isFreed();
    }

    /**
     * Binds this VBO for rendering.
     * (Internal use by OpenGLGraphicsAPI)
     */
    void bind() {
        if (isDisposed()) {
            throw new IllegalStateException("Cannot bind a disposed vertex buffer");
        }

        glBindBuffer(GL_ARRAY_BUFFER, region.getBufferId());
    }

    /**
     * Unbinds this VBO.
     * (Internal use by OpenGLGraphicsAPI)
     */
    void unbind() {
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    /**
     * Gets the OpenGL buffer ID.
     *
     * @return The OpenGL buffer ID
     */
    int getBufferId() {
        return region.getBufferId();
    }

    /**
     * Gets the offset of this vertex buffer within the buffer.
     *
     * @return The offset in bytes
     */
    long getOffset() {
        return region.getOffset();
    }
} 
