package com.mojang.minecraft.renderer.graphics.opengl;

import com.mojang.minecraft.renderer.graphics.IndexBuffer;

import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL15.*;

/**
 * OpenGL implementation of the IndexBuffer interface.
 * Represents an IBO (Index Buffer Object) in OpenGL.
 */
public class OpenGLIndexBuffer implements IndexBuffer {
    // OpenGL IBO ID
    private final int iboId;
    
    // Buffer state
    private int sizeInBytes;
    private int indexCount;
    private final int usage;
    
    // State tracking
    private boolean disposed = false;
    
    /**
     * Creates a new OpenGL index buffer.
     * 
     * @param usage The OpenGL usage hint
     */
    public OpenGLIndexBuffer(int usage) {
        this.iboId = glGenBuffers();
        this.usage = usage;
    }
    
    /**
     * Binds this index buffer.
     */
    public void bind() {
        if (isDisposed()) {
            throw new IllegalStateException("Cannot use a disposed index buffer");
        }
        
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, iboId);
    }
    
    /**
     * Unbinds this index buffer.
     */
    public void unbind() {
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
    }
    
    @Override
    public void setData(IntBuffer data, int sizeInBytes) {
        if (isDisposed()) {
            throw new IllegalStateException("Cannot use a disposed index buffer");
        }
        
        this.sizeInBytes = sizeInBytes;
        
        // Calculate index count
        this.indexCount = sizeInBytes / 4; // 4 bytes per int
        
        // Upload data to IBO
        bind();
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, data, usage);
        unbind();
    }
    
    @Override
    public void updateData(IntBuffer data, int offsetInBytes, int sizeInBytes) {
        if (isDisposed()) {
            throw new IllegalStateException("Cannot use a disposed index buffer");
        }
        
        // Upload data to IBO
        bind();
        glBufferSubData(GL_ELEMENT_ARRAY_BUFFER, offsetInBytes, data);
        unbind();
    }
    
    @Override
    public int getSizeInBytes() {
        return sizeInBytes;
    }
    
    @Override
    public int getIndexCount() {
        return indexCount;
    }
    
    @Override
    public boolean isDisposed() {
        return disposed;
    }
    
    @Override
    public void dispose() {
        if (!disposed) {
            glDeleteBuffers(iboId);
            disposed = true;
        }
    }
} 