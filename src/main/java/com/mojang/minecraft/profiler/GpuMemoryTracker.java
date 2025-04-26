package com.mojang.minecraft.profiler;

import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL15.*;

public class GpuMemoryTracker {

    public static long UPLOADED_GPU_MEMORY = 0;
    public static long TOTAL_GPU_MEMORY = 0;

    /**
     * Asserts the vbo is currently bound.
     *
     * @param sizeInBytes the size of the user buffer data
     * @param dispose     true if the buffer is being disposed, false if it is being created
     */
    public static void trackVbo(int sizeInBytes, boolean dispose) {
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
     *
     * @param sizeInBytes the size of the user buffer data
     * @param dispose     true if the buffer is being disposed, false if it is being created
     */
    public static void trackIbo(int sizeInBytes, boolean dispose) {
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

}
