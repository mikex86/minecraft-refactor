package com.mojang.minecraft.renderer.graphics.opengl;

import com.mojang.minecraft.profiler.GpuMemoryTracker;
import com.mojang.minecraft.renderer.graphics.ResourceState;
import com.mojang.minecraft.renderer.graphics.VertexBuffer;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL15.*;

/**
 * OpenGL implementation of the VertexBuffer interface.
 * Represents a VBO (Vertex Buffer Object) in OpenGL.
 */
public class OpenGLVertexBuffer implements VertexBuffer, OpenGLBufferStateTracked {
    // OpenGL VBO ID
    private int vboId;

    private int sizeInBytes;
    private final int usage;

    // State tracking
    private boolean disposed = false;
    private ResourceState.BufferAccess bufferAccess = ResourceState.BufferAccess.UNDEFINED;

    /**
     * Creates a new OpenGL vertex buffer.
     *
     * @param usage The OpenGL usage hint
     */
    public OpenGLVertexBuffer(int usage) {
        this.vboId = glGenBuffers();
        this.usage = usage;
    }

    @Override
    public void setData(ByteBuffer data, int sizeInBytes) {
        if (isDisposed()) {
            throw new IllegalStateException("Cannot use a disposed vertex buffer");
        }
        if (bufferAccess != ResourceState.BufferAccess.TRANSFER_DST) {
            throw new IllegalStateException(
                    "VertexBuffer setData requires state TRANSFER_DST but was " + bufferAccess
            );
        }

        // subtract previous size from allocated memory
        if (this.sizeInBytes != 0) {
            glBindBuffer(GL_ARRAY_BUFFER, vboId);
            GpuMemoryTracker.trackVbo(this.sizeInBytes, true);
        }

        this.sizeInBytes = sizeInBytes;

        // Upload data to VBO
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferData(GL_ARRAY_BUFFER, data, usage);

        // Add the size to allocated memory
        GpuMemoryTracker.trackVbo(this.sizeInBytes, false);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    @Override
    public void updateData(ByteBuffer data, int offsetInBytes, int sizeInBytes) {
        if (isDisposed()) {
            throw new IllegalStateException("Cannot use a disposed vertex buffer");
        }
        if (bufferAccess != ResourceState.BufferAccess.TRANSFER_DST) {
            throw new IllegalStateException(
                    "VertexBuffer updateData requires state TRANSFER_DST but was " + bufferAccess
            );
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
    public long getSizeInBytes() {
        return sizeInBytes;
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

    @Override
    public ResourceState.BufferAccess getBufferAccess() {
        return bufferAccess;
    }

    @Override
    public void setBufferAccess(ResourceState.BufferAccess access) {
        if (access == null) {
            throw new IllegalArgumentException("access cannot be null");
        }
        this.bufferAccess = access;
    }
}
