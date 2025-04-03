package com.mojang.minecraft.renderer.model;

import com.mojang.minecraft.renderer.Disposable;
import com.mojang.minecraft.renderer.Tesselator;
import com.mojang.minecraft.renderer.graphics.GraphicsAPI;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums.BufferUsage;
import com.mojang.minecraft.renderer.graphics.GraphicsFactory;
import com.mojang.minecraft.renderer.graphics.IndexedMesh;

/**
 * Handles mesh building and rendering for character models.
 * Uses Tesselator for mesh building and IndexedMesh for rendering.
 */
public class ModelMesh implements Disposable {
    // Graphics resources
    private IndexedMesh mesh;
    
    // Tesselator for building this mesh
    private final Tesselator tesselator;
    private final GraphicsAPI graphics;
    
    // State tracking
    private boolean dirty = true;
    private boolean disposed = false;
    
    /**
     * Creates a new model mesh.
     */
    public ModelMesh() {
        this.graphics = GraphicsFactory.getGraphicsAPI();
        this.tesselator = new Tesselator(); // Using a dedicated tesselator to avoid conflicts
        this.mesh = null;
    }
    
    /**
     * Starts a new definition of this mesh.
     */
    public void begin() {
        this.tesselator.init();
        this.dirty = true;
    }
    
    /**
     * Adds a vertex with texture coordinates to the mesh.
     */
    public void addVertex(float x, float y, float z, float u, float v) {
        tesselator.tex(u, v);
        tesselator.color(1.0f, 1.0f, 1.0f);
        tesselator.vertex(x, y, z);
    }
    
    /**
     * Ends the definition of this mesh and uploads it to the GPU.
     */
    public void end() {
        if (!this.dirty) {
            return;
        }
        
        // Clean up existing mesh if needed
        if (mesh != null) {
            mesh.dispose();
            mesh = null;
        }
        
        // Check if we have vertices to build
        int vertexCount = tesselator.getVertexCount();
        int indexCount = tesselator.getIndexCount();
        
        if (vertexCount > 0 && indexCount > 0) {
            // Create a new mesh with the tesselator data
            mesh = tesselator.createIndexedMesh(BufferUsage.STATIC);
        }
        
        this.dirty = false;
    }
    
    /**
     * Renders the mesh.
     */
    public void render() {
        if (this.mesh != null) {
            this.mesh.draw(graphics);
        }
    }
    
    /**
     * Disposes of this mesh's resources.
     */
    @Override
    public void dispose() {
        if (!this.disposed) {
            if (this.mesh != null) {
                this.mesh.dispose();
                this.mesh = null;
            }
            this.disposed = true;
        }
    }
} 