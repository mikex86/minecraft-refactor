package com.mojang.minecraft.renderer.graphics.opengl;

import com.mojang.minecraft.profiler.GpuMemoryTracker;
import com.mojang.minecraft.renderer.graphics.allocator.BufferAllocator;
import com.mojang.minecraft.renderer.graphics.annotation.RenderThreadOnly;

import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_DYNAMIC_DRAW;
import static org.lwjgl.opengl.GL15.GL_ELEMENT_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL15.glDeleteBuffers;
import static org.lwjgl.opengl.GL15.glGenBuffers;

/**
 * Dedicated OpenGL allocator: one GL buffer object per allocation.
 */
final class OpenGLDedicatedAllocator implements BufferAllocator<OpenGLBufferAllocation> {
    private final int bufferType;
    private final Map<Integer, DedicatedAllocation> activeAllocations = new HashMap<>();
    private boolean disposed;
    private long allocatedBytes;
    private int allocationCount;

    OpenGLDedicatedAllocator(int bufferType) {
        this.bufferType = bufferType;
    }

    @RenderThreadOnly
    @Override
    public OpenGLBufferAllocation allocate(int sizeInBytes) {
        if (disposed) {
            throw new IllegalStateException("Cannot allocate from a disposed allocator");
        }
        if (sizeInBytes <= 0) {
            throw new IllegalArgumentException("Allocation size must be greater than zero");
        }

        int bufferId = glGenBuffers();
        glBindBuffer(bufferType, bufferId);
        glBufferData(bufferType, sizeInBytes, bufferType == GL_ARRAY_BUFFER ? GL_DYNAMIC_DRAW : GL_STATIC_DRAW);
        GpuMemoryTracker.trackPooledBuffer(bufferType, sizeInBytes, false);
        glBindBuffer(bufferType, 0);

        DedicatedAllocation allocation = new DedicatedAllocation(this, bufferId, bufferType, sizeInBytes);
        activeAllocations.put(bufferId, allocation);
        allocatedBytes += sizeInBytes;
        allocationCount++;
        return allocation;
    }

    @Override
    public String getStats() {
        return "DedicatedAllocator(type=" + (bufferType == GL_ARRAY_BUFFER ? "VBO" : "IBO")
                + ", active=" + activeAllocations.size()
                + ", allocatedMB=" + String.format("%.2f", allocatedBytes / (1024.0 * 1024.0))
                + ", allocations=" + allocationCount + ")";
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        for (DedicatedAllocation allocation : activeAllocations.values().toArray(new DedicatedAllocation[0])) {
            allocation.free();
        }
        activeAllocations.clear();
        disposed = true;
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }

    private void freeAllocation(DedicatedAllocation allocation) {
        if (activeAllocations.remove(allocation.bufferId) == null) {
            return;
        }
        allocatedBytes = Math.max(0, allocatedBytes - allocation.sizeInBytes);
        glBindBuffer(bufferType, allocation.bufferId);
        GpuMemoryTracker.trackPooledBuffer(bufferType, allocation.sizeInBytes, true);
        glBindBuffer(bufferType, 0);
        glDeleteBuffers(allocation.bufferId);
    }

    private static final class DedicatedAllocation implements OpenGLBufferAllocation {
        private final OpenGLDedicatedAllocator owner;
        private final int bufferId;
        private final int bufferType;
        private final long sizeInBytes;
        private boolean freed;

        private DedicatedAllocation(OpenGLDedicatedAllocator owner, int bufferId, int bufferType, long sizeInBytes) {
            this.owner = owner;
            this.bufferId = bufferId;
            this.bufferType = bufferType;
            this.sizeInBytes = sizeInBytes;
            GpuMemoryTracker.trackPooledRegion(sizeInBytes, false);
        }

        @Override
        public long getOffset() {
            return 0;
        }

        @Override
        public int getBufferId() {
            return bufferId;
        }

        @Override
        public int getBufferType() {
            return bufferType;
        }

        @Override
        public long getSizeInBytes() {
            return sizeInBytes;
        }

        @Override
        public void free() {
            if (freed) {
                return;
            }
            freed = true;
            owner.freeAllocation(this);
            GpuMemoryTracker.trackPooledRegion(sizeInBytes, true);
        }

        @Override
        public boolean isFreed() {
            return freed;
        }
    }
}
