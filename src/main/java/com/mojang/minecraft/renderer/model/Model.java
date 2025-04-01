package com.mojang.minecraft.renderer.model;

import com.mojang.minecraft.renderer.Disposable;

/**
 * Interface for all 3D models in the game.
 * Standardizes the API for rendering and resource management.
 */
public interface Model extends Disposable {
    /**
     * Renders the model with the given animation time.
     * 
     * @param time Animation time in seconds
     */
    void render(float time);
} 