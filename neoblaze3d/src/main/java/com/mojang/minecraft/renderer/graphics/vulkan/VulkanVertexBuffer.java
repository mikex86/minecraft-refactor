package com.mojang.minecraft.renderer.graphics.vulkan;

import com.mojang.minecraft.profiler.GpuMemoryTracker;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums;
import com.mojang.minecraft.renderer.graphics.ResourceState;
import com.mojang.minecraft.renderer.graphics.VertexBuffer;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

class VulkanVertexBuffer implements VertexBuffer, VulkanBufferStateTracked, VulkanVertexBufferHandle {
    protected final VulkanContext context;
    protected final int usageFlags;
    protected final int memoryPropertyFlags;
    protected final boolean hostVisibleWrites;

    protected long buffer;
    protected long memory;
    protected int capacityInBytes;
    protected int sizeInBytes;
    protected long allocatedSizeInBytes;

    private ResourceState.BufferAccess bufferAccess = ResourceState.BufferAccess.UNDEFINED;
    private boolean disposed;

    VulkanVertexBuffer(VulkanContext context, int usageFlags) {
        this(context, usageFlags, GraphicsEnums.BufferUsage.DYNAMIC, 1);
    }

    VulkanVertexBuffer(VulkanContext context, int usageFlags, int initialCapacityInBytes) {
        this(context, usageFlags, GraphicsEnums.BufferUsage.DYNAMIC, initialCapacityInBytes);
    }

    VulkanVertexBuffer(VulkanContext context, int usageFlags, GraphicsEnums.BufferUsage usage) {
        this(context, usageFlags, usage, 1);
    }

    VulkanVertexBuffer(VulkanContext context, int usageFlags, GraphicsEnums.BufferUsage usage, int initialCapacityInBytes) {
        if (context == null) {
            throw new IllegalArgumentException("context cannot be null");
        }
        if (usage == null) {
            throw new IllegalArgumentException("usage cannot be null");
        }
        if (initialCapacityInBytes <= 0) {
            throw new IllegalArgumentException("initialCapacityInBytes must be > 0");
        }
        this.context = context;
        this.usageFlags = usageFlags;
        if (usage == GraphicsEnums.BufferUsage.DYNAMIC) {
            this.memoryPropertyFlags = VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
            this.hostVisibleWrites = true;
        } else {
            this.memoryPropertyFlags = VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT;
            this.hostVisibleWrites = false;
        }
        allocate(initialCapacityInBytes);
    }

    @Override
    public void setData(ByteBuffer data, int sizeInBytes) {
        ensureWritable();
        if (sizeInBytes <= 0) {
            throw new IllegalArgumentException("sizeInBytes must be > 0");
        }
        ensureCapacity(sizeInBytes);
        if (this.sizeInBytes > 0) {
            GpuMemoryTracker.trackBufferUpload(this.sizeInBytes, true);
        }
        writeData(0, data, sizeInBytes);
        this.sizeInBytes = sizeInBytes;
        GpuMemoryTracker.trackBufferUpload(this.sizeInBytes, false);
    }

    @Override
    public void updateData(ByteBuffer data, int offsetInBytes, int sizeInBytes) {
        ensureWritable();
        if (offsetInBytes < 0 || sizeInBytes <= 0) {
            throw new IllegalArgumentException("Invalid update range");
        }
        if (offsetInBytes + sizeInBytes > this.sizeInBytes) {
            throw new IllegalArgumentException("Update range exceeds vertex buffer size");
        }
        writeData(offsetInBytes, data, sizeInBytes);
    }

    @Override
    public long getSizeInBytes() {
        return sizeInBytes;
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        freeBuffer();
        disposed = true;
    }

    @Override
    public boolean isDisposed() {
        return disposed;
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

    @Override
    public long getVkBufferHandle() {
        return buffer;
    }

    @Override
    public long getVkBufferOffset() {
        return 0L;
    }

    protected void ensureWritable() {
        if (disposed) {
            throw new IllegalStateException("VertexBuffer has been disposed");
        }
        if (bufferAccess != ResourceState.BufferAccess.TRANSFER_DST) {
            throw new IllegalStateException("VertexBuffer writes require TRANSFER_DST but was " + bufferAccess);
        }
    }

    protected void ensureCapacity(int requiredBytes) {
        if (requiredBytes <= capacityInBytes) {
            return;
        }
        int newCapacity = Math.max(requiredBytes, capacityInBytes * 2);
        freeBuffer();
        allocate(newCapacity);
        sizeInBytes = 0;
    }

    protected void allocate(int bytes) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer pBuffer = stack.mallocLong(1);
            LongBuffer pMemory = stack.mallocLong(1);
            allocatedSizeInBytes = context.createBuffer(
                    bytes,
                    usageFlags,
                    memoryPropertyFlags,
                    pBuffer,
                    pMemory
            );
            buffer = pBuffer.get(0);
            memory = pMemory.get(0);
            capacityInBytes = bytes;
            GpuMemoryTracker.trackBufferAllocation(allocatedSizeInBytes, false);
        }
    }

    protected void writeData(int dstOffset, ByteBuffer src, int sizeInBytes) {
        if (hostVisibleWrites) {
            writeMapped(dstOffset, src, sizeInBytes);
            return;
        }
        writeViaStaging(dstOffset, src, sizeInBytes);
    }

    protected void writeMapped(int dstOffset, ByteBuffer src, int sizeInBytes) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer pMapped = stack.mallocPointer(1);
            context.checkVk(VK10.vkMapMemory(context.getDevice(), memory, dstOffset, sizeInBytes, 0, pMapped), "vkMapMemory(vertex)");
            VulkanMemoryCopies.copyByteBuffer(src, pMapped.get(0), sizeInBytes);
            VK10.vkUnmapMemory(context.getDevice(), memory);
        }
    }

    protected void writeViaStaging(int dstOffset, ByteBuffer src, int sizeInBytes) {
        context.uploadToBufferImmediate(src, sizeInBytes, buffer, dstOffset);
    }

    protected void freeBuffer() {
        if (sizeInBytes > 0) {
            GpuMemoryTracker.trackBufferUpload(sizeInBytes, true);
        }
        if (allocatedSizeInBytes > 0) {
            GpuMemoryTracker.trackBufferAllocation(allocatedSizeInBytes, true);
        }
        context.destroyBufferWithMemory(buffer, memory);
        buffer = VK10.VK_NULL_HANDLE;
        memory = VK10.VK_NULL_HANDLE;
        sizeInBytes = 0;
        capacityInBytes = 0;
        allocatedSizeInBytes = 0;
    }
}
