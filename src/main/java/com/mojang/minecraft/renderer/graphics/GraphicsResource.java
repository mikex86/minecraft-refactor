package com.mojang.minecraft.renderer.graphics;

import com.mojang.minecraft.renderer.Disposable;

/**
 * Base interface for all graphics resources.
 * Defines common operations for resource lifecycle management.
 */
public interface GraphicsResource extends Disposable {
    /**
     * Disposes of this resource, freeing any native resources it holds.
     * After this method is called, the resource should no longer be used.
     */
    @Override
    void dispose();
    
    /**
     * Checks if this resource has been disposed.
     * 
     * @return true if this resource has been disposed, false otherwise
     */
    boolean isDisposed();
} 