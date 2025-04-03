package com.mojang.minecraft.renderer;

import com.mojang.minecraft.renderer.graphics.*;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums.BufferUsage;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums.PrimitiveType;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * Tesselator implementation that uses the GraphicsAPI.
 * This provides the same functionality as the original Tesselator, but
 * uses the abstracted graphics API instead of direct OpenGL calls.
 * 
 * This version uses indexed triangles instead of direct quads for modern GPU compatibility.
 * It also uses Vertex Array Objects (VAOs) for improved rendering performance.
 */
public class Tesselator implements Disposable {
    private static final int MAX_FLOATS = 262144;
    private static final int MAX_INDICES = 262144;

    // CPU-side data storage
    private final FloatBuffer cpuVertexBuffer = BufferUtils.createFloatBuffer(MAX_FLOATS);
    private final IntBuffer cpuIndexBuffer = BufferUtils.createIntBuffer(MAX_INDICES);

    // State tracking
    private int vertexCount = 0;
    private int indexCount = 0;
    private int dataIndex = 0;
    private int vertexSize = 3; // xyz at minimum

    // Current vertex attributes
    private float textureU;
    private float textureV;
    private float colorR;
    private float colorG;
    private float colorB;

    // Feature flags
    private boolean hasColor = false;
    private boolean hasTexture = false;
    private boolean disableColors = false;

    // Graphics API and resources
    private final GraphicsAPI graphics;
    private VertexBuffer vertexBuffer;
    private IndexBuffer indexBuffer;
    private VertexArrayObject vao;
    private VertexBuffer.VertexFormat format;

    public static Tesselator instance = new Tesselator();

    /**
     * Creates a new tesselator
     */
    public Tesselator() {
        this.graphics = GraphicsFactory.getGraphicsAPI();
        this.vertexBuffer = graphics.createVertexBuffer(BufferUsage.DYNAMIC);
        this.indexBuffer = graphics.createIndexBuffer(BufferUsage.DYNAMIC);
        this.vao = graphics.createVertexArrayObject();
        clear();
    }

    /**
     * Gets the vertex buffer with accumulated vertex data
     */
    public FloatBuffer getBuffer() {
        return cpuVertexBuffer;
    }
    
    /**
     * Gets the index buffer with accumulated index data
     */
    public IntBuffer getIndexBuffer() {
        return cpuIndexBuffer;
    }

    /**
     * Gets the current vertex count
     */
    public int getVertexCount() {
        return vertexCount;
    }
    
    /**
     * Gets the current index count
     */
    public int getIndexCount() {
        return indexCount;
    }

    /**
     * Gets the vertex size in floats
     */
    public int getVertexSize() {
        return vertexSize;
    }

    /**
     * Returns whether this tesselator has texture coordinates
     */
    public boolean hasTexture() {
        return hasTexture;
    }

    /**
     * Returns whether this tesselator has color data
     */
    public boolean hasColor() {
        return hasColor;
    }

    /**
     * Sends all accumulated vertices to the GPU and renders them directly
     */
    public void flush() {
        if (this.vertexCount > 0) {
            // Update format
            format = new VertexBuffer.VertexFormat(
                    true,                 // Always has positions
                    this.hasColor,        // May have colors
                    this.hasTexture,      // May have textures
                    false                 // No normals
            );

            vertexBuffer.setFormat(format);

            // Upload data to GPU
            vertexBuffer.setData(cpuVertexBuffer, dataIndex * 4); // 4 bytes per float
            indexBuffer.setData(cpuIndexBuffer, indexCount * 4); // 4 bytes per int

            // Set up VAO
            vao.setVertexBuffer(vertexBuffer);
            vao.setIndexBuffer(indexBuffer);

            // Draw the vertices
            graphics.drawPrimitives(vao, PrimitiveType.TRIANGLES, 0, indexCount);
        }

        // Reset state
        clear();
    }
    
    /**
     * Creates an indexed mesh from the current tesselator state
     *
     * @param bufferUsage The buffer usage hint
     * @return The created indexed mesh
     */
    public IndexedMesh createIndexedMesh(BufferUsage bufferUsage) {
        // Set up vertex format based on tesselator state
        VertexBuffer.VertexFormat format = new VertexBuffer.VertexFormat(
                true,                 // Always has positions
                hasColor(),           // May have colors
                hasTexture(),         // May have texture coords
                false                 // No normals
        );

        // Create buffers
        VertexBuffer vertexBuffer = graphics.createVertexBuffer(bufferUsage);
        IndexBuffer indexBuffer = graphics.createIndexBuffer(bufferUsage);
        
        vertexBuffer.setFormat(format);

        // Upload data
        vertexBuffer.setData(cpuVertexBuffer, dataIndex * 4); // 4 bytes per float
        indexBuffer.setData(cpuIndexBuffer, indexCount * 4); // 4 bytes per int
        
        // Create mesh with VAO
        return new IndexedMesh(graphics, vertexBuffer, indexBuffer, indexCount);
    }

    /**
     * Resets the tesselator state
     */
    private void clear() {
        this.vertexCount = 0;
        this.indexCount = 0;
        this.cpuVertexBuffer.clear();
        this.cpuIndexBuffer.clear();
        this.dataIndex = 0;
    }

    /**
     * Initialize the tesselator for a new drawing sequence
     */
    public void init() {
        this.clear();
        this.hasColor = false;
        this.hasTexture = false;
        this.disableColors = false;
        this.vertexSize = 3; // Start with just xyz
    }

    /**
     * Set texture coordinates for the next vertex
     *
     * @param u U texture coordinate
     * @param v V texture coordinate
     */
    public void tex(float u, float v) {
        if (!this.hasTexture) {
            this.vertexSize += 2; // Add space for texture coordinates
        }

        this.hasTexture = true;
        this.textureU = u;
        this.textureV = v;
    }

    /**
     * Set color for the next vertex
     *
     * @param r Red component (0.0-1.0)
     * @param g Green component (0.0-1.0)
     * @param b Blue component (0.0-1.0)
     */
    public void color(float r, float g, float b) {
        if (this.disableColors) {
            return;
        }

        if (!this.hasColor) {
            this.vertexSize += 3; // Add space for color
        }

        this.hasColor = true;
        this.colorR = r;
        this.colorG = g;
        this.colorB = b;
    }

    /**
     * Add a vertex with texture coordinates
     *
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @param u U texture coordinate
     * @param v V texture coordinate
     */
    public void vertexUV(float x, float y, float z, float u, float v) {
        this.tex(u, v);
        this.vertex(x, y, z);
    }

    /**
     * Add a vertex
     *
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     */
    public void vertex(float x, float y, float z) {
        // Store vertex attributes in order: texcoords, color, position
        int currentIndex = this.dataIndex;

        // Add texture coordinates if enabled
        if (this.hasTexture) {
            this.cpuVertexBuffer.put(currentIndex++, this.textureU);
            this.cpuVertexBuffer.put(currentIndex++, this.textureV);
        }

        // Add color if enabled
        if (this.hasColor) {
            this.cpuVertexBuffer.put(currentIndex++, this.colorR);
            this.cpuVertexBuffer.put(currentIndex++, this.colorG);
            this.cpuVertexBuffer.put(currentIndex++, this.colorB);
        }

        // Add position (always present)
        this.cpuVertexBuffer.put(currentIndex++, x);
        this.cpuVertexBuffer.put(currentIndex++, y);
        this.cpuVertexBuffer.put(currentIndex++, z);

        // Update data index
        this.dataIndex = currentIndex;

        // Update vertex count
        this.vertexCount++;

        // Add indices for triangles
        if (this.vertexCount % 4 == 0) {
            // For each quad, generate two triangles
            int baseIndex = this.vertexCount - 4;
            
            // First triangle (0, 1, 2)
            this.cpuIndexBuffer.put(this.indexCount++, baseIndex);
            this.cpuIndexBuffer.put(this.indexCount++, baseIndex + 1);
            this.cpuIndexBuffer.put(this.indexCount++, baseIndex + 2);
            
            // Second triangle (0, 2, 3)
            this.cpuIndexBuffer.put(this.indexCount++, baseIndex);
            this.cpuIndexBuffer.put(this.indexCount++, baseIndex + 2);
            this.cpuIndexBuffer.put(this.indexCount++, baseIndex + 3);
        }
    }

    /**
     * Set color for the next vertex using a packed RGB value
     *
     * @param c RGB color value
     */
    public void color(int c) {
        float r = (float) (c >> 16 & 255) / 255.0F;
        float g = (float) (c >> 8 & 255) / 255.0F;
        float b = (float) (c & 255) / 255.0F;
        this.color(r, g, b);
    }

    /**
     * Disposes of any GPU resources held by this tesselator.
     */
    @Override
    public void dispose() {
        if (vertexBuffer != null) {
            vertexBuffer.dispose();
            vertexBuffer = null;
        }
        
        if (indexBuffer != null) {
            indexBuffer.dispose();
            indexBuffer = null;
        }
        
        if (vao != null) {
            vao.dispose();
            vao = null;
        }
    }
}