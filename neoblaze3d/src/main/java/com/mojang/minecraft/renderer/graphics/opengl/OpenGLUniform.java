package com.mojang.minecraft.renderer.graphics.opengl;

import com.mojang.minecraft.renderer.graphics.Uniform;

import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL15.GL_DYNAMIC_DRAW;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL15.glBufferSubData;
import static org.lwjgl.opengl.GL15.glDeleteBuffers;
import static org.lwjgl.opengl.GL15.glGenBuffers;
import static org.lwjgl.opengl.GL31.GL_UNIFORM_BUFFER;

/**
 * OpenGL uniform object backed by a dedicated UBO payload buffer.
 */
final class OpenGLUniform implements Uniform {
    private final int binding;
    private final ValueType type;
    private final int sizeInBytes;
    private final int bufferId;
    private final ByteBuffer stagingBuffer;
    private boolean dirty;
    private boolean disposed;

    OpenGLUniform(int binding, ValueType type) {
        if (binding < 0) {
            throw new IllegalArgumentException("binding must be >= 0");
        }
        if (type == null) {
            throw new IllegalArgumentException("type cannot be null");
        }
        this.binding = binding;
        this.type = type;
        this.sizeInBytes = std140SizeInBytes(type);
        this.stagingBuffer = MemoryUtil.memAlloc(sizeInBytes);
        this.bufferId = glGenBuffers();
        this.dirty = true;

        glBindBuffer(GL_UNIFORM_BUFFER, bufferId);
        glBufferData(GL_UNIFORM_BUFFER, sizeInBytes, GL_DYNAMIC_DRAW);
        glBindBuffer(GL_UNIFORM_BUFFER, 0);
    }

    @Override
    public int getBinding() {
        return binding;
    }

    @Override
    public ValueType getType() {
        return type;
    }

    @Override
    public int getSizeInBytes() {
        return sizeInBytes;
    }

    @Override
    public void setInt(int value) {
        ensureNotDisposed();
        assertType(ValueType.INT1);
        stagingBuffer.putInt(0, value);
        markDirty();
    }

    @Override
    public void setFloat(float value) {
        ensureNotDisposed();
        assertType(ValueType.FLOAT1);
        stagingBuffer.putFloat(0, value);
        markDirty();
    }

    @Override
    public void setFloat2(float x, float y) {
        ensureNotDisposed();
        assertType(ValueType.FLOAT2);
        stagingBuffer.putFloat(0, x);
        stagingBuffer.putFloat(4, y);
        markDirty();
    }

    @Override
    public void setFloat3(float x, float y, float z) {
        ensureNotDisposed();
        assertType(ValueType.FLOAT3);
        stagingBuffer.putFloat(0, x);
        stagingBuffer.putFloat(4, y);
        stagingBuffer.putFloat(8, z);
        markDirty();
    }

    @Override
    public void setFloat4(float x, float y, float z, float w) {
        ensureNotDisposed();
        assertType(ValueType.FLOAT4);
        stagingBuffer.putFloat(0, x);
        stagingBuffer.putFloat(4, y);
        stagingBuffer.putFloat(8, z);
        stagingBuffer.putFloat(12, w);
        markDirty();
    }

    @Override
    public void setFloatBuffer(FloatBuffer values) {
        ensureNotDisposed();
        if (type == ValueType.INT1) {
            throw new IllegalStateException("INT1 uniforms must be set with setInt");
        }
        if (values == null) {
            throw new IllegalArgumentException("values cannot be null");
        }

        FloatBuffer source = values.duplicate();
        source.position(0);

        if (type == ValueType.MAT3) {
            if (source.remaining() < 9) {
                throw new IllegalArgumentException("Expected at least 9 floats for MAT3, got " + source.remaining());
            }
            // std140 mat3 uses three vec4 columns (48 bytes)
            for (int column = 0; column < 3; column++) {
                int base = column * 16;
                stagingBuffer.putFloat(base, source.get());
                stagingBuffer.putFloat(base + 4, source.get());
                stagingBuffer.putFloat(base + 8, source.get());
                stagingBuffer.putFloat(base + 12, 0.0f);
            }
            markDirty();
            return;
        }

        int expected = elementCount(type);
        if (source.remaining() < expected) {
            throw new IllegalArgumentException("Expected at least " + expected + " floats, got " + source.remaining());
        }
        for (int i = 0; i < expected; i++) {
            stagingBuffer.putFloat(i * 4, source.get());
        }
        markDirty();
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        glDeleteBuffers(bufferId);
        MemoryUtil.memFree(stagingBuffer);
        disposed = true;
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }

    int getBufferId() {
        return bufferId;
    }

    void uploadIfDirty() {
        ensureNotDisposed();
        if (!dirty) {
            return;
        }
        ByteBuffer uploadData = stagingBuffer.duplicate();
        uploadData.position(0);
        uploadData.limit(sizeInBytes);

        glBindBuffer(GL_UNIFORM_BUFFER, bufferId);
        glBufferSubData(GL_UNIFORM_BUFFER, 0, uploadData);
        glBindBuffer(GL_UNIFORM_BUFFER, 0);
        dirty = false;
    }

    private void markDirty() {
        dirty = true;
    }

    private void assertType(ValueType expected) {
        if (type != expected) {
            throw new IllegalStateException("Uniform type mismatch. Expected " + expected + ", got " + type);
        }
    }

    private void ensureNotDisposed() {
        if (disposed) {
            throw new IllegalStateException("Uniform has been disposed");
        }
    }

    private static int std140SizeInBytes(ValueType type) {
        switch (type) {
            case INT1:
            case FLOAT1:
            case FLOAT2:
            case FLOAT3:
            case FLOAT4:
                return 16;
            case MAT3:
                return 48;
            case MAT4:
                return 64;
            default:
                throw new IllegalArgumentException("Unsupported uniform type: " + type);
        }
    }

    private static int elementCount(ValueType type) {
        switch (type) {
            case FLOAT1:
                return 1;
            case FLOAT2:
                return 2;
            case FLOAT3:
                return 3;
            case FLOAT4:
                return 4;
            case MAT4:
                return 16;
            default:
                throw new IllegalArgumentException("Unsupported float-buffer uniform type: " + type);
        }
    }
}

