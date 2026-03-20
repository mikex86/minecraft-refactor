package com.mojang.minecraft.renderer.graphics.vulkan;

import com.mojang.minecraft.renderer.graphics.allocator.BufferAllocator;

/**
 * Temporary Vulkan pooled allocator implementation.
 * For now this delegates allocation strategy to dedicated allocations while
 * preserving the allocator abstraction and hint contract.
 */
final class VulkanPooledAllocator implements BufferAllocator<VulkanBufferAllocation> {
    private final VulkanDedicatedAllocator backing;

    VulkanPooledAllocator(VulkanContext context, int usageFlags) {
        this.backing = new VulkanDedicatedAllocator(context, usageFlags);
    }

    @Override
    public VulkanBufferAllocation allocate(int sizeInBytes) {
        return backing.allocate(sizeInBytes);
    }

    @Override
    public String getStats() {
        return "VulkanPooledAllocator(" + backing.getStats() + ")";
    }

    @Override
    public void dispose() {
        backing.dispose();
    }

    @Override
    public boolean isDisposed() {
        return backing.isDisposed();
    }
}
