package com.mojang.minecraft.renderer.graphics.allocator;

import com.mojang.minecraft.renderer.graphics.GraphicsResource;

/**
 * Backend-neutral provider of buffer-memory allocations.
 *
 * @param <A> allocation type returned by this allocator
 */
public interface BufferAllocator<A extends BufferAllocation> extends GraphicsResource {

    /**
     * Allocates a buffer-memory handle.
     * Implementations may satisfy this via pooling/sub-allocation or dedicated backing memory.
     *
     * @param sizeInBytes requested allocation size in bytes
     * @return allocation handle, or null if allocation could not be satisfied
     */
    A allocate(int sizeInBytes);

    /**
     * @return allocator stats useful for diagnostics and debugging
     */
    String getStats();

}
