package com.mojang.minecraft.renderer;

import com.mojang.minecraft.renderer.graphics.CommandBuffer;
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
     * Uploads the mesh with the latest vertex data to the GPU.
     */
    public void upload(Tesselator tesselator) {
        // Clean up existing mesh if needed
        if (mesh != null) {
            mesh.dispose();
            mesh = null;
        }

        int vertexCount = tesselator.getVertexCount();
        int indexCount = tesselator.getIndexCount();

        if (vertexCount > 0 && indexCount > 0) {
            mesh = tesselator.createIndexedMesh(GraphicsEnums.BufferUsage.STATIC, true);
        }
    }

    /**
     * Draws this chunk mesh
     *
     * @param commandBuffer the commandBuffer API
     */
    public int draw(CommandBuffer commandBuffer) {
        if (mesh != null) {
            mesh.draw(commandBuffer);
            return 1;
        }
        return 0;
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