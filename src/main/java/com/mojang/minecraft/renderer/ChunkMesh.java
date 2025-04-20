package com.mojang.minecraft.renderer;

import com.mojang.minecraft.renderer.graphics.GraphicsAPI;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums;
import com.mojang.minecraft.renderer.graphics.IndexedMesh;

/**
 * Handles VBO-based rendering for a chunk mesh.
 */
public class ChunkMesh implements Disposable {
    // Graphics resources
    private IndexedMesh mesh;

    // State tracking
    private boolean disposed = false;

    /**
     * Creates a new chunk renderer.
     */
    public ChunkMesh() {
        // Create resources
        this.mesh = null;
    }

    /**
     * Rebuilds the mesh with the latest vertex data.
     */
    public void rebuild() {
        // Clean up existing mesh if needed
        if (mesh != null) {
            mesh.dispose();
            mesh = null;
        }

        Tesselator tesselator = Tesselator.instance;

        int vertexCount = tesselator.getVertexCount();
        int indexCount = tesselator.getIndexCount();

        if (vertexCount > 0 && indexCount > 0) {
            mesh = tesselator.createIndexedMesh(GraphicsEnums.BufferUsage.STATIC);
        }
    }

    /**
     * Draws this chunk mesh
     *
     * @param graphics the graphics API
     */
    public int draw(GraphicsAPI graphics) {
        if (mesh != null) {
            mesh.draw(graphics);
            return 1;
        }
        return 0;
    }

    /**
     * Clears the vertex data but keeps the buffer allocated.
     */
    public void clear() {
        Tesselator.instance.init();
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