package com.mojang.minecraft.renderer.graphics.opengl;

import com.mojang.minecraft.profiler.GpuMemoryTracker;
import com.mojang.minecraft.renderer.graphics.DataType;
import com.mojang.minecraft.renderer.graphics.VertexBuffer;

import java.nio.ByteBuffer;
import java.util.Objects;

import static org.lwjgl.opengl.GL15.*;

/**
 * OpenGL implementation of the VertexBuffer interface.
 * Represents a VBO (Vertex Buffer Object) in OpenGL.
 */
public class OpenGLVertexBuffer implements VertexBuffer {
    // OpenGL VBO ID
    private int vboId;

    // Buffer state
    private VertexFormat format;
    private int sizeInBytes;
    private int vertexCount;
    private final int usage;

    // State tracking
    private boolean disposed = false;

    /**
     * Creates a new OpenGL vertex buffer.
     *
     * @param usage The OpenGL usage hint
     */
    public OpenGLVertexBuffer(int usage) {
        this.vboId = glGenBuffers();
        this.usage = usage;
        this.format = null;
    }

    @Override
    public void setData(ByteBuffer data, int sizeInBytes) {
        Objects.requireNonNull(format, "Vertex format must be set before uploading data");

        if (isDisposed()) {
            throw new IllegalStateException("Cannot use a disposed vertex buffer");
        }

        // subtract previous size from allocated memory
        if (this.sizeInBytes != 0) {
            glBindBuffer(GL_ARRAY_BUFFER, vboId);
            GpuMemoryTracker.trackVbo(this.sizeInBytes, true);
        }

        this.sizeInBytes = sizeInBytes;

        // Calculate vertex count based on format stride
        this.vertexCount = sizeInBytes / format.getStrideInBytes();

        // Upload data to VBO
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferData(GL_ARRAY_BUFFER, data, usage);

        // Add the size to allocated memory
        GpuMemoryTracker.trackVbo(this.sizeInBytes, false);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    @Override
    public void updateData(ByteBuffer data, int offsetInBytes, int sizeInBytes) {
        Objects.requireNonNull(format, "Vertex format must be set before uploading data");

        if (isDisposed()) {
            throw new IllegalStateException("Cannot use a disposed vertex buffer");
        }

        if (offsetInBytes + sizeInBytes > this.sizeInBytes) {
            throw new IllegalArgumentException("Update range exceeds buffer size");
        }

        // Upload data to a specific portion of the VBO
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferSubData(GL_ARRAY_BUFFER, offsetInBytes, data);
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
        if (sizeInBytes > 0) {
            this.vertexCount = sizeInBytes / format.getStrideInBytes();
        }
    }

    @Override
    public long getSizeInBytes() {
        return sizeInBytes;
    }

    @Override
    public long getVertexCount() {
        return vertexCount;
    }

    @Override
    public void dispose() {
        if (!disposed) {
            glBindBuffer(GL_ARRAY_BUFFER, vboId);
            // track gpu memory de-allocation
            GpuMemoryTracker.trackVbo(this.sizeInBytes, true);
            glBindBuffer(GL_ARRAY_BUFFER, 0);

            glDeleteBuffers(vboId);
            vboId = 0;
            disposed = true;
        }
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }

    /**
     * Binds this VBO for rendering.
     * (Internal use by OpenGLGraphicsAPI)
     */
    void bind() {
        if (isDisposed()) {
            throw new IllegalStateException("Cannot bind a disposed vertex buffer");
        }

        glBindBuffer(GL_ARRAY_BUFFER, vboId);
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
        return vboId;
    }
} 