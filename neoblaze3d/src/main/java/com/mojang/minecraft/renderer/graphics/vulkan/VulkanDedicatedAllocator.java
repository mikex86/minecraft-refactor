package com.mojang.minecraft.renderer.graphics.vulkan;

import com.mojang.minecraft.profiler.GpuMemoryTracker;
import com.mojang.minecraft.renderer.graphics.allocator.BufferAllocator;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;

import java.nio.LongBuffer;
import java.util.HashMap;
import java.util.Map;

final class VulkanDedicatedAllocator implements BufferAllocator<VulkanBufferAllocation> {
    private final VulkanContext context;
    private final int usageFlags;
    private final Map<Long, DedicatedAllocation> allocations = new HashMap<Long, DedicatedAllocation>();
    private long allocatedBytes;
    private int allocationCount;
    private boolean disposed;

    VulkanDedicatedAllocator(VulkanContext context, int usageFlags) {
        this.context = context;
        this.usageFlags = usageFlags;
    }

    @Override
    public VulkanBufferAllocation allocate(int sizeInBytes) {
        if (disposed) {
            throw new IllegalStateException("Allocator has been disposed");
        }
        if (sizeInBytes <= 0) {
            throw new IllegalArgumentException("sizeInBytes must be > 0");
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer pBuffer = stack.mallocLong(1);
            LongBuffer pMemory = stack.mallocLong(1);
            int memoryPropertyFlags = VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT;
            long allocatedSizeInBytes = context.createBuffer(
                    sizeInBytes,
                    usageFlags,
                    memoryPropertyFlags,
                    pBuffer,
                    pMemory
            );

            DedicatedAllocation allocation = new DedicatedAllocation(
                    this,
                    pBuffer.get(0),
                    pMemory.get(0),
                    sizeInBytes,
                    allocatedSizeInBytes,
                    usageFlags,
                    memoryPropertyFlags
            );
            allocations.put(Long.valueOf(allocation.getBuffer()), allocation);
            allocatedBytes += sizeInBytes;
            allocationCount++;
            GpuMemoryTracker.trackPooledBufferBytes(allocatedSizeInBytes, false);
            GpuMemoryTracker.trackPooledRegionBytes(sizeInBytes, false);
            return allocation;
        }
    }

    @Override
    public String getStats() {
        return "VulkanDedicatedAllocator(active=" + allocations.size()
                + ", allocatedMB=" + String.format("%.2f", allocatedBytes / (1024.0 * 1024.0))
                + ", allocations=" + allocationCount + ")";
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        DedicatedAllocation[] values = allocations.values().toArray(new DedicatedAllocation[0]);
        for (DedicatedAllocation allocation : values) {
            allocation.free();
        }
        allocations.clear();
        disposed = true;
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }

    private void onFree(DedicatedAllocation allocation) {
        if (allocations.remove(Long.valueOf(allocation.buffer)) != null) {
            allocatedBytes = Math.max(0L, allocatedBytes - allocation.sizeInBytes);
            GpuMemoryTracker.trackPooledRegionBytes(allocation.sizeInBytes, true);
            GpuMemoryTracker.trackPooledBufferBytes(allocation.allocatedSizeInBytes, true);
            context.destroyBufferWithMemory(allocation.buffer, allocation.memory);
        }
    }

    private static final class DedicatedAllocation implements VulkanBufferAllocation {
        private final VulkanDedicatedAllocator owner;
        private final long buffer;
        private final long memory;
        private final long sizeInBytes;
        private final long allocatedSizeInBytes;
        private final int usageFlags;
        private final int memoryPropertyFlags;
        private boolean freed;

        private DedicatedAllocation(
                VulkanDedicatedAllocator owner,
                long buffer,
                long memory,
                long sizeInBytes,
                long allocatedSizeInBytes,
                int usageFlags,
                int memoryPropertyFlags
        ) {
            this.owner = owner;
            this.buffer = buffer;
            this.memory = memory;
            this.sizeInBytes = sizeInBytes;
            this.allocatedSizeInBytes = allocatedSizeInBytes;
            this.usageFlags = usageFlags;
            this.memoryPropertyFlags = memoryPropertyFlags;
        }

        @Override
        public long getBuffer() {
            return buffer;
        }

        @Override
        public long getMemory() {
            return memory;
        }

        @Override
        public long getOffset() {
            return 0L;
        }

        @Override
        public int getUsageFlags() {
            return usageFlags;
        }

        @Override
        public int getMemoryPropertyFlags() {
            return memoryPropertyFlags;
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
            owner.onFree(this);
        }

        @Override
        public boolean isFreed() {
            return freed;
        }
    }
}
