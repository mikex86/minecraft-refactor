package com.mojang.minecraft.renderer.graphics.opengl;

import com.mojang.minecraft.renderer.graphics.DataType;
import com.mojang.minecraft.renderer.graphics.VertexBuffer;
import com.mojang.minecraft.renderer.graphics.opengl.OpenGLBufferPool.BufferRegion;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL15.*;

/**
 * OpenGL implementation of the VertexBuffer interface that uses a region
 * from a buffer pool instead of creating its own buffer.
 */
public class OpenGLPooledVertexBuffer implements VertexBuffer {
    // Buffer region from the pool
    private final BufferRegion region;

    // Buffer state
    private VertexFormat format;
    private long vertexCount;

    // State tracking
    private boolean disposed = false;

    /**
     * Creates a new pooled OpenGL vertex buffer.
     *
     * @param region The buffer region from the pool
     */
    public OpenGLPooledVertexBuffer(BufferRegion region) {
        this.region = region;
        this.format = new VertexFormat(
                DataType.BYTE, null, null, null, null,
                true, false, false, false, false);
    }

    @Override
    public void setData(ByteBuffer data, int sizeInBytes) {
        if (isDisposed()) {
            throw new IllegalStateException("Cannot use a disposed vertex buffer");
        }

        if (sizeInBytes > region.getSize()) {
            throw new IllegalArgumentException("Data size exceeds buffer region size");
        }

        // Calculate vertex count based on format stride
        this.vertexCount = sizeInBytes / format.getStrideInBytes();

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

        if (offsetInBytes + sizeInBytes > region.getSize()) {
            throw new IllegalArgumentException("Update range exceeds buffer region size");
        }

        // Upload data to the buffer region
        glBindBuffer(GL_ARRAY_BUFFER, region.getBufferId());
        glBufferSubData(GL_ARRAY_BUFFER, region.getOffset() + offsetInBytes, data);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    @Override
    public VertexFormat getFormat() {
        return format;
    }

    @Override
    public void setFormat(VertexFormat format) {
        this.format = format;

        // Recalculate vertex count if the buffer has data
        if (region.getSize() > 0) {
            this.vertexCount = region.getSize() / format.getStrideInBytes();
        }
    }

    @Override
    public long getSizeInBytes() {
        return region.getSize();
    }

    @Override
    public long getVertexCount() {
        return vertexCount;
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