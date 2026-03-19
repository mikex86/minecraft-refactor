package com.mojang.minecraft.renderer.graphics.allocator;

import com.mojang.minecraft.renderer.graphics.GraphicsResource;

/**
 * Represents an allocated buffer-memory handle.
 * <p>
 * The underlying strategy is backend-defined and may be pooled/sub-allocated
 * or dedicated.
 */
public interface BufferAllocation extends GraphicsResource {
    /**
     * @return Allocation size in bytes.
     */
    long getSizeInBytes();

    /**
     * Releases this allocation back to its owner.
     */
    void free();

    /**
     * @return true when this allocation has been freed.
     */
    boolean isFreed();

    @Override
    default void dispose() {
        free();
    }

    @Override
    default boolean isDisposed() {
        return isFreed();
    }
}
