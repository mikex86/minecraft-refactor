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

    private static void addUploaded(long bytes) {
        UPLOADED_GPU_MEMORY += bytes;
        if (UPLOADED_GPU_MEMORY < 0) {
            UPLOADED_GPU_MEMORY = 0;
        }
    }

    private static void addTotal(long bytes) {
        TOTAL_GPU_MEMORY += bytes;
        if (TOTAL_GPU_MEMORY < 0) {
            TOTAL_GPU_MEMORY = 0;
        }
    }

    private static void addPooled(long bytes) {
        POOLED_GPU_MEMORY += bytes;
        if (POOLED_GPU_MEMORY < 0) {
            POOLED_GPU_MEMORY = 0;
        }
    }

    private static void addPooledUsed(long bytes) {
        POOLED_USED_GPU_MEMORY += bytes;
        if (POOLED_USED_GPU_MEMORY < 0) {
            POOLED_USED_GPU_MEMORY = 0;
        }
    }

    /**
     * Tracks non-pooled buffer allocation memory (for backends that already
     * know the final allocated byte size).
     */
    public static void trackBufferAllocation(long sizeInBytes, boolean dispose) {
        addTotal(dispose ? -sizeInBytes : sizeInBytes);
    }

    /**
     * Tracks non-pooled uploaded user data memory.
     */
    public static void trackBufferUpload(long sizeInBytes, boolean dispose) {
        addUploaded(dispose ? -sizeInBytes : sizeInBytes);
    }

    /**
     * Tracks pooled allocator backing memory (counts toward total GPU memory).
     */
    public static void trackPooledBufferBytes(long sizeInBytes, boolean dispose) {
        addPooled(dispose ? -sizeInBytes : sizeInBytes);
        addTotal(dispose ? -sizeInBytes : sizeInBytes);
    }

    /**
     * Tracks pooled region usage (counts toward uploaded and pooled-used memory).
     */
    public static void trackPooledRegionBytes(long sizeInBytes, boolean dispose) {
        addUploaded(dispose ? -sizeInBytes : sizeInBytes);
        addPooledUsed(dispose ? -sizeInBytes : sizeInBytes);
    }

    /**
     * Asserts the vbo is currently bound.
     * Tracks memory for a standard vertex buffer.
     *
     * @param sizeInBytes the size of the user buffer data
     * @param dispose     true if the buffer is being disposed, false if it is being created
     */
    public static void trackVbo(long sizeInBytes, boolean dispose) {
        trackBufferUpload(sizeInBytes, dispose);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer buffer = stack.mallocInt(1);
            glGetBufferParameteriv(GL_ARRAY_BUFFER, GL_BUFFER_SIZE, buffer);
            trackBufferAllocation(buffer.get(0), dispose);
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
        trackBufferUpload(sizeInBytes, dispose);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer buffer = stack.mallocInt(1);
            glGetBufferParameteriv(GL_ELEMENT_ARRAY_BUFFER, GL_BUFFER_SIZE, buffer);
            trackBufferAllocation(buffer.get(0), dispose);
        }
    }
    
    /**
     * Tracks memory for a pooled vertex buffer.
     * This should be called when creating or disposing a buffer pool.
     * Asserts the buffer is currently bound.
     *
     * @param bufferType  the type of buffer (GL_ARRAY_BUFFER or GL_ELEMENT_ARRAY_BUFFER)
     * @param sizeInBytes the total size of the pool
     * @param dispose     true if the pool is being disposed, false if it is being created
     */
    public static void trackPooledBuffer(int bufferType, long sizeInBytes, boolean dispose) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer buffer = stack.mallocLong(1);
            glGetBufferParameteri64v(bufferType, GL_BUFFER_SIZE, buffer);
            long actualSize = buffer.get(0);
            trackPooledBufferBytes(actualSize, dispose);
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
        trackPooledRegionBytes(sizeInBytes, dispose);
    }
}
