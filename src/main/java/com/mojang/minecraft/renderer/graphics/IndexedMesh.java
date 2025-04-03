package com.mojang.minecraft.renderer.graphics;

import com.mojang.minecraft.renderer.Disposable;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums.PrimitiveType;

/**
 * Represents an indexed mesh with a vertex buffer, index buffer, and vertex array object.
 * This class provides a convenient way to manage and render a 3D mesh.
 */
public class IndexedMesh implements Disposable {
    private final VertexBuffer vertexBuffer;
    private final IndexBuffer indexBuffer;
    private final VertexArrayObject vao;
    private final int indexCount;
    
    /**
     * Creates a new indexed mesh with legacy VBO/IBO approach.
     * 
     * @param vertexBuffer The vertex buffer
     * @param indexBuffer The index buffer
     * @param indexCount The number of indices
     * @deprecated Use {@link #IndexedMesh(GraphicsAPI, VertexBuffer, IndexBuffer, int)} instead
     */
    @Deprecated
    public IndexedMesh(VertexBuffer vertexBuffer, IndexBuffer indexBuffer, int indexCount) {
        this.vertexBuffer = vertexBuffer;
        this.indexBuffer = indexBuffer;
        this.vao = null;
        this.indexCount = indexCount;
    }
    
    /**
     * Creates a new indexed mesh with a VAO.
     * 
     * @param graphics The graphics API
     * @param vertexBuffer The vertex buffer
     * @param indexBuffer The index buffer
     * @param indexCount The number of indices
     */
    public IndexedMesh(GraphicsAPI graphics, VertexBuffer vertexBuffer, IndexBuffer indexBuffer, int indexCount) {
        this.vertexBuffer = vertexBuffer;
        this.indexBuffer = indexBuffer;
        this.indexCount = indexCount;
        
        // Create and set up VAO
        this.vao = graphics.createVertexArrayObject();
        this.vao.setVertexBuffer(vertexBuffer);
        this.vao.setIndexBuffer(indexBuffer);
    }
    
    /**
     * Draws this mesh.
     * 
     * @param graphics The graphics API
     */
    public void draw(GraphicsAPI graphics) {
        if (vao != null) {
            // Use the VAO-based rendering if available
            graphics.drawPrimitives(vao, PrimitiveType.TRIANGLES, 0, indexCount);
        } else {
            // Fall back to the legacy approach
            graphics.drawIndexedPrimitives(vertexBuffer, indexBuffer, PrimitiveType.TRIANGLES, 0, indexCount);
        }
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
     * Gets the vertex array object.
     * 
     * @return The vertex array object, or null if using legacy approach
     */
    public VertexArrayObject getVertexArrayObject() {
        return vao;
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
        
        if (vao != null) {
            vao.dispose();
        }
    }
} 