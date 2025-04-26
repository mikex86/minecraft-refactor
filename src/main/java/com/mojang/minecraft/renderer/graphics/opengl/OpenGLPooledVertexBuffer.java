package com.mojang.minecraft.renderer.graphics.opengl;

import com.mojang.minecraft.profiler.NativeMemoryTracker;
import com.mojang.minecraft.renderer.graphics.VertexBuffer;
import com.mojang.minecraft.renderer.graphics.opengl.OpenGLBufferPool.BufferRegion;
import org.lwjgl.BufferUtils;
import org.lwjgl.system.jemalloc.JEmalloc;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Objects;

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
        this.format = new VertexFormat(true, false, false, false, false);
    }

    @Override
    public void setData(FloatBuffer data, int sizeInBytes) {
        if (isDisposed()) {
            throw new IllegalStateException("Cannot use a disposed vertex buffer");
        }

        if (sizeInBytes > region.getSize()) {
            throw new IllegalArgumentException("Data size exceeds buffer region size");
        }

        // Calculate vertex count based on format stride
        this.vertexCount = sizeInBytes / (format.getStride() * 4L); // 4 bytes per float

        ByteBuffer byteBuffer = JEmalloc.je_malloc(sizeInBytes);
        Objects.requireNonNull(byteBuffer, "Failed to allocate ByteBuffer via JEmalloc");
        NativeMemoryTracker.ALLOCATED_NATIVE_MEMORY.addAndGet(sizeInBytes);

        // Save the buffer's position and limit
        int originalPosition = data.position();
        int originalLimit = data.limit();

        // Only read up to the size needed in floats
        int floatCount = sizeInBytes / 4; // 4 bytes per float

        // Make sure we don't read past the end of the buffer
        int limit = Math.min(originalPosition + floatCount, originalLimit);
        data.limit(limit);

        // Copy the float data to the byte buffer
        while (data.hasRemaining()) {
            byteBuffer.putFloat(data.get());
        }

        // Reset buffer positions
        data.position(originalPosition);
        data.limit(originalLimit);
        byteBuffer.flip();

        // Upload data to the buffer region
        glBindBuffer(GL_ARRAY_BUFFER, region.getBufferId());
        glBufferSubData(GL_ARRAY_BUFFER, region.getOffset(), byteBuffer);
        glBindBuffer(GL_ARRAY_BUFFER, 0);

        // Free the cpu buffer
        JEmalloc.je_free(byteBuffer);
        NativeMemoryTracker.ALLOCATED_NATIVE_MEMORY.addAndGet(-sizeInBytes);
    }

    @Override
    public void updateData(FloatBuffer data, int offsetInBytes, int sizeInBytes) {
        if (isDisposed()) {
            throw new IllegalStateException("Cannot use a disposed vertex buffer");
        }

        if (offsetInBytes + sizeInBytes > region.getSize()) {
            throw new IllegalArgumentException("Update range exceeds buffer region size");
        }

        ByteBuffer byteBuffer = JEmalloc.je_malloc(sizeInBytes);
        Objects.requireNonNull(byteBuffer, "Failed to allocate ByteBuffer via JEmalloc");
        NativeMemoryTracker.ALLOCATED_NATIVE_MEMORY.addAndGet(sizeInBytes);

        // Save the buffer's position and limit
        int originalPosition = data.position();
        int originalLimit = data.limit();

        // Only read up to the size needed in floats
        int floatCount = sizeInBytes / 4; // 4 bytes per float

        // Make sure we don't read past the end of the buffer
        int limit = Math.min(originalPosition + floatCount, originalLimit);
        data.limit(limit);

        // Copy the float data to the byte buffer
        while (data.hasRemaining()) {
            byteBuffer.putFloat(data.get());
        }

        // Reset buffer positions
        data.position(originalPosition);
        data.limit(originalLimit);
        byteBuffer.flip();

        // Upload data to the buffer region
        glBindBuffer(GL_ARRAY_BUFFER, region.getBufferId());
        glBufferSubData(GL_ARRAY_BUFFER, region.getOffset() + offsetInBytes, byteBuffer);
        glBindBuffer(GL_ARRAY_BUFFER, 0);

        // Free the cpu buffer
        JEmalloc.je_free(byteBuffer);
        NativeMemoryTracker.ALLOCATED_NATIVE_MEMORY.addAndGet(-sizeInBytes);
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
            this.vertexCount = region.getSize() / (format.getStride() * 4L); // 4 bytes per float
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