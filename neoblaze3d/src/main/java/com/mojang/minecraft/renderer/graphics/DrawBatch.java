package com.mojang.minecraft.renderer.graphics;

import com.mojang.minecraft.renderer.graphics.GraphicsEnums.PrimitiveType;

import java.util.Arrays;
import java.util.Objects;

/**
 * Reusable draw-command batch to avoid per-frame object allocation.
 * Caller owns lifecycle and should reuse/clear each frame.
 */
public final class DrawBatch {
    private PrimitiveType[] primitiveTypes;
    private VertexBuffer[] vertexBuffers;
    private IndexBuffer[] indexBuffers;
    private int[] starts;
    private int[] counts;
    private int size;

    public DrawBatch(int initialCapacity) {
        if (initialCapacity <= 0) {
            throw new IllegalArgumentException("initialCapacity must be > 0");
        }
        primitiveTypes = new PrimitiveType[initialCapacity];
        vertexBuffers = new VertexBuffer[initialCapacity];
        indexBuffers = new IndexBuffer[initialCapacity];
        starts = new int[initialCapacity];
        counts = new int[initialCapacity];
    }

    public void clear() {
        // Null object references so disposed resources are not retained by the batch.
        Arrays.fill(primitiveTypes, 0, size, null);
        Arrays.fill(vertexBuffers, 0, size, null);
        Arrays.fill(indexBuffers, 0, size, null);
        size = 0;
    }

    public void add(PrimitiveType primitiveType,
                    VertexBuffer vertexBuffer,
                    IndexBuffer indexBuffer,
                    int start,
                    int count) {
        Objects.requireNonNull(primitiveType, "primitiveType cannot be null");
        Objects.requireNonNull(vertexBuffer, "vertexBuffer cannot be null");
        if (count < 0) {
            throw new IllegalArgumentException("count must be >= 0");
        }

        ensureCapacity(size + 1);
        primitiveTypes[size] = primitiveType;
        vertexBuffers[size] = vertexBuffer;
        indexBuffers[size] = indexBuffer;
        starts[size] = start;
        counts[size] = count;
        size++;
    }

    public int size() {
        return size;
    }

    public PrimitiveType getPrimitiveType(int index) {
        return primitiveTypes[index];
    }

    public VertexBuffer getVertexBuffer(int index) {
        return vertexBuffers[index];
    }

    public IndexBuffer getIndexBuffer(int index) {
        return indexBuffers[index];
    }

    public int getStart(int index) {
        return starts[index];
    }

    public int getCount(int index) {
        return counts[index];
    }

    private void ensureCapacity(int required) {
        if (required <= primitiveTypes.length) {
            return;
        }
        int newCapacity = Math.max(primitiveTypes.length * 2, required);
        primitiveTypes = Arrays.copyOf(primitiveTypes, newCapacity);
        vertexBuffers = Arrays.copyOf(vertexBuffers, newCapacity);
        indexBuffers = Arrays.copyOf(indexBuffers, newCapacity);
        starts = Arrays.copyOf(starts, newCapacity);
        counts = Arrays.copyOf(counts, newCapacity);
    }
}
