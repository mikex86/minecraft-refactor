package com.mojang.minecraft.renderer.graphics;

/**
 * Backend-neutral descriptor-set style resource bindings.
 * A descriptor set groups uniforms/textures for a compatible pipeline layout.
 */
public interface DescriptorSet extends GraphicsResource {

    PipelineLayout getLayout();

    Uniform getUniform(int binding);

    Texture getTexture(int binding);

}
