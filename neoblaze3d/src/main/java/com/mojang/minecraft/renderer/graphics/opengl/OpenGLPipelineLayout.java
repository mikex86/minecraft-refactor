package com.mojang.minecraft.renderer.graphics.opengl;

import com.mojang.minecraft.renderer.graphics.PipelineLayout;

import java.util.List;

/**
 * OpenGL implementation of PipelineLayout.
 */
public final class OpenGLPipelineLayout implements PipelineLayout {
    private final Descriptor descriptor;
    private boolean disposed;

    public OpenGLPipelineLayout(Descriptor descriptor) {
        if (descriptor == null) {
            throw new IllegalArgumentException("descriptor cannot be null");
        }
        this.descriptor = descriptor;
    }

    @Override
    public String getDebugName() {
        return descriptor.getDebugName();
    }

    @Override
    public List<Binding> getBindings() {
        return descriptor.getBindings();
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

