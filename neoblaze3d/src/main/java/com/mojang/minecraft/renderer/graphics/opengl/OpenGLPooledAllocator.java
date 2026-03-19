package com.mojang.minecraft.renderer.graphics.opengl;

import com.mojang.minecraft.profiler.GpuMemoryTracker;
import com.mojang.minecraft.renderer.graphics.allocator.BufferAllocator;
import com.mojang.minecraft.renderer.graphics.annotation.RenderThreadOnly;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.logging.Logger;
import org.lwjgl.system.jemalloc.JEmalloc;

import static org.lwjgl.opengl.GL15.*;

/**
 * A pool for OpenGL buffer objects that manages a single large buffer
 * and allocates regions from it to avoid creating many small buffer objects.
 */
final class OpenGLPooledAllocator implements BufferAllocator<OpenGLBufferAllocation> {
    private static final Logger LOGGER = Logger.getLogger(OpenGLPooledAllocator.class.getName());
    
    // Growth settings
    private static final float GROWTH_THRESHOLD = 0.85f; // Start growing at 85% capacity
    private static final float GROWTH_FACTOR = 1.5f; // Grow by 50% each time
    private static final long MAX_SIZE = 4L * 1024L * 1024L * 1024L; // 4GB max size
    private static final Comparator<Region> REGION_OFFSET_COMPARATOR = new Comparator<Region>() {
        @Override
        public int compare(Region a, Region b) {
            return Long.compare(a.offset, b.offset);
        }
    };
    private static final Comparator<BufferRegion> BUFFER_REGION_OFFSET_COMPARATOR = new Comparator<BufferRegion>() {
        @Override
        public int compare(BufferRegion a, BufferRegion b) {
            return Long.compare(a.getOffset(), b.getOffset());
        }
    };

    // The OpenGL buffer ID
    private int bufferId;

    // The total size of the buffer in bytes
    private long totalSize;

    // The buffer type (GL_ARRAY_BUFFER or GL_ELEMENT_ARRAY_BUFFER)
    private final int bufferType;

    // List of free regions in the buffer (offset, size)
    private final List<Region> freeRegions = new ArrayList<>();

    // Map of active buffer regions (for defragmentation)
    private final Map<Long, BufferRegion> activeRegions = new HashMap<>();

    // Track whether the pool has been disposed
    private boolean disposed = false;

    // Track if defragmentation is in progress
    private boolean defragmenting = false;
    
    // Track if growth is in progress
    private boolean growing = false;

    // Stats for debugging
    private long totalAllocated = 0;
    private int totalAllocationCount = 0;
    private int failedAllocations = 0;
    private int defragmentationCount = 0;
    private int growthCount = 0;

    /**
     * Creates a new buffer pool.
     *
     * @param bufferType The OpenGL buffer type (GL_ARRAY_BUFFER or GL_ELEMENT_ARRAY_BUFFER)
     * @param sizeInBytes The total size of the buffer in bytes
     */
    public OpenGLPooledAllocator(int bufferType, long sizeInBytes) {
        this.bufferType = bufferType;
        this.totalSize = sizeInBytes;

        // Create the buffer
        this.bufferId = glGenBuffers();

        // Bind and allocate the buffer
        glBindBuffer(bufferType, bufferId);
        glBufferData(bufferType, sizeInBytes, bufferType == GL_ARRAY_BUFFER ? GL_DYNAMIC_DRAW : GL_STATIC_DRAW);

        // Track GPU memory allocation using the new method
        GpuMemoryTracker.trackPooledBuffer(bufferType, sizeInBytes, false);

        glBindBuffer(bufferType, 0);

        // Initially, there's one big free region
        freeRegions.add(new Region(0, sizeInBytes));
    }

    /**
     * Allocates a region from the buffer.
     *
     * @param sizeInBytes The size of the region to allocate
     * @return A BufferRegion representing the allocated region, or null if no suitable region is available
     */
    @RenderThreadOnly
    @Override
    public BufferRegion allocate(int sizeInBytes) {
        if (disposed) {
            throw new IllegalStateException("Cannot allocate from a disposed buffer pool");
        }
        if (sizeInBytes <= 0) {
            throw new IllegalArgumentException("Allocation size must be greater than zero");
        }

        // Check total free space first
        long totalFreeSpace = sumFreeRegionBytes();
        long totalUsed = totalSize - totalFreeSpace;
        
        // Check if we need to grow the buffer
        if ((totalUsed / (float)totalSize) > GROWTH_THRESHOLD && !growing && totalSize < MAX_SIZE) {
            if (growBuffer()) {
                // Try allocation again with the larger buffer
                return allocate(sizeInBytes);
            }
        }
        
        // If still not enough total space, try defragmentation
        if (totalFreeSpace < sizeInBytes && !defragmenting) {
            defragmentMemory();
            
            // Recalculate free space after defragmentation
            totalFreeSpace = sumFreeRegionBytes();
            
            // If still not enough space after defragmentation, try growing
            if (totalFreeSpace < sizeInBytes && !growing && totalSize < MAX_SIZE) {
                if (growBuffer()) {
                    // Try allocation again with the larger buffer
                    return allocate(sizeInBytes);
                }
            }
        }
        
        // If still not enough total space, fail
        if (totalFreeSpace < sizeInBytes) {
            failedAllocations++;
            return null;
        }

        // Find the best fit region (smallest region that fits), with at most one
        // defragmentation attempt and one growth attempt to avoid unbounded recursion.
        Region bestFit = null;
        int bestFitIndex = -1;
        boolean attemptedDefrag = false;

        while (bestFit == null) {
            for (int i = 0; i < freeRegions.size(); i++) {
                Region region = freeRegions.get(i);
                if (region.size >= sizeInBytes && (bestFit == null || region.size < bestFit.size)) {
                    bestFit = region;
                    bestFitIndex = i;
                }
            }

            if (bestFit != null) {
                break;
            }

            if (!attemptedDefrag && !defragmenting) {
                attemptedDefrag = true;
                defragmentMemory();
                continue;
            }

            if (!growing && totalSize < MAX_SIZE && growBuffer()) {
                return allocate(sizeInBytes);
            }

            failedAllocations++;
            return null;
        }

        // Remove the region from the free list
        freeRegions.remove(bestFitIndex);

        // If the region is larger than what we need, split it
        if (bestFit.size > sizeInBytes) {
            // Add the remainder back to the free list
            freeRegions.add(new Region(bestFit.offset + sizeInBytes, bestFit.size - sizeInBytes));

            // Sort the free list by offset to facilitate merging adjacent regions
            Collections.sort(freeRegions, REGION_OFFSET_COMPARATOR);
        }

        if (activeRegions.containsKey(bestFit.offset)) {
            LOGGER.severe("Buffer pool corruption detected: active region collision at offset " + bestFit.offset + ". Repairing free list.");
            rebuildFreeRegionsFromActiveRegions();
            failedAllocations++;
            return null;
        }

        // Create and return a buffer region
        BufferRegion region = new BufferRegion(this, bufferId, bufferType, bestFit.offset, sizeInBytes);

        // Track allocation
        activeRegions.put(bestFit.offset, region);
        totalAllocated += sizeInBytes;
        totalAllocationCount++;

        return region;
    }

    /**
     * Frees a region back to the pool.
     *
     * @param offset The offset of the region
     * @param size The size of the region
     */
    @RenderThreadOnly
    public void free(long offset, long size) {
        if (disposed) {
            return;
        }

        // Remove from active regions
        BufferRegion removedRegion = activeRegions.remove(offset);
        if (removedRegion == null) {
            LOGGER.warning("Attempted to free unknown buffer region at offset " + offset + " (size " + size + "). Skipping free region insertion to avoid overlap corruption.");
            rebuildFreeRegionsFromActiveRegions();
            return;
        }
        long removedSize = removedRegion.getSize();
        totalAllocated = Math.max(0, totalAllocated - removedSize);

        // Create a new free region
        Region newRegion = new Region(offset, removedSize);

        // Find where to insert the new region (keeping the list sorted by offset)
        int insertIndex = 0;
        while (insertIndex < freeRegions.size() && freeRegions.get(insertIndex).offset < offset) {
            insertIndex++;
        }

        // Insert the new region
        freeRegions.add(insertIndex, newRegion);

        // Merge adjacent regions
        mergeAdjacentRegions();
    }
    
    /**
     * Grows the buffer by creating a new larger buffer and copying all data.
     * 
     * @return True if growth was successful, false otherwise
     */
    @RenderThreadOnly
    private boolean growBuffer() {
        if (growing || disposed || totalSize >= MAX_SIZE) {
            return false;
        }
        
        try {
            growing = true;
            
            // Calculate new size (max out at MAX_SIZE)
            long newSize = Math.min(MAX_SIZE, (long)(totalSize * GROWTH_FACTOR));
            if (newSize <= totalSize) {
                return false; // Can't grow anymore
            }
            
            LOGGER.info(String.format("Growing %s buffer pool from %.2f MB to %.2f MB", 
                    bufferType == GL_ARRAY_BUFFER ? "VBO" : "IBO", 
                    totalSize / (1024.0 * 1024.0),
                    newSize / (1024.0 * 1024.0)));

            // First track deallocation of old buffer
            glBindBuffer(bufferType, bufferId);
            GpuMemoryTracker.trackPooledBuffer(bufferType, totalSize, true);

            // Create new buffer
            int newBufferId = glGenBuffers();
            glBindBuffer(bufferType, newBufferId);
            glBufferData(bufferType, newSize, bufferType == GL_ARRAY_BUFFER ? GL_DYNAMIC_DRAW : GL_STATIC_DRAW);

            // Then track allocation of new buffer
            GpuMemoryTracker.trackPooledBuffer(bufferType, newSize, false);
            
            // Copy data from active regions to new buffer
            if (!activeRegions.isEmpty()) {
                // Buffer for copying data (64MB temp buffer or max int).
                ByteBuffer tempBuffer = allocateJemallocBuffer(
                        Math.min(Integer.MAX_VALUE, 64 * 1024 * 1024),
                        "grow-buffer copy staging"
                );
                try {
                    // Copy each active region
                    for (BufferRegion region : activeRegions.values()) {
                        long offset = region.getOffset();
                        long remaining = region.getSize();
                        long srcOffset = offset;
                        long dstOffset = offset;
                        
                        while (remaining > 0) {
                            // Determine copy size for this iteration
                            int copySize = (int)Math.min(remaining, tempBuffer.capacity());
                            tempBuffer.clear();
                            tempBuffer.limit(copySize);
                            
                            // Read from old buffer
                            glBindBuffer(bufferType, bufferId);
                            glGetBufferSubData(bufferType, srcOffset, tempBuffer);
                            tempBuffer.position(0);
                            tempBuffer.limit(copySize);
                            
                            // Write to new buffer
                            glBindBuffer(bufferType, newBufferId);
                            glBufferSubData(bufferType, dstOffset, tempBuffer);
                            
                            // Update offsets and remaining
                            srcOffset += copySize;
                            dstOffset += copySize;
                            remaining -= copySize;
                        }
                        
                        // Update the region's buffer ID
                        region.setBufferId(newBufferId);
                    }
                } finally {
                    JEmalloc.je_free(tempBuffer);
                }
            }
            
            // Delete old buffer
            glBindBuffer(bufferType, 0);
            glDeleteBuffers(bufferId);
            
            // Update pool state
            bufferId = newBufferId;
            
            // Update total size
            totalSize = newSize;
            rebuildFreeRegionsFromActiveRegions();
            
            // Update stats
            growthCount++;
            
            return true;
        } catch (Exception e) {
            LOGGER.severe("Failed to grow buffer: " + e.getMessage());
            return false;
        } finally {
            growing = false;
        }
    }

    /**
     * Merges adjacent free regions in the list.
     */
    private void mergeAdjacentRegions() {
        if (freeRegions.size() < 2) {
            return;
        }

        int i = 0;
        while (i < freeRegions.size() - 1) {
            Region current = freeRegions.get(i);
            Region next = freeRegions.get(i + 1);

            long currentEnd = current.offset + current.size;
            long nextEnd = next.offset + next.size;

            // Merge adjacent or overlapping regions to keep free space canonical.
            if (currentEnd >= next.offset) {
                current.size = Math.max(currentEnd, nextEnd) - current.offset;
                freeRegions.remove(i + 1);
            } else {
                i++;
            }
        }
    }

    /**
     * Defragments the memory by moving allocated regions to eliminate fragmentation.
     */
    @RenderThreadOnly
    private void defragmentMemory() {
        if (defragmenting || disposed || activeRegions.isEmpty()) {
            return;
        }

        Map<BufferRegion, ByteBuffer> regionData = new HashMap<>();
        try {
            defragmenting = true;
            defragmentationCount++;
            
            LOGGER.info("Starting buffer defragmentation");

            // Sort active regions by offset
            List<BufferRegion> sortedRegions = new ArrayList<>(activeRegions.values());
            Collections.sort(sortedRegions, BUFFER_REGION_OFFSET_COMPARATOR);

            // Calculate total fragmentation (sum of gaps between regions)
            long expectedOffset = 0;
            long totalFragmentation = 0;

            for (BufferRegion region : sortedRegions) {
                totalFragmentation += region.getOffset() - expectedOffset;
                expectedOffset = region.getOffset() + region.getSize();
            }

            // If fragmentation is minimal, don't bother defragmenting
            if (totalFragmentation < 1024 * 1024) { // Less than 1MB fragmentation
                return;
            }
            
            LOGGER.info(String.format("Defragmenting buffer with %.2f MB fragmentation", 
                    totalFragmentation / (1024.0 * 1024.0)));

            // Read all region data into temporary buffers
            glBindBuffer(bufferType, bufferId);
            for (BufferRegion region : sortedRegions) {
                int size = (int) Math.min(Integer.MAX_VALUE, region.getSize());
                ByteBuffer data = allocateJemallocBuffer(size, "defragment-region staging");

                // Copy data from GPU to CPU
                glGetBufferSubData(bufferType, region.getOffset(), data);
                data.position(0);
                data.limit(size);

                regionData.put(region, data);
            }

            // Re-allocate and copy data back, compacted
            long newOffset = 0;
            for (BufferRegion region : sortedRegions) {
                long size = region.getSize();

                // Update region offset (internal state only)
                region.setOffset(newOffset);

                // Upload data back to GPU at new location
                ByteBuffer data = regionData.get(region);
                glBufferSubData(bufferType, newOffset, data);

                // Move to next position
                newOffset += size;
            }

            glBindBuffer(bufferType, 0);

            // Update active regions map
            Map<Long, BufferRegion> newActiveRegions = new HashMap<>();
            for (BufferRegion region : sortedRegions) {
                newActiveRegions.put(region.getOffset(), region);
            }
            activeRegions.clear();
            activeRegions.putAll(newActiveRegions);
            rebuildFreeRegionsFromActiveRegions();
            
            LOGGER.info("Buffer defragmentation complete");

        } finally {
            for (ByteBuffer data : regionData.values()) {
                if (data != null) {
                    JEmalloc.je_free(data);
                }
            }
            defragmenting = false;
        }
    }

    private static ByteBuffer allocateJemallocBuffer(int sizeInBytes, String context) {
        ByteBuffer buffer = JEmalloc.je_malloc(sizeInBytes);
        if (buffer == null) {
            throw new OutOfMemoryError("jemalloc failed to allocate " + sizeInBytes + " bytes for " + context);
        }
        return buffer;
    }

    /**
     * Gets buffer statistics for debugging.
     *
     * @return A string with buffer statistics
     */
    @Override
    public String getStats() {
        long allocatedBytes = totalAllocated;
        long freeBytes = sumFreeRegionBytes();
        int numFreeRegions = freeRegions.size();

        return String.format("Buffer Pool Stats (type=%s):%n" +
                "  Total size: %.2f MB%n" +
                "  Allocated: %.2f MB (%d regions, %d failed allocations)%n" +
                "  Free: %.2f MB (%d regions)%n" +
                "  Usage: %.1f%%%n" +
                "  Defragmentations: %d%n" +
                "  Growths: %d (current size: %.2f MB)",
                bufferType == GL_ARRAY_BUFFER ? "VBO" : "IBO",
                totalSize / (1024.0 * 1024.0),
                allocatedBytes / (1024.0 * 1024.0), 
                activeRegions.size(), failedAllocations,
                freeBytes / (1024.0 * 1024.0), 
                numFreeRegions,
                100.0 * allocatedBytes / totalSize,
                defragmentationCount,
                growthCount,
                totalSize / (1024.0 * 1024.0));
    }

    /**
     * Gets the buffer ID.
     *
     * @return The OpenGL buffer ID
     */
    public int getBufferId() {
        return bufferId;
    }

    /**
     * Gets the buffer type.
     *
     * @return The OpenGL buffer type
     */
    public int getBufferType() {
        return bufferType;
    }

    /**
     * Disposes of the buffer pool.
     */
    @Override
    public synchronized void dispose() {
        if (!disposed) {
            glBindBuffer(bufferType, bufferId);

            // Track memory de-allocation
            GpuMemoryTracker.trackPooledBuffer(bufferType, totalSize, true);

            glBindBuffer(bufferType, 0);

            // Delete the buffer
            glDeleteBuffers(bufferId);
            
            freeRegions.clear();
            activeRegions.clear();
            disposed = true;
        }
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }

    /**
     * Updates a region of the buffer with data.
     *
     * @param offset The offset to update
     * @param data The data to upload
     * @param dataSize The size of the data in bytes
     */
    public void updateRegion(long offset, ByteBuffer data, int dataSize) {
        if (disposed) {
            throw new IllegalStateException("Cannot update a disposed buffer pool");
        }
        
        glBindBuffer(bufferType, bufferId);
        glBufferSubData(bufferType, offset, data);
        glBindBuffer(bufferType, 0);
    }

    private void rebuildFreeRegionsFromActiveRegions() {
        freeRegions.clear();
        if (activeRegions.isEmpty()) {
            freeRegions.add(new Region(0, totalSize));
            return;
        }

        List<BufferRegion> sortedRegions = new ArrayList<>(activeRegions.values());
        Collections.sort(sortedRegions, BUFFER_REGION_OFFSET_COMPARATOR);

        long cursor = 0;
        for (BufferRegion region : sortedRegions) {
            long offset = region.getOffset();
            long end = offset + region.getSize();

            if (offset > cursor) {
                freeRegions.add(new Region(cursor, offset - cursor));
            }
            cursor = Math.max(cursor, end);
        }

        if (cursor < totalSize) {
            freeRegions.add(new Region(cursor, totalSize - cursor));
        }
    }

    private long sumFreeRegionBytes() {
        long total = 0;
        for (Region freeRegion : freeRegions) {
            total += freeRegion.size;
        }
        return total;
    }

    /**
     * A region in the buffer.
     */
    private static class Region {
        long offset;
        long size;
        
        Region(long offset, long size) {
            this.offset = offset;
            this.size = size;
        }
    }

    /**
     * Mutable interface for buffer region (for defragmentation).
     */
    private interface MutableBufferRegion {
        void setOffset(long newOffset);
        void setBufferId(int newBufferId);
    }

    /**
     * A region allocated from the buffer pool.
     */
    public static class BufferRegion implements MutableBufferRegion, OpenGLBufferAllocation {
        private final OpenGLPooledAllocator pool;
        private int bufferId;
        private final int bufferType;
        private long offset;
        private final long size;
        private boolean freed = false;
        
        BufferRegion(OpenGLPooledAllocator pool, int bufferId, int bufferType, long offset, long size) {
            this.pool = pool;
            this.bufferId = bufferId;
            this.bufferType = bufferType;
            this.offset = offset;
            this.size = size;
            
            // Track the allocated region
            GpuMemoryTracker.trackPooledRegion(size, false);
        }
        
        /**
         * Gets the buffer ID.
         * 
         * @return The OpenGL buffer ID
         */
        @Override
        public int getBufferId() {
            return bufferId;
        }
        
        /**
         * Gets the buffer type.
         * 
         * @return The OpenGL buffer type
         */
        @Override
        public int getBufferType() {
            return bufferType;
        }
        
        /**
         * Gets the offset of the region in the buffer.
         * 
         * @return The offset in bytes
         */
        @Override
        public long getOffset() {
            return offset;
        }
        
        /**
         * Sets the offset of the region (for defragmentation).
         * 
         * @param newOffset The new offset in bytes
         */
        @Override
        public void setOffset(long newOffset) {
            this.offset = newOffset;
        }
        
        /**
         * Sets the buffer ID (for buffer growth).
         * 
         * @param newBufferId The new buffer ID
         */
        @Override
        public void setBufferId(int newBufferId) {
            this.bufferId = newBufferId;
        }
        
        /**
         * Gets the size of the region.
         * 
         * @return The size in bytes
         */
        public long getSize() {
            return size;
        }

        @Override
        public long getSizeInBytes() {
            return size;
        }
        
        /**
         * Frees the region back to the pool.
         */
        @Override
        public void free() {
            if (!freed) {
                // Track memory deallocation first
                GpuMemoryTracker.trackPooledRegion(size, true);
                
                pool.free(offset, size);
                freed = true;
            }
        }
        
        /**
         * Checks if the region has been freed.
         * 
         * @return true if the region has been freed, false otherwise
         */
        @Override
        public boolean isFreed() {
            return freed;
        }
    }
}
