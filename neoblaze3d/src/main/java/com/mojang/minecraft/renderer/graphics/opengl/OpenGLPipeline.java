package com.mojang.minecraft.renderer.graphics.opengl;

import com.mojang.minecraft.renderer.graphics.Pipeline;
import com.mojang.minecraft.renderer.graphics.PipelineLayout;
import com.mojang.minecraft.renderer.graphics.ShaderProgram;

/**
 * OpenGL implementation of Pipeline.
 */
public final class OpenGLPipeline implements Pipeline {
    private final Descriptor descriptor;
    private boolean disposed;

    public OpenGLPipeline(Descriptor descriptor) {
        if (descriptor == null) {
            throw new IllegalArgumentException("descriptor cannot be null");
        }
        if (descriptor.getLayout() == null) {
            throw new IllegalArgumentException("pipeline layout cannot be null");
        }
        if (!(descriptor.getLayout() instanceof OpenGLPipelineLayout)) {
            throw new IllegalArgumentException("layout must be an OpenGLPipelineLayout");
        }
        if (descriptor.getProgram() == null) {
            throw new IllegalArgumentException("program cannot be null");
        }
        if (descriptor.getBlendState() == null) {
            throw new IllegalArgumentException("blend state cannot be null");
        }
        if (descriptor.getDepthState() == null) {
            throw new IllegalArgumentException("depth state cannot be null");
        }
        if (descriptor.getRasterizerState() == null) {
            throw new IllegalArgumentException("rasterizer state cannot be null");
        }
        this.descriptor = descriptor;
    }

    @Override
    public PipelineLayout getLayout() {
        return descriptor.getLayout();
    }

    @Override
    public ShaderProgram getProgram() {
        return descriptor.getProgram();
    }

    @Override
    public BlendState getBlendState() {
        return descriptor.getBlendState();
    }

    @Override
    public DepthState getDepthState() {
        return descriptor.getDepthState();
    }

    @Override
    public RasterizerState getRasterizerState() {
        return descriptor.getRasterizerState();
    }

    @Override
    public String getDebugName() {
        return descriptor.getDebugName();
    }

    @Override
    public void dispose() {
        disposed = true;
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }
}
