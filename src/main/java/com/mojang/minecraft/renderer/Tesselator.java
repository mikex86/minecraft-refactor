package com.mojang.minecraft.renderer;

import java.nio.FloatBuffer;

import org.lwjgl.BufferUtils;

import static org.lwjgl.opengl.GL11.*;

/**
 * Handles efficient rendering of vertices for OpenGL rendering
 */
public class Tesselator {
    private static final int MAX_FLOATS = 524288;      // MAX_MEMORY_USE / 8 (size of float)

    // Vertex data storage
    private FloatBuffer vertexBuffer = BufferUtils.createFloatBuffer(MAX_FLOATS);
    private float[] vertexData = new float[MAX_FLOATS];

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

    // Singleton instance
    public static Tesselator instance = new Tesselator();

    /**
     * Private constructor for singleton pattern
     */
    private Tesselator() {
    }

    /**
     * Sends all accumulated vertices to the GPU and renders them
     */
    public void flush() {
        if (this.vertexCount > 0) {
            // Prepare buffer
            this.vertexBuffer.clear();
            this.vertexBuffer.put(this.vertexData, 0, this.dataIndex);
            this.vertexBuffer.flip();

            // Set up vertex data format
            if (this.hasTexture && this.hasColor) {
                glInterleavedArrays(GL_T2F_C3F_V3F, 0, this.vertexBuffer);
            } else if (this.hasTexture) {
                glInterleavedArrays(GL_T2F_V3F, 0, this.vertexBuffer);
            } else if (this.hasColor) {
                glInterleavedArrays(GL_C3F_V3F, 0, this.vertexBuffer);
            } else {
                glInterleavedArrays(GL_V3F, 0, this.vertexBuffer);
            }

            // Enable required vertex arrays
            glEnableClientState(GL_VERTEX_ARRAY);
            if (this.hasTexture) {
                glEnableClientState(GL_TEXTURE_COORD_ARRAY);
            }
            if (this.hasColor) {
                glEnableClientState(GL_COLOR_ARRAY);
            }

            // Draw the vertices
            glDrawArrays(GL_QUADS, 0, this.vertexCount);

            // Disable vertex arrays
            glDisableClientState(GL_VERTEX_ARRAY);
            if (this.hasTexture) {
                glDisableClientState(GL_TEXTURE_COORD_ARRAY);
            }
            if (this.hasColor) {
                glDisableClientState(GL_COLOR_ARRAY);
            }
        }

        // Reset state
        this.clear();
    }

    /**
     * Resets the tesselator state
     */
    private void clear() {
        this.vertexCount = 0;
        this.vertexBuffer.clear();
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
            this.vertexData[this.dataIndex++] = this.textureU;
            this.vertexData[this.dataIndex++] = this.textureV;
        }

        // Add color if set
        if (this.hasColor) {
            this.vertexData[this.dataIndex++] = this.colorR;
            this.vertexData[this.dataIndex++] = this.colorG;
            this.vertexData[this.dataIndex++] = this.colorB;
        }

        // Add vertex position
        this.vertexData[this.dataIndex++] = x;
        this.vertexData[this.dataIndex++] = y;
        this.vertexData[this.dataIndex++] = z;

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
     * Disable coloring for future vertices
     */
    public void noColor() {
        this.disableColors = true;
    }
}
