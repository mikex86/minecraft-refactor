package com.mojang.minecraft.renderer.graphics.opengl;

import com.mojang.minecraft.renderer.graphics.Uniform;

import java.nio.FloatBuffer;

/**
 * OpenGL uniform object that stores the latest value for a numeric uniform location.
 */
final class OpenGLUniform implements Uniform {
    private final int binding;
    private final ValueType type;
    private final float[] floatValues;
    private int intValue;
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
        this.floatValues = new float[elementCount(type)];
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
    public void setInt(int value) {
        assertType(ValueType.INT1);
        intValue = value;
    }

    @Override
    public void setFloat(float value) {
        assertType(ValueType.FLOAT1);
        floatValues[0] = value;
    }

    @Override
    public void setFloat2(float x, float y) {
        assertType(ValueType.FLOAT2);
        floatValues[0] = x;
        floatValues[1] = y;
    }

    @Override
    public void setFloat3(float x, float y, float z) {
        assertType(ValueType.FLOAT3);
        floatValues[0] = x;
        floatValues[1] = y;
        floatValues[2] = z;
    }

    @Override
    public void setFloat4(float x, float y, float z, float w) {
        assertType(ValueType.FLOAT4);
        floatValues[0] = x;
        floatValues[1] = y;
        floatValues[2] = z;
        floatValues[3] = w;
    }

    @Override
    public void setFloatBuffer(FloatBuffer values) {
        if (type == ValueType.INT1) {
            throw new IllegalStateException("INT1 uniforms must be set with setInt");
        }
        if (values == null) {
            throw new IllegalArgumentException("values cannot be null");
        }

        FloatBuffer source = values.duplicate();
        source.position(0);
        int count = floatValues.length;
        if (source.remaining() < count) {
            throw new IllegalArgumentException("Expected at least " + count + " floats, got " + source.remaining());
        }
        for (int i = 0; i < count; i++) {
            floatValues[i] = source.get();
        }
    }

    @Override
    public void dispose() {
        disposed = true;
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }

    int intValue() {
        return intValue;
    }

    float[] floatValues() {
        return floatValues;
    }

    private void assertType(ValueType expected) {
        if (type != expected) {
            throw new IllegalStateException("Uniform type mismatch. Expected " + expected + ", got " + type);
        }
    }

    private static int elementCount(ValueType type) {
        switch (type) {
            case INT1:
            case FLOAT1:
                return 1;
            case FLOAT2:
                return 2;
            case FLOAT3:
                return 3;
            case FLOAT4:
                return 4;
            case MAT3:
                return 9;
            case MAT4:
                return 16;
            default:
                throw new IllegalArgumentException("Unsupported uniform type: " + type);
        }
    }
}
