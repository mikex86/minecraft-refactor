package com.mojang.minecraft.renderer;

import com.mojang.minecraft.renderer.graphics.GraphicsAPI;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums;
import com.mojang.minecraft.renderer.graphics.GraphicsFactory;
import com.mojang.minecraft.renderer.graphics.VertexBuffer;
import com.mojang.minecraft.renderer.graphics.IndexBuffer;
import com.mojang.minecraft.renderer.Tesselator.IndexedMesh;

import java.nio.FloatBuffer;
import java.util.Objects;

/**
 * Handles VBO-based rendering for a chunk mesh.
 */
public class ChunkMesh implements Disposable {
    // Graphics resources
    private final GraphicsAPI graphics;
    private IndexedMesh mesh;

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
        this.mesh = null;
        this.tesselator = Tesselator.instance;
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

        // Clean up existing mesh if needed
        if (mesh != null) {
            mesh.dispose();
            mesh = null;
        }

        int vertexCount = tesselator.getVertexCount();
        int indexCount = tesselator.getIndexCount();

        if (vertexCount > 0 && indexCount > 0) {
            mesh = tesselator.createIndexedMesh(GraphicsEnums.BufferUsage.STATIC);
        }

        dirty = false;
    }

    /**
     * Renders the mesh.
     */
    public void render() {
        if (mesh == null) {
            return;
        }

        // Draw the mesh
        mesh.draw(graphics);
    }

    /**
     * Clears the vertex data but keeps the buffer allocated.
     */
    public void clear() {
        tesselator.init();
        setDirty();
    }

    /**
     * Disposes of the resources held by this mesh.
     */
    @Override
    public void dispose() {
        if (!disposed) {
            if (mesh != null) {
                mesh.dispose();
                mesh = null;
            }
            disposed = true;
        }
    }
} 