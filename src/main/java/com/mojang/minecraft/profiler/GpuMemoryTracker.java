package com.mojang.minecraft.profiler;

import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL32.glGetBufferParameteri64v;

public class GpuMemoryTracker {

    // Total memory uploaded by user (actual buffer data)
    public static long UPLOADED_GPU_MEMORY = 0;
    
    // Total memory allocated on the GPU (includes pool sizes)
    public static long TOTAL_GPU_MEMORY = 0;
    
    // Memory allocated for pooled buffers
    public static long POOLED_GPU_MEMORY = 0;
    
    // Memory used within pooled buffers
    public static long POOLED_USED_GPU_MEMORY = 0;

    /**
     * Asserts the vbo is currently bound.
     * Tracks memory for a standard vertex buffer.
     *
     * @param sizeInBytes the size of the user buffer data
     * @param dispose     true if the buffer is being disposed, false if it is being created
     */
    public static void trackVbo(long sizeInBytes, boolean dispose) {
        if (dispose) {
            UPLOADED_GPU_MEMORY -= sizeInBytes;
        } else {
            UPLOADED_GPU_MEMORY += sizeInBytes;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer buffer = stack.mallocInt(1);
            glGetBufferParameteriv(GL_ARRAY_BUFFER, GL_BUFFER_SIZE, buffer);
            if (dispose) {
                TOTAL_GPU_MEMORY -= buffer.get(0);
            } else {
                TOTAL_GPU_MEMORY += buffer.get(0);
            }
        }
    }

    /**
     * Asserts the ibo is currently bound.
     * Tracks memory for a standard index buffer.
     *
     * @param sizeInBytes the size of the user buffer data
     * @param dispose     true if the buffer is being disposed, false if it is being created
     */
    public static void trackIbo(long sizeInBytes, boolean dispose) {
        if (dispose) {
            UPLOADED_GPU_MEMORY -= sizeInBytes;
        } else {
            UPLOADED_GPU_MEMORY += sizeInBytes;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer buffer = stack.mallocInt(1);
            glGetBufferParameteriv(GL_ELEMENT_ARRAY_BUFFER, GL_BUFFER_SIZE, buffer);
            if (dispose) {
                TOTAL_GPU_MEMORY -= buffer.get(0);
            } else {
                TOTAL_GPU_MEMORY += buffer.get(0);
            }
        }
    }
    
    /**
     * Tracks memory for a pooled vertex buffer.
     * This should be called when creating or disposing a buffer pool.
     * Asserts the buffer is currently bound.
     *
     * @param sizeInBytes the total size of the pool
     * @param dispose     true if the pool is being disposed, false if it is being created
     */
    public static void trackPooledBuffer(int bufferType, long sizeInBytes, boolean dispose) {
        if (dispose) {
            POOLED_GPU_MEMORY -= sizeInBytes;
        } else {
            POOLED_GPU_MEMORY += sizeInBytes;
        }
        
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer buffer = stack.mallocLong(1);
            glGetBufferParameteri64v(bufferType, GL_BUFFER_SIZE, buffer);
            if (dispose) {
                TOTAL_GPU_MEMORY -= buffer.get(0);
            } else {
                TOTAL_GPU_MEMORY += buffer.get(0);
            }
        }
    }
    
    /**
     * Tracks memory for data uploaded to a pooled buffer region.
     * This counts toward the user data but not toward the total allocated memory,
     * as the pool memory is already counted.
     *
     * @param sizeInBytes the size of the region being used
     * @param dispose     true if the region is being freed, false if it is being allocated
     */
    public static void trackPooledRegion(long sizeInBytes, boolean dispose) {
        if (dispose) {
            UPLOADED_GPU_MEMORY -= sizeInBytes;
            POOLED_USED_GPU_MEMORY -= sizeInBytes;
        } else {
            UPLOADED_GPU_MEMORY += sizeInBytes;
            POOLED_USED_GPU_MEMORY += sizeInBytes;
        }
    }
}
