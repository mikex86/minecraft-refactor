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
     * Begins a render pass for the specified framebuffer region.
     * Draw calls must happen between beginRenderPass/endRenderPass.
     */
    void beginRenderPass(RenderPassAttachments attachments, int x, int y, int width, int height);

    /**
     * Ends the active render pass.
     */
    void endRenderPass();

    /**
     * Draws primitives using explicit vertex/index buffers.
     */
    void draw(PrimitiveType type, VertexBuffer vertexBuffer, IndexBuffer indexBuffer, int start, int count);

    /**
     * Draws a reusable batch of draw commands.
     */
    void drawBatch(DrawBatch drawBatch);

    /**
     * Binds a descriptor set compatible with the currently bound pipeline.
     */
    void bindDescriptorSet(DescriptorSet descriptorSet);

    /**
     * Transitions texture access state with strict old-state validation.
     */
    void transitionTexture(Texture texture, ResourceState.TextureAccess expectedOldAccess, ResourceState.TextureAccess newAccess);

    /**
     * Transitions vertex-buffer access state with strict old-state validation.
     */
    void transitionVertexBuffer(VertexBuffer vertexBuffer, ResourceState.BufferAccess expectedOldAccess, ResourceState.BufferAccess newAccess);

    /**
     * Transitions index-buffer access state with strict old-state validation.
     */
    void transitionIndexBuffer(IndexBuffer indexBuffer, ResourceState.BufferAccess expectedOldAccess, ResourceState.BufferAccess newAccess);

}
