package com.mojang.minecraft.renderer.graphics;

import com.mojang.minecraft.renderer.graphics.allocator.BufferAllocation;
import com.mojang.minecraft.renderer.graphics.allocator.BufferAllocator;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums.*;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Main interface for the graphics API abstraction layer.
 * This is the entry point for all graphics operations.
 * It provides methods to create and manage graphics resources.
 */
public interface GraphicsAPI {

    enum Backend {
        OPENGL,
        VULKAN
    }

    enum BufferBinding {
        VERTEX,
        INDEX
    }

    enum BufferAllocatorHint {
        POOLED,
        DEDICATED
    }

    /**
     * Returns the logical backend selected by the runtime.
     */
    Backend getBackend();

    /**
     * Initialize the graphics API.
     * This should be called once at the start of the application.
     */
    void initialize();

    /**
     * Shutdown the graphics API.
     * This should be called once at the end of the application.
     */
    void shutdown();

    /**
     * Begins recording/rendering for a frame using a reusable command buffer.
     * The returned command buffer is reset and ready for new commands.
     */
    CommandBuffer beginFrame();

    /**
     * Ends frame command recording/submission.
     */
    void endFrame();

    /**
     * Blocks until the backend has finished processing outstanding GPU work.
     * Default implementation is a no-op for backends that do not require explicit synchronization.
     */
    default void waitIdle() {
    }

    /**
     * Creates a backend-managed allocator for a buffer binding point.
     * Allocation strategy is hint-based; backends may substitute an equivalent strategy.
     * Caller owns allocator lifecycle and must dispose the returned allocator.
     */
    BufferAllocator<? extends BufferAllocation> createAllocator(BufferBinding binding, BufferAllocatorHint hint);

    /**
     * Creates a vertex buffer.
     *
     * @param usage The intended usage pattern of the buffer
     * @return A new vertex buffer
     */
    VertexBuffer createVertexBuffer(BufferUsage usage);

    /**
     * Creates a vertex buffer backed by the provided allocator.
     * The allocator decides whether this is pooled/sub-allocated or dedicated.
     */
    VertexBuffer createVertexBuffer(BufferUsage usage, BufferAllocator<? extends BufferAllocation> allocator, int sizeInBytes);

    /**
     * Creates an index buffer.
     *
     * @param usage The intended usage pattern of the buffer
     * @return A new index buffer
     */
    IndexBuffer createIndexBuffer(BufferUsage usage);

    /**
     * Creates an index buffer backed by the provided allocator.
     * The allocator decides whether this is pooled/sub-allocated or dedicated.
     */
    IndexBuffer createIndexBuffer(BufferUsage usage, BufferAllocator<? extends BufferAllocation> allocator, int sizeInBytes);

    /**
     * Creates a texture.
     *
     * @param width  The texture width
     * @param height The texture height
     * @param format The texture format
     * @return A new texture
     */
    Texture createTexture(int width, int height, TextureFormat format);

    /**
     * Creates a texture from raw image data.
     *
     * @param width  The texture width
     * @param height The texture height
     * @param format The texture format
     * @param data   The raw image data
     * @return A new texture
     */
    Texture createTexture(int width, int height, TextureFormat format, ByteBuffer data);

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

    /**
     * Creates a pipeline layout.
     *
     * @param descriptor Layout descriptor
     * @return A new pipeline layout
     */
    PipelineLayout createPipelineLayout(PipelineLayout.Descriptor descriptor);

    /**
     * Creates a graphics pipeline.
     *
     * @param descriptor Pipeline descriptor
     * @return A new pipeline
     */
    Pipeline createPipeline(Pipeline.Descriptor descriptor);

    /**
     * Creates a reusable uniform object bound to a numeric shader location.
     */
    Uniform createUniform(int binding, Uniform.ValueType type);

    /**
     * Creates a backend shader program from precompiled binaries.
     */
    ShaderProgram createShaderProgramFromPrecompiled(String vertexBinaryPath, String fragmentBinaryPath) throws IOException;

}
