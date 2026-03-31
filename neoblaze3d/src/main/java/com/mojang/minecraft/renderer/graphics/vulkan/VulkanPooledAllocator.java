package com.mojang.minecraft.renderer.graphics.vulkan;

import com.mojang.minecraft.profiler.GpuMemoryTracker;
import com.mojang.minecraft.renderer.graphics.allocator.BufferAllocator;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;

import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Vulkan pooled allocator backed by large device-local pages and sub-allocation.
 * This keeps many mesh allocations on a small number of VkBuffer handles.
 */
final class VulkanPooledAllocator implements BufferAllocator<VulkanBufferAllocation> {
    private static final int DEFAULT_VERTEX_PAGE_SIZE = 128 * 1024 * 1024;
    private static final int DEFAULT_INDEX_PAGE_SIZE = 64 * 1024 * 1024;
    private static final int DEFAULT_VERTEX_ALIGNMENT = 96;

    private final VulkanContext context;
    private final int usageFlags;
    private final int memoryPropertyFlags;
    private final int alignmentInBytes;
    private final long defaultPageSizeInBytes;

    private final List<PoolPage> pages = new ArrayList<PoolPage>();
    private final Set<PoolAllocation> liveAllocations = new HashSet<PoolAllocation>();

    private long requestedLiveBytes;
    private int allocationCount;
    private boolean disposed;

    VulkanPooledAllocator(VulkanContext context, int usageFlags) {
        this.context = context;
        this.usageFlags = usageFlags;
        this.memoryPropertyFlags = VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT;

        boolean vertexUsage = (usageFlags & VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT) != 0;
        boolean indexUsage = (usageFlags & VK10.VK_BUFFER_USAGE_INDEX_BUFFER_BIT) != 0;
        if (vertexUsage && !indexUsage) {
            // 96B alignment keeps common legacy strides (24B/32B) base-vertex compatible.
            this.alignmentInBytes = DEFAULT_VERTEX_ALIGNMENT;
            this.defaultPageSizeInBytes = DEFAULT_VERTEX_PAGE_SIZE;
        } else if (indexUsage && !vertexUsage) {
            this.alignmentInBytes = 4;
            this.defaultPageSizeInBytes = DEFAULT_INDEX_PAGE_SIZE;
        } else {
            this.alignmentInBytes = 16;
            this.defaultPageSizeInBytes = DEFAULT_VERTEX_PAGE_SIZE;
        }
    }

    @Override
    public synchronized VulkanBufferAllocation allocate(int sizeInBytes) {
        if (disposed) {
            throw new IllegalStateException("Allocator has been disposed");
        }
        if (sizeInBytes <= 0) {
            throw new IllegalArgumentException("sizeInBytes must be > 0");
        }

        long alignedSizeInBytes = alignUp(sizeInBytes, alignmentInBytes);
        PoolPage page = null;
        long offset = -1L;
        for (int i = 0; i < pages.size(); i++) {
            PoolPage candidate = pages.get(i);
            long candidateOffset = candidate.allocate(alignedSizeInBytes);
            if (candidateOffset >= 0L) {
                page = candidate;
                offset = candidateOffset;
                break;
            }
        }

        if (page == null) {
            long pageSize = Math.max(defaultPageSizeInBytes, alignedSizeInBytes);
            page = createPage(pageSize);
            pages.add(page);
            offset = page.allocate(alignedSizeInBytes);
            if (offset < 0L) {
                throw new IllegalStateException("Newly created page could not satisfy allocation");
            }
        }

        PoolAllocation allocation = new PoolAllocation(
                this,
                page,
                offset,
                sizeInBytes,
                alignedSizeInBytes,
                usageFlags,
                memoryPropertyFlags
        );
        liveAllocations.add(allocation);
        requestedLiveBytes += sizeInBytes;
        allocationCount++;
        GpuMemoryTracker.trackPooledRegionBytes(sizeInBytes, false);
        return allocation;
    }

    @Override
    public synchronized String getStats() {
        long pooledBytes = 0L;
        for (int i = 0; i < pages.size(); i++) {
            pooledBytes += pages.get(i).capacityInBytes;
        }
        return "VulkanPooledAllocator(pages=" + pages.size()
                + ", pooledMB=" + String.format("%.2f", pooledBytes / (1024.0 * 1024.0))
                + ", requestedMB=" + String.format("%.2f", requestedLiveBytes / (1024.0 * 1024.0))
                + ", allocations=" + allocationCount + ")";
    }

    @Override
    public synchronized void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;

        PoolAllocation[] active = liveAllocations.toArray(new PoolAllocation[0]);
        for (int i = 0; i < active.length; i++) {
            PoolAllocation allocation = active[i];
            if (!allocation.freed) {
                allocation.freed = true;
                GpuMemoryTracker.trackPooledRegionBytes(allocation.requestedSizeInBytes, true);
            }
        }
        liveAllocations.clear();
        requestedLiveBytes = 0L;

        for (int i = 0; i < pages.size(); i++) {
            PoolPage page = pages.get(i);
            GpuMemoryTracker.trackPooledBufferBytes(page.allocatedSizeInBytes, true);
            context.destroyBufferWithMemory(page.buffer, page.memory);
        }
        pages.clear();
    }

    @Override
    public synchronized boolean isDisposed() {
        return disposed;
    }

    private synchronized void onFree(PoolAllocation allocation) {
        if (!liveAllocations.remove(allocation)) {
            return;
        }
        requestedLiveBytes = Math.max(0L, requestedLiveBytes - allocation.requestedSizeInBytes);
        GpuMemoryTracker.trackPooledRegionBytes(allocation.requestedSizeInBytes, true);
        allocation.page.free(allocation.offsetInBytes, allocation.alignedSizeInBytes);
    }

    private PoolPage createPage(long capacityInBytes) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer pBuffer = stack.mallocLong(1);
            LongBuffer pMemory = stack.mallocLong(1);
            long allocatedSizeInBytes = context.createBuffer(
                    capacityInBytes,
                    usageFlags,
                    memoryPropertyFlags,
                    pBuffer,
                    pMemory
            );
            GpuMemoryTracker.trackPooledBufferBytes(allocatedSizeInBytes, false);
            return new PoolPage(pBuffer.get(0), pMemory.get(0), capacityInBytes, allocatedSizeInBytes);
        }
    }

    private static long alignUp(long value, int alignmentInBytes) {
        long mask = alignmentInBytes - 1L;
        return (value + mask) & ~mask;
    }

    private static final class PoolPage {
        final long buffer;
        final long memory;
        final long capacityInBytes;
        final long allocatedSizeInBytes;
        final TreeMap<Long, Long> freeByOffset = new TreeMap<Long, Long>();

        PoolPage(long buffer, long memory, long capacityInBytes, long allocatedSizeInBytes) {
            this.buffer = buffer;
            this.memory = memory;
            this.capacityInBytes = capacityInBytes;
            this.allocatedSizeInBytes = allocatedSizeInBytes;
            freeByOffset.put(Long.valueOf(0L), Long.valueOf(capacityInBytes));
        }

        long allocate(long sizeInBytes) {
            for (Map.Entry<Long, Long> entry : freeByOffset.entrySet()) {
                long offset = entry.getKey().longValue();
                long regionSize = entry.getValue().longValue();
                if (regionSize < sizeInBytes) {
                    continue;
                }

                freeByOffset.remove(entry.getKey());
                long remaining = regionSize - sizeInBytes;
                if (remaining > 0L) {
                    freeByOffset.put(Long.valueOf(offset + sizeInBytes), Long.valueOf(remaining));
                }
                return offset;
            }
            return -1L;
        }

        void free(long offsetInBytes, long sizeInBytes) {
            long mergedOffset = offsetInBytes;
            long mergedSize = sizeInBytes;

            Map.Entry<Long, Long> lower = freeByOffset.floorEntry(Long.valueOf(offsetInBytes));
            if (lower != null) {
                long lowerOffset = lower.getKey().longValue();
                long lowerSize = lower.getValue().longValue();
                long lowerEnd = lowerOffset + lowerSize;
                if (lowerEnd == offsetInBytes) {
                    mergedOffset = lowerOffset;
                    mergedSize += lowerSize;
                    freeByOffset.remove(lower.getKey());
                }
            }

            Map.Entry<Long, Long> higher = freeByOffset.ceilingEntry(Long.valueOf(offsetInBytes));
            if (higher != null) {
                long higherOffset = higher.getKey().longValue();
                long higherSize = higher.getValue().longValue();
                long mergedEnd = mergedOffset + mergedSize;
                if (mergedEnd == higherOffset) {
                    mergedSize += higherSize;
                    freeByOffset.remove(higher.getKey());
                }
            }

            freeByOffset.put(Long.valueOf(mergedOffset), Long.valueOf(mergedSize));
        }
    }

    private static final class PoolAllocation implements VulkanBufferAllocation {
        private final VulkanPooledAllocator owner;
        private final PoolPage page;
        private final long offsetInBytes;
        private final long requestedSizeInBytes;
        private final long alignedSizeInBytes;
        private final int usageFlags;
        private final int memoryPropertyFlags;
        private boolean freed;

        private PoolAllocation(VulkanPooledAllocator owner,
                               PoolPage page,
                               long offsetInBytes,
                               long requestedSizeInBytes,
                               long alignedSizeInBytes,
                               int usageFlags,
                               int memoryPropertyFlags) {
            this.owner = owner;
            this.page = page;
            this.offsetInBytes = offsetInBytes;
            this.requestedSizeInBytes = requestedSizeInBytes;
            this.alignedSizeInBytes = alignedSizeInBytes;
            this.usageFlags = usageFlags;
            this.memoryPropertyFlags = memoryPropertyFlags;
        }

        @Override
        public long getBuffer() {
            return page.buffer;
        }

        @Override
        public long getMemory() {
            return page.memory;
        }

        @Override
        public long getOffset() {
            return offsetInBytes;
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
            return requestedSizeInBytes;
        }

        @Override
        public void free() {
            synchronized (owner) {
                if (freed) {
                    return;
                }
                freed = true;
                owner.onFree(this);
            }
        }

        @Override
        public boolean isFreed() {
            synchronized (owner) {
                return freed;
            }
        }
    }
}
