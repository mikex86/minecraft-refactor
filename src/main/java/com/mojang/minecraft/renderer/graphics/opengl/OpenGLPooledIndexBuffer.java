package com.mojang.minecraft.renderer.graphics.opengl;

import com.mojang.minecraft.renderer.graphics.IndexBuffer;
import com.mojang.minecraft.renderer.graphics.opengl.OpenGLBufferPool.BufferRegion;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import org.lwjgl.BufferUtils;

import static org.lwjgl.opengl.GL15.*;

/**
 * OpenGL implementation of the IndexBuffer interface that uses a region
 * from a buffer pool instead of creating its own buffer.
 */
public class OpenGLPooledIndexBuffer implements IndexBuffer {
    // Buffer region from the pool
    private final BufferRegion region;
    
    // Buffer state
    private int indexCount;
    
    // State tracking
    private boolean disposed = false;
    
    /**
     * Creates a new pooled OpenGL index buffer.
     * 
     * @param region The buffer region from the pool
     */
    public OpenGLPooledIndexBuffer(BufferRegion region) {
        this.region = region;
    }
    
    /**
     * Binds this index buffer.
     */
    public void bind() {
        if (isDisposed()) {
            throw new IllegalStateException("Cannot use a disposed index buffer");
        }
        
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, region.getBufferId());
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
        
        if (sizeInBytes > region.getSize()) {
            throw new IllegalArgumentException("Data size exceeds buffer region size");
        }
        
        // Calculate index count
        this.indexCount = sizeInBytes / 4; // 4 bytes per int
        
        // Create a ByteBuffer using LWJGL's BufferUtils to ensure direct allocation
        ByteBuffer byteBuffer = BufferUtils.createByteBuffer(sizeInBytes);
        
        // Save the buffer's position and limit
        int originalPosition = data.position();
        int originalLimit = data.limit();
        
        // Only read up to the size needed in ints
        int intCount = sizeInBytes / 4; // 4 bytes per int
        
        // Make sure we don't read past the end of the buffer
        int limit = Math.min(originalPosition + intCount, originalLimit);
        data.limit(limit);
        
        // Copy the int data to the byte buffer
        while (data.hasRemaining()) {
            byteBuffer.putInt(data.get());
        }
        
        // Reset buffer positions
        data.position(originalPosition);
        data.limit(originalLimit);
        byteBuffer.flip();
        
        // Upload data to the buffer region
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, region.getBufferId());
        glBufferSubData(GL_ELEMENT_ARRAY_BUFFER, region.getOffset(), byteBuffer);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
    }
    
    @Override
    public void updateData(IntBuffer data, int offsetInBytes, int sizeInBytes) {
        if (isDisposed()) {
            throw new IllegalStateException("Cannot use a disposed index buffer");
        }
        
        if (offsetInBytes + sizeInBytes > region.getSize()) {
            throw new IllegalArgumentException("Update range exceeds buffer region size");
        }
        
        // Create a ByteBuffer using LWJGL's BufferUtils to ensure direct allocation
        ByteBuffer byteBuffer = BufferUtils.createByteBuffer(sizeInBytes);
        
        // Save the buffer's position and limit
        int originalPosition = data.position();
        int originalLimit = data.limit();
        
        // Only read up to the size needed in ints
        int intCount = sizeInBytes / 4; // 4 bytes per int
        
        // Make sure we don't read past the end of the buffer
        int limit = Math.min(originalPosition + intCount, originalLimit);
        data.limit(limit);
        
        // Copy the int data to the byte buffer
        while (data.hasRemaining()) {
            byteBuffer.putInt(data.get());
        }
        
        // Reset buffer positions
        data.position(originalPosition);
        data.limit(originalLimit);
        byteBuffer.flip();
        
        // Upload data to the buffer region
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, region.getBufferId());
        glBufferSubData(GL_ELEMENT_ARRAY_BUFFER, region.getOffset() + offsetInBytes, byteBuffer);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
    }
    
    @Override
    public int getSizeInBytes() {
        return region.getSize();
    }
    
    @Override
    public int getIndexCount() {
        return indexCount;
    }
    
    @Override
    public boolean isDisposed() {
        return disposed || region.isFreed();
    }
    
    @Override
    public void dispose() {
        if (!disposed) {
            // Free the region back to the pool
            region.free();
            disposed = true;
        }
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
     * Gets the offset of this index buffer within the buffer.
     *
     * @return The offset in bytes
     */
    int getOffset() {
        return region.getOffset();
    }
} 