package com.mojang.minecraft.renderer.graphics.opengl;

import com.mojang.minecraft.renderer.graphics.CommandBuffer;
import com.mojang.minecraft.renderer.graphics.GraphicsAPI;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums.BufferUsage;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums.TextureFormat;
import com.mojang.minecraft.renderer.graphics.IndexBuffer;
import com.mojang.minecraft.renderer.graphics.Pipeline;
import com.mojang.minecraft.renderer.graphics.PipelineLayout;
import com.mojang.minecraft.renderer.graphics.ShaderProgram;
import com.mojang.minecraft.renderer.graphics.Texture;
import com.mojang.minecraft.renderer.graphics.Uniform;
import com.mojang.minecraft.renderer.graphics.VertexBuffer;
import com.mojang.minecraft.renderer.graphics.allocator.BufferAllocation;
import com.mojang.minecraft.renderer.graphics.allocator.BufferAllocator;

import java.io.IOException;
import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.GL_LEQUAL;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.glBlendFunc;
import static org.lwjgl.opengl.GL11.glClearDepth;
import static org.lwjgl.opengl.GL11.glDepthFunc;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_DYNAMIC_DRAW;
import static org.lwjgl.opengl.GL15.GL_ELEMENT_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL15.GL_STREAM_DRAW;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;

/**
 * OpenGL implementation of the GraphicsAPI interface.
 * This class owns device/resource lifecycle; draw commands are recorded via {@link OpenGLCommandBuffer}.
 */
public class OpenGLGraphicsAPI implements GraphicsAPI {
    private static final long DEFAULT_POOL_SIZE = 128L * 1024L * 1024L; // 128 MB

    private final OpenGLCommandBuffer frameCommandBuffer = new OpenGLCommandBuffer();

    // Default VAO (required for OpenGL core profile)
    private int defaultVaoId;

    @Override
    public CommandBuffer beginFrame() {
        frameCommandBuffer.reset();
        return frameCommandBuffer;
    }

    @Override
    public void endFrame() {
        frameCommandBuffer.executeRecordedCommands();
    }

    @Override
    public void initialize() {
        defaultVaoId = glGenVertexArrays();
        glBindVertexArray(defaultVaoId);

        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glClearDepth(1.0f);
        glDepthFunc(GL_LEQUAL);

        frameCommandBuffer.reset();
        frameCommandBuffer.initializeResources();
    }

    @Override
    public void shutdown() {
        frameCommandBuffer.dispose();
        glDeleteVertexArrays(defaultVaoId);
    }

    @Override
    public BufferAllocator<? extends BufferAllocation> createAllocator(BufferBinding binding, BufferAllocatorHint hint) {
        int bufferType = bufferTypeFor(binding);
        BufferAllocator<? extends BufferAllocation> allocator;
        if (hint == BufferAllocatorHint.POOLED) {
            allocator = new OpenGLPooledAllocator(bufferType, DEFAULT_POOL_SIZE);
        } else {
            allocator = new OpenGLDedicatedAllocator(bufferType);
        }
        return allocator;
    }

    @Override
    public VertexBuffer createVertexBuffer(BufferUsage usage) {
        return new OpenGLVertexBuffer(translateBufferUsage(usage));
    }

    @Override
    public VertexBuffer createVertexBuffer(BufferUsage usage, BufferAllocator<? extends BufferAllocation> allocator, int sizeInBytes) {
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
        if (!(allocation instanceof OpenGLBufferAllocation)) {
            allocation.free();
            throw new IllegalArgumentException("Allocator does not provide OpenGL buffer allocations");
        }

        OpenGLBufferAllocation glAllocation = (OpenGLBufferAllocation) allocation;
        if (glAllocation.getBufferType() != GL_ARRAY_BUFFER) {
            glAllocation.free();
            throw new IllegalArgumentException("Allocator buffer type is not GL_ARRAY_BUFFER");
        }

        return new OpenGLPooledVertexBuffer(glAllocation);
    }

    @Override
    public IndexBuffer createIndexBuffer(BufferUsage usage) {
        return new OpenGLIndexBuffer(translateBufferUsage(usage));
    }

    @Override
    public IndexBuffer createIndexBuffer(BufferUsage usage, BufferAllocator<? extends BufferAllocation> allocator, int sizeInBytes) {
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
        if (!(allocation instanceof OpenGLBufferAllocation)) {
            allocation.free();
            throw new IllegalArgumentException("Allocator does not provide OpenGL buffer allocations");
        }

        OpenGLBufferAllocation glAllocation = (OpenGLBufferAllocation) allocation;
        if (glAllocation.getBufferType() != GL_ELEMENT_ARRAY_BUFFER) {
            glAllocation.free();
            throw new IllegalArgumentException("Allocator buffer type is not GL_ELEMENT_ARRAY_BUFFER");
        }

        return new OpenGLPooledIndexBuffer(glAllocation);
    }

    @Override
    public Texture createTexture(int width, int height, TextureFormat format) {
        return new OpenGLTexture(width, height, format);
    }

    @Override
    public Texture createTexture(int width, int height, TextureFormat format, ByteBuffer data) {
        OpenGLTexture texture = new OpenGLTexture(width, height, format);
        texture.update(0, 0, width, height, data);
        return texture;
    }

    @Override
    public PipelineLayout createPipelineLayout(PipelineLayout.Descriptor descriptor) {
        return new OpenGLPipelineLayout(descriptor);
    }

    @Override
    public Pipeline createPipeline(Pipeline.Descriptor descriptor) {
        return new OpenGLPipeline(descriptor);
    }

    @Override
    public Uniform createUniform(int binding, Uniform.ValueType type) {
        return new OpenGLUniform(binding, type);
    }

    @Override
    public ShaderProgram createShaderProgramFromPrecompiled(String vertexBinaryPath, String fragmentBinaryPath) throws IOException {
        return OpenGLShaderProgram.fromPrecompiledBinaries(vertexBinaryPath, fragmentBinaryPath);
    }

    private static int bufferTypeFor(BufferBinding binding) {
        if (binding == BufferBinding.VERTEX) {
            return GL_ARRAY_BUFFER;
        }
        return GL_ELEMENT_ARRAY_BUFFER;
    }

    private static int translateBufferUsage(BufferUsage usage) {
        switch (usage) {
            case STATIC:
                return GL_STATIC_DRAW;
            case DYNAMIC:
                return GL_DYNAMIC_DRAW;
            case STREAM:
                return GL_STREAM_DRAW;
            default:
                return GL_STATIC_DRAW;
        }
    }
}
