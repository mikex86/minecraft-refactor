package com.mojang.minecraft.renderer.graphics.vulkan;

import com.mojang.minecraft.renderer.graphics.ResourceState;
import com.mojang.minecraft.renderer.graphics.VertexBuffer;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;

import java.nio.ByteBuffer;

final class VulkanAllocatedVertexBuffer implements VertexBuffer, VulkanBufferStateTracked, VulkanVertexBufferHandle {
    private final VulkanContext context;
    private final VulkanBufferAllocation allocation;

    private int sizeInBytes;
    private ResourceState.BufferAccess bufferAccess = ResourceState.BufferAccess.UNDEFINED;
    private boolean disposed;

    VulkanAllocatedVertexBuffer(VulkanContext context, VulkanBufferAllocation allocation) {
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
    public void setData(ByteBuffer data, int sizeInBytes) {
        ensureWritable();
        if (sizeInBytes <= 0) {
            throw new IllegalArgumentException("sizeInBytes must be > 0");
        }
        if (sizeInBytes > allocation.getSizeInBytes()) {
            throw new IllegalArgumentException("Data size exceeds allocation size");
        }
        writeMapped(0, data, sizeInBytes);
        this.sizeInBytes = sizeInBytes;
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
        writeMapped(offsetInBytes, data, sizeInBytes);
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
            throw new IllegalStateException("VertexBuffer has been disposed");
        }
        if (bufferAccess != ResourceState.BufferAccess.TRANSFER_DST) {
            throw new IllegalStateException("VertexBuffer writes require TRANSFER_DST but was " + bufferAccess);
        }
    }

    private void writeMapped(int dstOffset, ByteBuffer src, int sizeInBytes) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer pMapped = stack.mallocPointer(1);
            long mapOffset = allocation.getOffset() + dstOffset;
            context.checkVk(VK10.vkMapMemory(context.getDevice(), allocation.getMemory(), mapOffset, sizeInBytes, 0, pMapped), "vkMapMemory(allocated vertex)");
            VulkanMemoryCopies.copyByteBuffer(src, pMapped.get(0), sizeInBytes);
            VK10.vkUnmapMemory(context.getDevice(), allocation.getMemory());
        }
    }
}
