package com.mojang.minecraft.renderer.graphics.opengl;

import com.mojang.minecraft.renderer.graphics.IndexBuffer;
import com.mojang.minecraft.renderer.graphics.ResourceState;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.jemalloc.JEmalloc;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL15.*;

/**
 * OpenGL implementation of the IndexBuffer interface that uses a region
 * from a buffer pool instead of creating its own buffer.
 */
public class OpenGLPooledIndexBuffer implements IndexBuffer, OpenGLBufferStateTracked {
    private static final int STACK_UPLOAD_THRESHOLD_BYTES = 16 * 1024;

    // Buffer region from the pool
    private final OpenGLBufferAllocation region;
    
    // Buffer state
    private int indexCount;
    
    // State tracking
    private boolean disposed = false;
    private ResourceState.BufferAccess bufferAccess = ResourceState.BufferAccess.UNDEFINED;
    
    /**
     * Creates a new pooled OpenGL index buffer.
     * 
     * @param region The buffer region from the pool
     */
    public OpenGLPooledIndexBuffer(OpenGLBufferAllocation region) {
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
        if (bufferAccess != ResourceState.BufferAccess.TRANSFER_DST) {
            throw new IllegalStateException(
                    "IndexBuffer setData requires state TRANSFER_DST but was " + bufferAccess
            );
        }
        
        if (sizeInBytes > region.getSizeInBytes()) {
            throw new IllegalArgumentException("Data size exceeds buffer region size");
        }
        
        // Calculate index count
        this.indexCount = sizeInBytes / 4; // 4 bytes per int
        
        if (sizeInBytes <= STACK_UPLOAD_THRESHOLD_BYTES) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer byteBuffer = stack.malloc(sizeInBytes);
                writeIntsAsBytes(data, sizeInBytes, byteBuffer);

                // Upload data to the buffer region
                glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, region.getBufferId());
                glBufferSubData(GL_ELEMENT_ARRAY_BUFFER, region.getOffset(), byteBuffer);
                glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
            }
        } else {
            ByteBuffer byteBuffer = JEmalloc.je_malloc(sizeInBytes);
            if (byteBuffer == null) {
                throw new OutOfMemoryError("jemalloc failed to allocate " + sizeInBytes + " bytes");
            }
            try {
                writeIntsAsBytes(data, sizeInBytes, byteBuffer);

                // Upload data to the buffer region
                glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, region.getBufferId());
                glBufferSubData(GL_ELEMENT_ARRAY_BUFFER, region.getOffset(), byteBuffer);
                glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
            } finally {
                JEmalloc.je_free(byteBuffer);
            }
        }
    }
    
    @Override
    public void updateData(IntBuffer data, int offsetInBytes, int sizeInBytes) {
        if (isDisposed()) {
            throw new IllegalStateException("Cannot use a disposed index buffer");
        }
        if (bufferAccess != ResourceState.BufferAccess.TRANSFER_DST) {
            throw new IllegalStateException(
                    "IndexBuffer updateData requires state TRANSFER_DST but was " + bufferAccess
            );
        }
        
        if (offsetInBytes + sizeInBytes > region.getSizeInBytes()) {
            throw new IllegalArgumentException("Update range exceeds buffer region size");
        }
        
        if (sizeInBytes <= STACK_UPLOAD_THRESHOLD_BYTES) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer byteBuffer = stack.malloc(sizeInBytes);
                writeIntsAsBytes(data, sizeInBytes, byteBuffer);

                // Upload data to the buffer region
                glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, region.getBufferId());
                glBufferSubData(GL_ELEMENT_ARRAY_BUFFER, region.getOffset() + offsetInBytes, byteBuffer);
                glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
            }
        } else {
            ByteBuffer byteBuffer = JEmalloc.je_malloc(sizeInBytes);
            if (byteBuffer == null) {
                throw new OutOfMemoryError("jemalloc failed to allocate " + sizeInBytes + " bytes");
            }
            try {
                writeIntsAsBytes(data, sizeInBytes, byteBuffer);

                // Upload data to the buffer region
                glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, region.getBufferId());
                glBufferSubData(GL_ELEMENT_ARRAY_BUFFER, region.getOffset() + offsetInBytes, byteBuffer);
                glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
            } finally {
                JEmalloc.je_free(byteBuffer);
            }
        }
    }

    private static void writeIntsAsBytes(IntBuffer data, int sizeInBytes, ByteBuffer byteBuffer) {
        // Save the buffer's position and limit.
        int originalPosition = data.position();
        int originalLimit = data.limit();
        try {
            // Only read up to the size needed in ints.
            int intCount = sizeInBytes / 4; // 4 bytes per int

            // Make sure we don't read past the end of the buffer.
            int limit = Math.min(originalPosition + intCount, originalLimit);
            data.limit(limit);

            // Copy the int data to the byte buffer.
            while (data.hasRemaining()) {
                byteBuffer.putInt(data.get());
            }
            byteBuffer.flip();
        } finally {
            // Always restore caller-owned buffer state.
            data.position(originalPosition);
            data.limit(originalLimit);
        }
    }
    
    @Override
    public long getSizeInBytes() {
        return region.getSizeInBytes();
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
    long getOffset() {
        return region.getOffset();
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
