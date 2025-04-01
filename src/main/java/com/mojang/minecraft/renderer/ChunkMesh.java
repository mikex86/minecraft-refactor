package com.mojang.minecraft.renderer;

import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Handles VBO-based rendering for a single chunk section.
 * Each ChunkMesh represents one layer of rendering data for a section of a chunk.
 * This design supports moving toward cubic chunks in the future.
 */
public class ChunkMesh {
    // VBO data
    private int vboId;
    private int vertexCount;
    
    // Tesselator for this mesh
    private final Tesselator tesselator;
    
    // State tracking
    private boolean dirty = true;
    private boolean disposed = false;
    
    /**
     * Creates a new chunk section mesh.
     */
    public ChunkMesh() {
        // Create VBO and tesselator
        vboId = glGenBuffers();
        tesselator = new Tesselator();
        vertexCount = 0;
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
        
        FloatBuffer buffer = tesselator.getBuffer();
        vertexCount = tesselator.getVertexCount();
        
        if (vertexCount > 0) {
            // Bind VBO and upload data
            glBindBuffer(GL_ARRAY_BUFFER, vboId);
            glBufferData(GL_ARRAY_BUFFER, buffer, GL_STATIC_DRAW);
            glBindBuffer(GL_ARRAY_BUFFER, 0);
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
        
        // Bind the VBO
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        
        // Set up vertex arrays based on data format
        int stride = tesselator.getVertexSize() * 4; // in bytes
        int offset = 0;
        
        // Enable vertex arrays
        glEnableClientState(GL_VERTEX_ARRAY);
        
        // Set up texture coordinates if present
        if (tesselator.hasTexture()) {
            glEnableClientState(GL_TEXTURE_COORD_ARRAY);
            glTexCoordPointer(2, GL_FLOAT, stride, offset);
            offset += 2 * 4; // 2 floats * 4 bytes
        }
        
        // Set up colors if present
        if (tesselator.hasColor()) {
            glEnableClientState(GL_COLOR_ARRAY);
            glColorPointer(3, GL_FLOAT, stride, offset);
            offset += 3 * 4; // 3 floats * 4 bytes
        }
        
        // Set up vertices
        glVertexPointer(3, GL_FLOAT, stride, offset);
        
        // Draw the mesh
        glDrawArrays(GL_QUADS, 0, vertexCount);
        
        // Disable vertex arrays
        glDisableClientState(GL_VERTEX_ARRAY);
        if (tesselator.hasTexture()) {
            glDisableClientState(GL_TEXTURE_COORD_ARRAY);
        }
        if (tesselator.hasColor()) {
            glDisableClientState(GL_COLOR_ARRAY);
        }
        
        // Unbind VBO
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }
    
    /**
     * Clears the vertex data but keeps the VBO allocated.
     */
    public void clear() {
        tesselator.init();
        vertexCount = 0;
        setDirty();
    }
    
    /**
     * Disposes of this mesh's resources.
     */
    public void dispose() {
        if (!disposed) {
            if (vboId != 0) {
                glDeleteBuffers(vboId);
                vboId = 0;
            }
            disposed = true;
        }
    }
} 