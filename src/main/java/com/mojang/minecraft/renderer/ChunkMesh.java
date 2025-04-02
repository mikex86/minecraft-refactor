package com.mojang.minecraft.renderer;

import com.mojang.minecraft.renderer.graphics.GraphicsAPI;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums;
import com.mojang.minecraft.renderer.graphics.GraphicsFactory;
import com.mojang.minecraft.renderer.graphics.VertexBuffer;

import java.nio.FloatBuffer;
import java.util.Objects;

/**
 * Handles VBO-based rendering for a single chunk section.
 * Each ChunkMesh represents one layer of rendering data for a section of a chunk.
 * This design supports moving toward cubic chunks in the future.
 */
public class ChunkMesh implements Disposable {
    // Graphics resources
    private final GraphicsAPI graphics;
    private VertexBuffer vertexBuffer;
    private int vertexCount;

    // Tesselator for building this mesh
    private final Tesselator tesselator;

    // State tracking
    private boolean dirty = true;
    private boolean disposed = false;

    /**
     * Creates a new chunk renderer.
     */
    public ChunkMesh() {
        // Get graphics API instance
        this.graphics = GraphicsFactory.getGraphicsAPI();

        // Create resources
        this.vertexBuffer = null;
        this.tesselator = Tesselator.instance;
        this.vertexCount = 0;
    }

    /**
     * Gets the tesselator for this mesh.
     */
    public Tesselator getTesselator() {
        return tesselator;
    }

    /**
     * Marks this mesh as dirty, requiring a rebuild.
     */
    public void setDirty() {
        dirty = true;
    }

    /**
     * Returns whether the mesh needs to be rebuilt.
     */
    public boolean isDirty() {
        return dirty;
    }

    /**
     * Rebuilds the mesh with the latest vertex data.
     */
    public void rebuild() {
        if (!dirty) {
            return;
        }

        vertexCount = tesselator.getVertexCount();

        if (vertexCount > 0) {
            vertexBuffer = tesselator.createVertexBuffer(GraphicsEnums.BufferUsage.STATIC);
        }

        dirty = false;
    }

    /**
     * Renders the mesh.
     */
    public void render() {
        if (vertexCount == 0) {
            return;
        }
        Objects.requireNonNull(vertexBuffer, "Vertex buffer is null despite that vertex count is not zero");

        // Draw the mesh
        graphics.drawPrimitives(vertexBuffer, GraphicsEnums.PrimitiveType.QUADS, 0, vertexCount);
    }

    /**
     * Clears the vertex data but keeps the buffer allocated.
     */
    public void clear() {
        tesselator.init();
        vertexCount = 0;
        setDirty();
    }

    /**
     * Disposes of the resources held by this mesh.
     */
    @Override
    public void dispose() {
        if (!disposed) {
            if (vertexBuffer != null)
                vertexBuffer.dispose();
            tesselator.dispose();
            disposed = true;
        }
    }
} 