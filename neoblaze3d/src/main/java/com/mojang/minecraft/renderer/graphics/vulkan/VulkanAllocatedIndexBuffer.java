package com.mojang.minecraft.renderer.graphics.vulkan;

import com.mojang.minecraft.renderer.graphics.IndexBuffer;
import com.mojang.minecraft.renderer.graphics.ResourceState;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;

import java.nio.IntBuffer;

final class VulkanAllocatedIndexBuffer implements IndexBuffer, VulkanBufferStateTracked, VulkanIndexBufferHandle {
    private final VulkanContext context;
    private final VulkanBufferAllocation allocation;

    private int sizeInBytes;
    private int indexCount;
    private ResourceState.BufferAccess bufferAccess = ResourceState.BufferAccess.UNDEFINED;
    private boolean disposed;

    VulkanAllocatedIndexBuffer(VulkanContext context, VulkanBufferAllocation allocation) {
        if (context == null) {
            throw new IllegalArgumentException("context cannot be null");
        }
        if (allocation == null) {
            throw new IllegalArgumentException("allocation cannot be null");
        }
        this.context = context;
        this.allocation = allocation;
    }

    @Override
    public void setData(IntBuffer data, int sizeInBytes) {
        ensureWritable();
        if (sizeInBytes <= 0 || (sizeInBytes % 4) != 0) {
            throw new IllegalArgumentException("sizeInBytes must be positive and aligned to 4 bytes");
        }
        if (sizeInBytes > allocation.getSizeInBytes()) {
            throw new IllegalArgumentException("Data size exceeds allocation size");
        }
        writeMapped(0, data, sizeInBytes);
        this.sizeInBytes = sizeInBytes;
        this.indexCount = sizeInBytes / 4;
    }

    @Override
    public void updateData(IntBuffer data, int offsetInBytes, int sizeInBytes) {
        ensureWritable();
        if (offsetInBytes < 0 || sizeInBytes <= 0 || (sizeInBytes % 4) != 0) {
            throw new IllegalArgumentException("Invalid update range");
        }
        if (offsetInBytes + sizeInBytes > this.sizeInBytes) {
            throw new IllegalArgumentException("Update range exceeds index buffer size");
        }
        writeMapped(offsetInBytes, data, sizeInBytes);
    }

    @Override
    public long getSizeInBytes() {
        return sizeInBytes;
    }

    @Override
    public int getIndexCount() {
        return indexCount;
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        allocation.free();
        disposed = true;
    }

    @Override
    public boolean isDisposed() {
        return disposed || allocation.isFreed();
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
        return allocation.getBuffer();
    }

    @Override
    public long getVkBufferOffset() {
        return allocation.getOffset();
    }

    private void ensureWritable() {
        if (isDisposed()) {
            throw new IllegalStateException("IndexBuffer has been disposed");
        }
        if (bufferAccess != ResourceState.BufferAccess.TRANSFER_DST) {
            throw new IllegalStateException("IndexBuffer writes require TRANSFER_DST but was " + bufferAccess);
        }
    }

    private void writeMapped(int dstOffset, IntBuffer src, int sizeInBytes) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer pMapped = stack.mallocPointer(1);
            long mapOffset = allocation.getOffset() + dstOffset;
            context.checkVk(VK10.vkMapMemory(context.getDevice(), allocation.getMemory(), mapOffset, sizeInBytes, 0, pMapped), "vkMapMemory(allocated index)");
            VulkanMemoryCopies.copyIntBuffer(src, pMapped.get(0), sizeInBytes);
            VK10.vkUnmapMemory(context.getDevice(), allocation.getMemory());
        }
    }
}
