package com.mojang.minecraft.renderer.graphics.vulkan;

import com.mojang.minecraft.renderer.graphics.CommandBuffer;
import com.mojang.minecraft.renderer.graphics.GraphicsAPI;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums.BufferUsage;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums.TextureFormat;
import com.mojang.minecraft.renderer.graphics.GraphicsFactory;
import com.mojang.minecraft.renderer.graphics.IndexBuffer;
import com.mojang.minecraft.renderer.graphics.Pipeline;
import com.mojang.minecraft.renderer.graphics.PipelineLayout;
import com.mojang.minecraft.renderer.graphics.ResourceState;
import com.mojang.minecraft.renderer.graphics.ShaderProgram;
import com.mojang.minecraft.renderer.graphics.Texture;
import com.mojang.minecraft.renderer.graphics.Uniform;
import com.mojang.minecraft.renderer.graphics.VertexBuffer;
import com.mojang.minecraft.renderer.graphics.allocator.BufferAllocation;
import com.mojang.minecraft.renderer.graphics.allocator.BufferAllocator;
import org.lwjgl.vulkan.VK10;

import java.io.IOException;
import java.nio.ByteBuffer;

public final class VulkanGraphicsAPI implements GraphicsAPI {
    private final VulkanContext context;
    private final VulkanCommandBuffer frameCommandBuffer;

    public VulkanGraphicsAPI() {
        long windowHandle = GraphicsFactory.getWindowHandleHint();
        if (windowHandle == 0L) {
            throw new IllegalStateException("Vulkan backend requires a GLFW window handle before GraphicsFactory.getGraphicsAPI() is called");
        }
        this.context = new VulkanContext(windowHandle);
        this.frameCommandBuffer = new VulkanCommandBuffer(context);
    }

    @Override
    public Backend getBackend() {
        return Backend.VULKAN;
    }

    @Override
    public void initialize() {
        context.initialize();
    }

    @Override
    public void shutdown() {
        context.dispose();
    }

    @Override
    public CommandBuffer beginFrame() {
        context.beginFrame();
        frameCommandBuffer.resetForFrame();
        return frameCommandBuffer;
    }

    @Override
    public void endFrame() {
        if (frameCommandBuffer == null) {
            return;
        }
        context.endFrame();
    }

    @Override
    public BufferAllocator<? extends BufferAllocation> createAllocator(BufferBinding binding, BufferAllocatorHint hint) {
        int usageFlags = usageFlagsForBinding(binding);
        if (hint == BufferAllocatorHint.POOLED) {
            return new VulkanPooledAllocator(context, usageFlags);
        }
        return new VulkanDedicatedAllocator(context, usageFlags);
    }

    @Override
    public VertexBuffer createVertexBuffer(BufferUsage usage) {
        return new VulkanVertexBuffer(context, usageFlagsForVertexBuffer(usage));
    }

    @Override
    public VertexBuffer createVertexBuffer(BufferUsage usage,
                                           BufferAllocator<? extends BufferAllocation> allocator,
                                           int sizeInBytes) {
        if (allocator == null) {
            throw new IllegalArgumentException("allocator cannot be null");
        }
        if (sizeInBytes <= 0) {
            throw new IllegalArgumentException("sizeInBytes must be > 0");
        }

        BufferAllocation allocation = allocator.allocate(sizeInBytes);
        if (allocation == null) {
            return null;
        }
        if (!(allocation instanceof VulkanBufferAllocation)) {
            allocation.free();
            throw new IllegalArgumentException("Allocator does not provide Vulkan buffer allocations");
        }

        VulkanBufferAllocation vkAllocation = (VulkanBufferAllocation) allocation;
        if ((vkAllocation.getUsageFlags() & VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT) == 0) {
            vkAllocation.free();
            throw new IllegalArgumentException("Allocator allocation is not vertex-buffer compatible");
        }

        return new VulkanAllocatedVertexBuffer(context, vkAllocation);
    }

    @Override
    public IndexBuffer createIndexBuffer(BufferUsage usage) {
        return new VulkanIndexBuffer(context, usageFlagsForIndexBuffer(usage));
    }

    @Override
    public IndexBuffer createIndexBuffer(BufferUsage usage,
                                         BufferAllocator<? extends BufferAllocation> allocator,
                                         int sizeInBytes) {
        if (allocator == null) {
            throw new IllegalArgumentException("allocator cannot be null");
        }
        if (sizeInBytes <= 0) {
            throw new IllegalArgumentException("sizeInBytes must be > 0");
        }

        BufferAllocation allocation = allocator.allocate(sizeInBytes);
        if (allocation == null) {
            return null;
        }
        if (!(allocation instanceof VulkanBufferAllocation)) {
            allocation.free();
            throw new IllegalArgumentException("Allocator does not provide Vulkan buffer allocations");
        }

        VulkanBufferAllocation vkAllocation = (VulkanBufferAllocation) allocation;
        if ((vkAllocation.getUsageFlags() & VK10.VK_BUFFER_USAGE_INDEX_BUFFER_BIT) == 0) {
            vkAllocation.free();
            throw new IllegalArgumentException("Allocator allocation is not index-buffer compatible");
        }

        return new VulkanAllocatedIndexBuffer(context, vkAllocation);
    }

    @Override
    public Texture createTexture(int width, int height, TextureFormat format) {
        return new VulkanTexture(context, width, height, format);
    }

    @Override
    public Texture createTexture(int width, int height, TextureFormat format, ByteBuffer data) {
        VulkanTexture texture = new VulkanTexture(context, width, height, format);
        texture.transition(ResourceState.TextureAccess.UNDEFINED, ResourceState.TextureAccess.TRANSFER_DST);
        texture.update(0, 0, width, height, data);
        texture.transition(ResourceState.TextureAccess.TRANSFER_DST, ResourceState.TextureAccess.SHADER_READ);
        return texture;
    }

    @Override
    public void transitionTexture(Texture texture,
                                  ResourceState.TextureAccess expectedOldAccess,
                                  ResourceState.TextureAccess newAccess) {
        VulkanResourceTransitions.transitionTexture(texture, expectedOldAccess, newAccess);
    }

    @Override
    public void transitionVertexBuffer(VertexBuffer vertexBuffer,
                                       ResourceState.BufferAccess expectedOldAccess,
                                       ResourceState.BufferAccess newAccess) {
        VulkanResourceTransitions.transitionVertexBuffer(vertexBuffer, expectedOldAccess, newAccess);
    }

    @Override
    public void transitionIndexBuffer(IndexBuffer indexBuffer,
                                      ResourceState.BufferAccess expectedOldAccess,
                                      ResourceState.BufferAccess newAccess) {
        VulkanResourceTransitions.transitionIndexBuffer(indexBuffer, expectedOldAccess, newAccess);
    }

    @Override
    public PipelineLayout createPipelineLayout(PipelineLayout.Descriptor descriptor) {
        return new VulkanPipelineLayout(context, descriptor);
    }

    @Override
    public Pipeline createPipeline(Pipeline.Descriptor descriptor) {
        return new VulkanPipeline(context, descriptor);
    }

    @Override
    public Uniform createUniform(int binding, Uniform.ValueType type) {
        return new VulkanUniform(binding, type);
    }

    @Override
    public ShaderProgram createShaderProgramFromPrecompiled(String vertexBinaryPath, String fragmentBinaryPath) throws IOException {
        return VulkanShaderProgram.fromPrecompiledBinaries(context, vertexBinaryPath, fragmentBinaryPath);
    }

    @Override
    public void waitIdle() {
        context.waitIdle();
    }

    /**
     * Requests a readback capture of the currently recording frame.
     * Must be called after render pass work has been recorded and before {@link #endFrame()}.
     */
    public void requestCurrentFrameCaptureRgba() {
        context.requestCurrentFrameCaptureRgba();
    }

    /**
     * Returns RGBA8, bottom-up frame data for the last submitted captured frame.
     * Note: This function is not optimized and only for debug purposes.
     */
    public ByteBuffer consumeLastFrameCaptureRgba() {
        return context.consumeLastFrameCaptureRgba();
    }

    public static boolean isRuntimeSupported() {
        return VulkanLoader.ensureGlfwVulkanLoaded();
    }

    private static int usageFlagsForBinding(BufferBinding binding) {
        if (binding == BufferBinding.VERTEX) {
            return VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;
        }
        return VK10.VK_BUFFER_USAGE_INDEX_BUFFER_BIT;
    }

    private static int usageFlagsForVertexBuffer(BufferUsage usage) {
        return VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT | VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT;
    }

    private static int usageFlagsForIndexBuffer(BufferUsage usage) {
        return VK10.VK_BUFFER_USAGE_INDEX_BUFFER_BIT | VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT;
    }
}
