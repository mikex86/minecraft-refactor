package com.mojang.minecraft.renderer;

import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Handles VBO-based rendering for a chunk section.
 * Each chunk mesh contains vertex data for each render layer.
 */
public class ChunkMesh {
    // Constants
    private static final int LAYER_COUNT = 2;
    
    // VBO data for each layer
    private final int[] vboIds = new int[LAYER_COUNT];
    private final int[] vertexCounts = new int[LAYER_COUNT];
    
    // Tesselators for each layer - one per mesh
    private final Tesselator[] tesselators = new Tesselator[LAYER_COUNT];
    
    // State tracking
    private boolean[] dirty = new boolean[LAYER_COUNT];
    private boolean disposed = false;
    
    /**
     * Creates a new chunk mesh.
     */
    public ChunkMesh() {
        // Create VBOs
        for (int i = 0; i < LAYER_COUNT; i++) {
            vboIds[i] = glGenBuffers();
            tesselators[i] = new Tesselator();
            dirty[i] = true;
            vertexCounts[i] = 0;
        }
    }
    
    /**
     * Gets the tesselator for the specified layer.
     */
    public Tesselator getTesselator(int layer) {
        return tesselators[layer];
    }
    
    /**
     * Marks this mesh as dirty, requiring a rebuild.
     */
    public void setDirty() {
        for (int i = 0; i < LAYER_COUNT; i++) {
            dirty[i] = true;
        }
    }
    
    /**
     * Returns whether the mesh needs to be rebuilt.
     */
    public boolean isDirty(int layer) {
        return dirty[layer];
    }
    
    /**
     * Rebuilds the mesh for the specified layer.
     */
    public void rebuild(int layer) {
        if (!dirty[layer]) {
            return;
        }
        
        Tesselator tesselator = tesselators[layer];
        FloatBuffer buffer = tesselator.getBuffer();
        vertexCounts[layer] = tesselator.getVertexCount();
        
        if (vertexCounts[layer] > 0) {
            // Bind VBO and upload data
            glBindBuffer(GL_ARRAY_BUFFER, vboIds[layer]);
            glBufferData(GL_ARRAY_BUFFER, buffer, GL_STATIC_DRAW);
            glBindBuffer(GL_ARRAY_BUFFER, 0);
        }
        
        dirty[layer] = false;
    }
    
    /**
     * Renders the mesh for the specified layer.
     */
    public void render(int layer) {
        if (vertexCounts[layer] == 0) {
            return;
        }
        
        Tesselator tesselator = tesselators[layer];
        
        // Bind the VBO
        glBindBuffer(GL_ARRAY_BUFFER, vboIds[layer]);
        
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
        glDrawArrays(GL_QUADS, 0, vertexCounts[layer]);
        
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
     * Disposes of this mesh's resources.
     */
    public void dispose() {
        if (!disposed) {
            for (int i = 0; i < LAYER_COUNT; i++) {
                if (vboIds[i] != 0) {
                    glDeleteBuffers(vboIds[i]);
                    vboIds[i] = 0;
                }
            }
            disposed = true;
        }
    }
} 