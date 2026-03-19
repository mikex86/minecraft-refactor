package com.mojang.minecraft.renderer.shader;

import java.nio.FloatBuffer;

/**
 * Helper base class for typed shader wrappers backed by an OpenGL {@link Shader}.
 */
public abstract class DelegatingShader implements IShader {
    protected final Shader delegate;

    protected DelegatingShader(Shader delegate) {
        this.delegate = delegate;
    }

    @Override
    public void use() {
        delegate.use();
    }

    @Override
    public void detach() {
        delegate.detach();
    }

    protected final void setUniform(String name, boolean value) {
        delegate.setUniform(name, value);
    }

    protected final void setUniform(String name, int value) {
        delegate.setUniform(name, value);
    }

    protected final void setUniform(String name, float value) {
        delegate.setUniform(name, value);
    }

    protected final void setUniform(String name, float x, float y) {
        delegate.setUniform(name, x, y);
    }

    protected final void setUniform(String name, float x, float y, float z) {
        delegate.setUniform(name, x, y, z);
    }

    protected final void setUniform(String name, float x, float y, float z, float w) {
        delegate.setUniform(name, x, y, z, w);
    }

    protected final void setUniform4fv(String name, FloatBuffer buffer) {
        delegate.setUniform4fv(name, buffer);
    }

    protected final void setUniformMatrix4fv(String name, FloatBuffer matrix) {
        delegate.setUniformMatrix4fv(name, matrix);
    }

    @Override
    public void dispose() {
        delegate.dispose();
    }
}
