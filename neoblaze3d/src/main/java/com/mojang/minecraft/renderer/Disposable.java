package com.mojang.minecraft.renderer;

/**
 * Interface for resources that need to be explicitly disposed.
 */
public interface Disposable {
    /**
     * Releases any resources held by this object.
     * Should be called when the object is no longer needed.
     */
    void dispose();
} 