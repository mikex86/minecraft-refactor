package com.mojang.minecraft.renderer.graphics;

import com.mojang.minecraft.renderer.Disposable;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums.PrimitiveType;

/**
 * Represents an indexed mesh with explicit vertex and index buffers.
 * This class provides a convenient way to manage and render a 3D mesh.
 */
public class IndexedMesh implements Disposable {
    private final VertexBuffer vertexBuffer;
    private final IndexBuffer indexBuffer;

    private final int vertexCount;
    private final int indexCount;


    /**
     * Creates a new indexed mesh.
     *
     * @param vertexBuffer The vertex buffer
     * @param indexBuffer  The index buffer (nullable)
     * @param vertexCount  The number of vertices
     * @param indexCount   The number of indices
     */
    public IndexedMesh(VertexBuffer vertexBuffer, IndexBuffer indexBuffer, int vertexCount, int indexCount) {
        this.vertexBuffer = vertexBuffer;
        this.indexBuffer = indexBuffer;
        this.vertexCount = vertexCount;
        this.indexCount = indexCount;
    }

    /**
     * Creates a new indexed mesh.
     *
     * @param commandBuffer     The commandBuffer API
     * @param vertexBuffer The vertex buffer
     * @param indexBuffer  The index buffer (nullable)
     * @param vertexCount  The number of vertices
     * @param indexCount   The number of indices
     * @deprecated Use {@link #IndexedMesh(VertexBuffer, IndexBuffer, int, int)} instead
     */
    @Deprecated
    public IndexedMesh(CommandBuffer commandBuffer, VertexBuffer vertexBuffer, IndexBuffer indexBuffer, int vertexCount, int indexCount) {
        this(vertexBuffer, indexBuffer, vertexCount, indexCount);
    }

    /**
     * Draws this mesh.
     *
     * @param commandBuffer      The commandBuffer API
     * @param primitiveType The primitive type to draw (e.g., triangles, lines)
     */
    public void draw(CommandBuffer commandBuffer, PrimitiveType primitiveType) {
        int elementCount = indexBuffer != null ? indexCount : vertexCount;
        commandBuffer.draw(primitiveType, vertexBuffer, indexBuffer, 0, elementCount);
    }

    public void draw(CommandBuffer commandBuffer) {
        draw(commandBuffer, PrimitiveType.TRIANGLES);
    }

    /**
     * Gets the vertex buffer.
     *
     * @return The vertex buffer
     */
    public VertexBuffer getVertexBuffer() {
        return vertexBuffer;
    }

    /**
     * Gets the index buffer.
     *
     * @return The index buffer
     */
    public IndexBuffer getIndexBuffer() {
        return indexBuffer;
    }

    /**
     * Gets the index count.
     *
     * @return The index count
     */
    public int getIndexCount() {
        return indexCount;
    }

    @Override
    public void dispose() {
        if (vertexBuffer != null) {
            vertexBuffer.dispose();
        }

        if (indexBuffer != null) {
            indexBuffer.dispose();
        }

    }
}
