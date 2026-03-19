package com.mojang.minecraft.renderer.graphics;

import com.mojang.minecraft.renderer.graphics.GraphicsEnums.PrimitiveType;

/**
 * Records and executes rendering commands for a single frame.
 * Backends may execute immediately (OpenGL) or record for deferred submission
 * (Vulkan-style backends).
 */
public interface CommandBuffer {

    /**
     * Binds a graphics pipeline.
     *
     * @param pipeline Pipeline to bind, or null to unbind
     */
    void setPipeline(Pipeline pipeline);

    /**
     * Sets the viewport.
     */
    void setViewport(int x, int y, int width, int height);

    /**
     * Clears the color and/or depth buffer.
     */
    void clear(boolean clearColor, boolean clearDepth, float r, float g, float b, float a);

    /**
     * Draws primitives using explicit vertex/index buffers.
     */
    void draw(PrimitiveType type, VertexBuffer vertexBuffer, IndexBuffer indexBuffer, int start, int count);

    /**
     * Sets the active texture.
     */
    void setTexture(Texture texture);

}
