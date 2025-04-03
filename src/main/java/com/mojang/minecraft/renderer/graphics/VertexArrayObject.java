package com.mojang.minecraft.renderer.graphics;

/**
 * Interface for a vertex array object resource.
 * Represents a VAO that contains the state for vertex and index buffers and their attribute bindings.
 * This abstraction helps in transitioning to modern OpenGL core profile.
 */
public interface VertexArrayObject extends GraphicsResource {
    /**
     * Binds the vertex buffer and sets up its attributes in this VAO.
     * This will define the vertex format for this VAO.
     * 
     * @param vertexBuffer The vertex buffer to bind
     */
    void setVertexBuffer(VertexBuffer vertexBuffer);
    
    /**
     * Binds the index buffer to this VAO.
     * 
     * @param indexBuffer The index buffer to bind
     */
    void setIndexBuffer(IndexBuffer indexBuffer);
    
    /**
     * Gets the currently bound vertex buffer.
     * 
     * @return The vertex buffer
     */
    VertexBuffer getVertexBuffer();
    
    /**
     * Gets the currently bound index buffer.
     * 
     * @return The index buffer
     */
    IndexBuffer getIndexBuffer();
    
    /**
     * Gets the vertex count of the bound vertex buffer.
     * 
     * @return The vertex count
     */
    int getVertexCount();
    
    /**
     * Gets the index count of the bound index buffer.
     * 
     * @return The index count
     */
    int getIndexCount();
} 