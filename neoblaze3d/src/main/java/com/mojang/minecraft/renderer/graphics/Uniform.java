package com.mojang.minecraft.renderer.graphics;

import java.nio.FloatBuffer;

/**
 * Backend-neutral uniform object.
 * Uniform instances are expected to be created once and reused in hot paths.
 */
public interface Uniform extends GraphicsResource {

    enum ValueType {
        INT1,
        FLOAT1,
        FLOAT2,
        FLOAT3,
        FLOAT4,
        MAT3,
        MAT4
    }

    int getBinding();

    ValueType getType();

    void setInt(int value);

    void setFloat(float value);

    void setFloat2(float x, float y);

    void setFloat3(float x, float y, float z);

    void setFloat4(float x, float y, float z, float w);

    /**
     * Sets matrix/vector data from a float buffer.
     * The expected element count depends on {@link #getType()}.
     */
    void setFloatBuffer(FloatBuffer values);
}

