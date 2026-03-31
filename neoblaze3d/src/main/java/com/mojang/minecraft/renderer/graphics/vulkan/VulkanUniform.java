package com.mojang.minecraft.renderer.graphics.vulkan;

import com.mojang.minecraft.renderer.graphics.Uniform;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

final class VulkanUniform implements Uniform {
    private final int binding;
    private final ValueType type;
    private final int sizeInBytes;
    private final ByteBuffer stagingBuffer;

    private boolean disposed;
    private int mutationVersion;
    private int cachedSnapshotFrameId;
    private int cachedSnapshotVersion;
    private int cachedSnapshotOffset;

    VulkanUniform(int binding, ValueType type) {
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
        this.mutationVersion = 1;
        this.cachedSnapshotFrameId = -1;
        this.cachedSnapshotVersion = -1;
        this.cachedSnapshotOffset = -1;
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
        markMutated();
    }

    @Override
    public void setFloat(float value) {
        ensureNotDisposed();
        assertType(ValueType.FLOAT1);
        stagingBuffer.putFloat(0, value);
        markMutated();
    }

    @Override
    public void setFloat2(float x, float y) {
        ensureNotDisposed();
        assertType(ValueType.FLOAT2);
        stagingBuffer.putFloat(0, x);
        stagingBuffer.putFloat(4, y);
        markMutated();
    }

    @Override
    public void setFloat3(float x, float y, float z) {
        ensureNotDisposed();
        assertType(ValueType.FLOAT3);
        stagingBuffer.putFloat(0, x);
        stagingBuffer.putFloat(4, y);
        stagingBuffer.putFloat(8, z);
        markMutated();
    }

    @Override
    public void setFloat4(float x, float y, float z, float w) {
        ensureNotDisposed();
        assertType(ValueType.FLOAT4);
        stagingBuffer.putFloat(0, x);
        stagingBuffer.putFloat(4, y);
        stagingBuffer.putFloat(8, z);
        stagingBuffer.putFloat(12, w);
        markMutated();
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

        int sourcePosition = values.position();
        int sourceRemaining = values.remaining();

        if (type == ValueType.MAT3) {
            if (sourceRemaining < 9) {
                throw new IllegalArgumentException("Expected at least 9 floats for MAT3, got " + sourceRemaining);
            }
            for (int column = 0; column < 3; column++) {
                int base = column * 16;
                int srcBase = sourcePosition + column * 3;
                stagingBuffer.putFloat(base, values.get(srcBase));
                stagingBuffer.putFloat(base + 4, values.get(srcBase + 1));
                stagingBuffer.putFloat(base + 8, values.get(srcBase + 2));
                stagingBuffer.putFloat(base + 12, 0.0f);
            }
            markMutated();
            return;
        }

        int expected = elementCount(type);
        if (sourceRemaining < expected) {
            throw new IllegalArgumentException("Expected at least " + expected + " floats, got " + sourceRemaining);
        }
        for (int i = 0; i < expected; i++) {
            stagingBuffer.putFloat(i * 4, values.get(sourcePosition + i));
        }
        markMutated();
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        MemoryUtil.memFree(stagingBuffer);
        disposed = true;
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }

    void copyCurrentValueTo(ByteBuffer dst, int dstOffset) {
        ensureNotDisposed();
        if (dst == null) {
            throw new IllegalArgumentException("dst cannot be null");
        }
        if (dstOffset < 0 || dstOffset + sizeInBytes > dst.capacity()) {
            throw new IllegalArgumentException("dstOffset out of bounds for uniform copy");
        }

        if (dst.isDirect()) {
            long srcAddress = MemoryUtil.memAddress(stagingBuffer);
            long dstAddress = MemoryUtil.memAddress(dst) + dstOffset;
            MemoryUtil.memCopy(srcAddress, dstAddress, sizeInBytes);
            return;
        }

        for (int i = 0; i < sizeInBytes; i++) {
            dst.put(dstOffset + i, stagingBuffer.get(i));
        }
    }

    int getSnapshotOffsetForFrame(int frameId) {
        if (cachedSnapshotFrameId == frameId && cachedSnapshotVersion == mutationVersion) {
            return cachedSnapshotOffset;
        }
        return -1;
    }

    void setSnapshotOffsetForFrame(int frameId, int offset) {
        cachedSnapshotFrameId = frameId;
        cachedSnapshotVersion = mutationVersion;
        cachedSnapshotOffset = offset;
    }

    int getMutationVersion() {
        return mutationVersion;
    }

    private void markMutated() {
        mutationVersion++;
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
