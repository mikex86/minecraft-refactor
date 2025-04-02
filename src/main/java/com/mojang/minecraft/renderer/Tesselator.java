package com.mojang.minecraft.renderer;

import com.mojang.minecraft.renderer.graphics.GraphicsAPI;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums.BufferUsage;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums.PrimitiveType;
import com.mojang.minecraft.renderer.graphics.GraphicsFactory;
import com.mojang.minecraft.renderer.graphics.VertexBuffer;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

/**
 * Tesselator implementation that uses the GraphicsAPI.
 * This provides the same functionality as the original Tesselator, but
 * uses the abstracted graphics API instead of direct OpenGL calls.
 */
public class Tesselator implements Disposable {
    private static final int MAX_FLOATS = 262144;

    // CPU-side data storage
    private final FloatBuffer cpuVertexBuffer = BufferUtils.createFloatBuffer(MAX_FLOATS);

    // State tracking
    private int vertexCount = 0;
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
    private VertexBuffer buffer;
    private VertexBuffer.VertexFormat format;

    public static Tesselator instance = new Tesselator();

    /**
     * Creates a new tesselator
     */
    public Tesselator() {
        this.graphics = GraphicsFactory.getGraphicsAPI();
        this.buffer = graphics.createVertexBuffer(BufferUsage.DYNAMIC);
        clear();
    }

    /**
     * Gets the vertex buffer with accumulated vertex data
     */
    public FloatBuffer getBuffer() {
        return cpuVertexBuffer;
    }

    /**
     * Gets the current vertex count
     */
    public int getVertexCount() {
        return vertexCount;
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

            buffer.setFormat(format);

            // Upload data
            buffer.setData(cpuVertexBuffer, dataIndex * 4); // 4 bytes per float


            // Draw the vertices
            graphics.drawPrimitives(buffer, PrimitiveType.QUADS, 0, this.vertexCount);
        }

        // Reset state
        this.clear();
    }


    /**
     * Creates a vertex buffer from the current tesselator state
     *
     * @param bufferUsage The buffer usage hint
     * @return The created vertex buffer
     */
    public VertexBuffer createVertexBuffer(BufferUsage bufferUsage) {
        // Set up vertex format based on tesselator state
        VertexBuffer.VertexFormat format = new VertexBuffer.VertexFormat(
                true,                      // Always has positions
                hasColor(),     // May have colors
                hasTexture(),   // May have texture coords
                false                      // No normals
        );

        VertexBuffer vertexBuffer = graphics.createVertexBuffer(bufferUsage);

        vertexBuffer.setFormat(format);

        // Upload data
        vertexBuffer.setData(cpuVertexBuffer, cpuVertexBuffer.remaining() * 4); // 4 bytes per float
        return vertexBuffer;
    }

    /**
     * Resets the tesselator state
     */
    private void clear() {
        this.vertexCount = 0;
        this.cpuVertexBuffer.clear();
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

        // Flush if buffer is almost full (on every 4 vertices as quads)
        if (this.vertexCount % 4 == 0 && this.dataIndex >= MAX_FLOATS - this.vertexSize * 4) {
            this.flush();
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
        if (buffer != null) {
            buffer.dispose();
            buffer = null;
        }
    }
}