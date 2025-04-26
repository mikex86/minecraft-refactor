package com.mojang.minecraft.renderer.graphics.opengl;

import com.mojang.minecraft.profiler.GpuMemoryTracker;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static org.lwjgl.opengl.GL15.*;

/**
 * A pool for OpenGL buffer objects that manages a single large buffer
 * and allocates regions from it to avoid creating many small buffer objects.
 */
public class OpenGLBufferPool {
    // The OpenGL buffer ID
    private final int bufferId;
    
    // The total size of the buffer in bytes
    private final int totalSize;
    
    // The buffer type (GL_ARRAY_BUFFER or GL_ELEMENT_ARRAY_BUFFER)
    private final int bufferType;
    
    // List of free regions in the buffer (offset, size)
    private final List<Region> freeRegions = new ArrayList<>();
    
    // Track whether the pool has been disposed
    private boolean disposed = false;
    
    /**
     * Creates a new buffer pool.
     * 
     * @param bufferType The OpenGL buffer type (GL_ARRAY_BUFFER or GL_ELEMENT_ARRAY_BUFFER)
     * @param sizeInBytes The total size of the buffer in bytes
     */
    public OpenGLBufferPool(int bufferType, int sizeInBytes) {
        this.bufferType = bufferType;
        this.totalSize = sizeInBytes;
        
        // Create the buffer
        this.bufferId = glGenBuffers();
        
        // Bind and allocate the buffer
        glBindBuffer(bufferType, bufferId);
        glBufferData(bufferType, sizeInBytes, bufferType == GL_ARRAY_BUFFER ? GL_DYNAMIC_DRAW : GL_STATIC_DRAW);
        glBindBuffer(bufferType, 0);
        
        // Track GPU memory allocation
        if (bufferType == GL_ARRAY_BUFFER) {
            GpuMemoryTracker.trackVbo(sizeInBytes, false);
        } else {
            GpuMemoryTracker.trackIbo(sizeInBytes, false);
        }
        
        // Initially, there's one big free region
        freeRegions.add(new Region(0, sizeInBytes));
    }
    
    /**
     * Allocates a region from the buffer.
     * 
     * @param sizeInBytes The size of the region to allocate
     * @return A BufferRegion representing the allocated region, or null if no suitable region is available
     */
    public synchronized BufferRegion allocate(int sizeInBytes) {
        if (disposed) {
            throw new IllegalStateException("Cannot allocate from a disposed buffer pool");
        }
        
        // Find the best fit region (smallest region that fits)
        Region bestFit = null;
        int bestFitIndex = -1;
        
        for (int i = 0; i < freeRegions.size(); i++) {
            Region region = freeRegions.get(i);
            
            if (region.size >= sizeInBytes && (bestFit == null || region.size < bestFit.size)) {
                bestFit = region;
                bestFitIndex = i;
            }
        }
        
        // If no suitable region was found, return null
        if (bestFit == null) {
            return null;
        }
        
        // Remove the region from the free list
        freeRegions.remove(bestFitIndex);
        
        // If the region is larger than what we need, split it
        if (bestFit.size > sizeInBytes) {
            // Add the remainder back to the free list
            freeRegions.add(new Region(bestFit.offset + sizeInBytes, bestFit.size - sizeInBytes));
            
            // Sort the free list by offset to facilitate merging adjacent regions
            Collections.sort(freeRegions, Comparator.comparingInt(r -> r.offset));
        }
        
        // Create and return a buffer region
        return new BufferRegion(this, bufferId, bufferType, bestFit.offset, sizeInBytes);
    }
    
    /**
     * Frees a region back to the pool.
     * 
     * @param offset The offset of the region
     * @param size The size of the region
     */
    public synchronized void free(int offset, int size) {
        if (disposed) {
            return;
        }
        
        // Create a new free region
        Region newRegion = new Region(offset, size);
        
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
            
            // If the regions are adjacent, merge them
            if (current.offset + current.size == next.offset) {
                current.size += next.size;
                freeRegions.remove(i + 1);
            } else {
                i++;
            }
        }
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
    public synchronized void dispose() {
        if (!disposed) {
            glDeleteBuffers(bufferId);
            
            // Track GPU memory deallocation
            if (bufferType == GL_ARRAY_BUFFER) {
                GpuMemoryTracker.trackVbo(totalSize, true);
            } else {
                GpuMemoryTracker.trackIbo(totalSize, true);
            }
            
            freeRegions.clear();
            disposed = true;
        }
    }
    
    /**
     * Updates a region of the buffer with data.
     * 
     * @param offset The offset to update
     * @param data The data to upload
     * @param dataSize The size of the data in bytes
     */
    public void updateRegion(int offset, ByteBuffer data, int dataSize) {
        if (disposed) {
            throw new IllegalStateException("Cannot update a disposed buffer pool");
        }
        
        glBindBuffer(bufferType, bufferId);
        glBufferSubData(bufferType, offset, data);
        glBindBuffer(bufferType, 0);
    }
    
    /**
     * A region in the buffer.
     */
    private static class Region {
        int offset;
        int size;
        
        Region(int offset, int size) {
            this.offset = offset;
            this.size = size;
        }
    }
    
    /**
     * A region allocated from the buffer pool.
     */
    public static class BufferRegion {
        private final OpenGLBufferPool pool;
        private final int bufferId;
        private final int bufferType;
        private final int offset;
        private final int size;
        private boolean freed = false;
        
        BufferRegion(OpenGLBufferPool pool, int bufferId, int bufferType, int offset, int size) {
            this.pool = pool;
            this.bufferId = bufferId;
            this.bufferType = bufferType;
            this.offset = offset;
            this.size = size;
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
         * Gets the offset of the region in the buffer.
         * 
         * @return The offset in bytes
         */
        public int getOffset() {
            return offset;
        }
        
        /**
         * Gets the size of the region.
         * 
         * @return The size in bytes
         */
        public int getSize() {
            return size;
        }
        
        /**
         * Frees the region back to the pool.
         */
        public void free() {
            if (!freed) {
                pool.free(offset, size);
                freed = true;
            }
        }
        
        /**
         * Checks if the region has been freed.
         * 
         * @return true if the region has been freed, false otherwise
         */
        public boolean isFreed() {
            return freed;
        }
    }
} 