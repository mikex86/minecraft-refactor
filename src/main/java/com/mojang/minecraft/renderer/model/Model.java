package com.mojang.minecraft.renderer.model;

import com.mojang.minecraft.renderer.Disposable;
import com.mojang.minecraft.renderer.graphics.CommandBuffer;
import com.mojang.minecraft.renderer.graphics.MatrixStack;
import com.mojang.minecraft.renderer.graphics.MutableDescriptorSet;

/**
 * Interface for all 3D models in the game.
 * Standardizes the API for rendering and resource management.
 */
public interface Model<T> extends Disposable {

    /**
     * Renders the model with the given animation time.
     *
     * @param commandBuffer     The commandBuffer API to use for rendering
     * @param descriptorSet Descriptor set used by this model's draws
     * @param drawable     The drawable object to render
     * @param partialTicks The partial ticks for animation smoothing
     */
    void render(CommandBuffer commandBuffer, MatrixStack matrixStack, MutableDescriptorSet descriptorSet, T drawable, float partialTicks);
}
