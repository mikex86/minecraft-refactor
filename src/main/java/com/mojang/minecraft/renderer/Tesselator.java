package com.mojang.minecraft.renderer;

import com.mojang.minecraft.renderer.graphics.GraphicsAPI;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums.BufferUsage;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums.PrimitiveType;
import com.mojang.minecraft.renderer.graphics.GraphicsFactory;
import com.mojang.minecraft.renderer.graphics.VertexBuffer;
import com.mojang.minecraft.renderer.graphics.IndexBuffer;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * Tesselator implementation that uses the GraphicsAPI.
 * This provides the same functionality as the original Tesselator, but
 * uses the abstracted graphics API instead of direct OpenGL calls.
 * 
 * This version uses indexed triangles instead of direct quads for modern GPU compatibility.
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
    private VertexBuffer.VertexFormat format;

    public static Tesselator instance = new Tesselator();

    /**
     * Creates a new tesselator
     */
    public Tesselator() {
        this.graphics = GraphicsFactory.getGraphicsAPI();
        this.vertexBuffer = graphics.createVertexBuffer(BufferUsage.DYNAMIC);
        this.indexBuffer = graphics.createIndexBuffer(BufferUsage.DYNAMIC);
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

            // Draw the vertices
            graphics.drawIndexedPrimitives(vertexBuffer, indexBuffer, PrimitiveType.TRIANGLES, 0, indexCount);
        }

        // Reset state
        clear();
    }
    
    /**
     * Creates an indexed mesh from the current tesselator state
     *
     * @param bufferUsage The buffer usage hint
     * @return The created vertex buffer
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
        
        return new IndexedMesh(vertexBuffer, indexBuffer, indexCount);
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
        if (!this.disableColors) {
            if (!this.hasColor) {
                this.vertexSize += 3; // Add space for color components
            }

            this.hasColor = true;
            this.colorR = r;
            this.colorG = g;
            this.colorB = b;
        }
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
     * Add a vertex with current texture coordinates and color
     * When every 4 vertices are added, they are converted into two triangles.
     *
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     */
    public void vertex(float x, float y, float z) {
        // Add texture coordinates if set
        if (this.hasTexture) {
            this.cpuVertexBuffer.put(this.dataIndex++, this.textureU);
            this.cpuVertexBuffer.put(this.dataIndex++, this.textureV);
        }

        // Add color if set
        if (this.hasColor) {
            this.cpuVertexBuffer.put(this.dataIndex++, this.colorR);
            this.cpuVertexBuffer.put(this.dataIndex++, this.colorG);
            this.cpuVertexBuffer.put(this.dataIndex++, this.colorB);
        }

        // Add vertex position
        this.cpuVertexBuffer.put(this.dataIndex++, x);
        this.cpuVertexBuffer.put(this.dataIndex++, y);
        this.cpuVertexBuffer.put(this.dataIndex++, z);

        // Increment vertex count
        this.vertexCount++;
        
        // Add indices for triangles every 4 vertices (convert quad to 2 triangles)
        if (this.vertexCount % 4 == 0) {
            int baseVertex = this.vertexCount - 4;
            
            // First triangle (vertices 0, 1, 2)
            this.cpuIndexBuffer.put(this.indexCount++, baseVertex);
            this.cpuIndexBuffer.put(this.indexCount++, baseVertex + 1);
            this.cpuIndexBuffer.put(this.indexCount++, baseVertex + 2);
            
            // Second triangle (vertices 0, 2, 3)
            this.cpuIndexBuffer.put(this.indexCount++, baseVertex);
            this.cpuIndexBuffer.put(this.indexCount++, baseVertex + 2);
            this.cpuIndexBuffer.put(this.indexCount++, baseVertex + 3);
            
            // Flush if buffer is almost full
            if (this.dataIndex >= MAX_FLOATS - this.vertexSize * 4 || 
                this.indexCount >= MAX_INDICES - 6) {
                this.flush();
            }
        }
    }

    /**
     * Set color using a packed RGB integer
     *
     * @param c Packed RGB color (0xRRGGBB)
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
    }
    
    /**
     * Represents an indexed mesh with a vertex buffer and an index buffer.
     */
    public static class IndexedMesh implements Disposable {
        private final VertexBuffer vertexBuffer;
        private final IndexBuffer indexBuffer;
        private final int indexCount;
        
        /**
         * Creates a new indexed mesh.
         *
         * @param vertexBuffer The vertex buffer
         * @param indexBuffer The index buffer
         * @param indexCount The number of indices
         */
        public IndexedMesh(VertexBuffer vertexBuffer, IndexBuffer indexBuffer, int indexCount) {
            this.vertexBuffer = vertexBuffer;
            this.indexBuffer = indexBuffer;
            this.indexCount = indexCount;
        }
        
        /**
         * Draws this mesh.
         *
         * @param graphics The graphics API
         */
        public void draw(GraphicsAPI graphics) {
            graphics.drawIndexedPrimitives(vertexBuffer, indexBuffer, PrimitiveType.TRIANGLES, 0, indexCount);
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
         * Gets the number of indices.
         *
         * @return The number of indices
         */
        public int getIndexCount() {
            return indexCount;
        }
        
        /**
         * Disposes of the resources held by this mesh.
         */
        @Override
        public void dispose() {
            if (vertexBuffer != null) {
                vertexBuffer.dispose();
            }
            
            if (indexBuffer != null) {
                indexBuffer.dispose();
            }
        }
    }
}