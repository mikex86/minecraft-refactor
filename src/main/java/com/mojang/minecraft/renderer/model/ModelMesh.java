package com.mojang.minecraft.renderer.model;

import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL15.*;

/**
 * Handles VBO-based rendering for character models.
 * Replaces OpenGL display lists with modern VBO rendering.
 */
public class ModelMesh {
    // VBO data
    private int vboId;
    private int indexVboId;
    private int vertexCount;
    
    // Buffer for vertex data
    private FloatBuffer vertexBuffer;
    
    // State tracking
    private boolean dirty = true;
    private boolean disposed = false;
    
    // Vertex data layout
    private static final int VERTEX_SIZE = 5; // 3 position + 2 texture coordinates
    private static final int MAX_VERTICES = 10000; // Should be enough for character models
    
    /**
     * Creates a new model mesh.
     */
    public ModelMesh() {
        this.vboId = glGenBuffers();
        this.indexVboId = glGenBuffers();
        this.vertexBuffer = BufferUtils.createFloatBuffer(MAX_VERTICES * VERTEX_SIZE);
        this.vertexCount = 0;
    }
    
    /**
     * Starts a new definition of this mesh.
     */
    public void begin() {
        this.vertexBuffer.clear();
        this.vertexCount = 0;
        this.dirty = true;
    }
    
    /**
     * Adds a vertex with texture coordinates to the mesh.
     */
    public void addVertex(float x, float y, float z, float u, float v) {
        this.vertexBuffer.put(x);
        this.vertexBuffer.put(y);
        this.vertexBuffer.put(z);
        this.vertexBuffer.put(u);
        this.vertexBuffer.put(v);
        this.vertexCount++;
    }
    
    /**
     * Ends the definition of this mesh and uploads it to the GPU.
     */
    public void end() {
        if (!this.dirty) {
            return;
        }
        
        // Prepare the buffer for reading
        this.vertexBuffer.flip();
        
        if (this.vertexCount > 0) {
            // Upload vertex data to the VBO
            glBindBuffer(GL_ARRAY_BUFFER, this.vboId);
            glBufferData(GL_ARRAY_BUFFER, this.vertexBuffer, GL_STATIC_DRAW);
            glBindBuffer(GL_ARRAY_BUFFER, 0);
        }
        
        this.dirty = false;
    }
    
    /**
     * Renders the mesh.
     */
    public void render() {
        if (this.vertexCount == 0) {
            return;
        }
        
        // Bind the VBO
        glBindBuffer(GL_ARRAY_BUFFER, this.vboId);
        
        // Enable vertex arrays
        glEnableClientState(GL_VERTEX_ARRAY);
        glEnableClientState(GL_TEXTURE_COORD_ARRAY);
        
        // Set up vertex arrays
        int stride = VERTEX_SIZE * 4; // in bytes
        
        // Position (3 floats)
        glVertexPointer(3, GL_FLOAT, stride, 0);
        
        // Texture coordinates (2 floats)
        glTexCoordPointer(2, GL_FLOAT, stride, 3 * 4); // Offset by 3 floats
        
        // Draw the mesh as quads
        glDrawArrays(GL_QUADS, 0, this.vertexCount);
        
        // Disable vertex arrays
        glDisableClientState(GL_VERTEX_ARRAY);
        glDisableClientState(GL_TEXTURE_COORD_ARRAY);
        
        // Unbind VBO
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }
    
    /**
     * Disposes of this mesh's resources.
     */
    public void dispose() {
        if (!this.disposed) {
            if (this.vboId != 0) {
                glDeleteBuffers(this.vboId);
                this.vboId = 0;
            }
            if (this.indexVboId != 0) {
                glDeleteBuffers(this.indexVboId);
                this.indexVboId = 0;
            }
            this.disposed = true;
        }
    }
} 