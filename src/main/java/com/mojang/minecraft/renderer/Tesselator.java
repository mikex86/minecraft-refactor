package com.mojang.minecraft.renderer;

import com.mojang.minecraft.profiler.NativeMemoryTracker;
import com.mojang.minecraft.renderer.graphics.*;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums.BufferUsage;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums.PrimitiveType;
import com.mojang.minecraft.util.Fp16Util;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.jemalloc.JEmalloc;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/**
 * Tesselator implementation that uses the GraphicsAPI.
 * This provides the same functionality as the original Tesselator, but
 * uses the abstracted graphics API instead of direct OpenGL calls.
 * <p>
 * This version uses indexed triangles instead of direct quads for modern GPU compatibility.
 * It also uses Vertex Array Objects (VAOs) for improved rendering performance.
 */
public final class Tesselator implements Disposable {
    private static final int MAX_BYTES = 1048576;
    private static final int MAX_INDICES = 262144;

    // CPU-side data storage
    private final long cpuVertexBuffer = JEmalloc.nje_calloc(MAX_BYTES, 1);
    private final long cpuVertexBufferCapacity = MAX_BYTES;

    private final long cpuIndexBuffer = JEmalloc.nje_calloc(MAX_INDICES, Integer.BYTES);
    private final long cpuIndexBufferCapacity = MAX_INDICES * Integer.BYTES;

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
    private float grayScale;

    private DataType positionDataType;
    private DataType texCoordsDataType;

    // Feature flags
    private boolean hasColor = false;
    private boolean hasGrayScale = false;
    private boolean hasTexture = false;
    private boolean disableColors = false;
    private boolean useIndexBuffer = true;

    // Graphics API and resources
    private final GraphicsAPI graphics;
    private VertexBuffer vertexBuffer;
    private IndexBuffer indexBuffer;
    private VertexArrayObject vao;
    private VertexBuffer.VertexFormat format;

    /**
     * Tesselator to use for everything else.
     */
    public static Tesselator instance = new Tesselator();

    /**
     * Creates a new tesselator
     */
    public Tesselator() {
        NativeMemoryTracker.ALLOCATED_NATIVE_MEMORY.addAndGet(cpuVertexBufferCapacity);
        NativeMemoryTracker.ALLOCATED_NATIVE_MEMORY.addAndGet(cpuIndexBufferCapacity);
        this.graphics = GraphicsFactory.getGraphicsAPI();
        this.vertexBuffer = null;
        this.indexBuffer = null;
        clear();
    }

    private void ensureVAOInitialized() {
        if (this.vertexBuffer != null) {
            return;
        }
        this.vertexBuffer = graphics.createVertexBuffer(BufferUsage.DYNAMIC);
        this.indexBuffer = graphics.createIndexBuffer(BufferUsage.DYNAMIC);
        this.vao = graphics.createVertexArrayObject();
    }

    /**
     * Gets the vertex buffer with accumulated vertex data
     */
    public ByteBuffer getBuffer() {
        return MemoryUtil.memByteBuffer(this.cpuVertexBuffer, this.dataIndex);
    }

    /**
     * Gets the index buffer with accumulated index data
     */
    public IntBuffer getIndexBuffer() {
        return MemoryUtil.memIntBuffer(this.cpuIndexBuffer, this.indexCount);
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
     * Returns whether this tesselator has grayscale data
     */
    public boolean hasGrayScale() {
        return hasGrayScale;
    }

    /**
     * Sends all accumulated vertices to the GPU and renders them directly
     */
    public void flush() {
        if (this.vertexCount > 0) {
            ensureVAOInitialized();

            // Update format
            format = new VertexBuffer.VertexFormat(
                    this.positionDataType, // Position data type
                    DataType.FLOAT, // Color data type
                    DataType.UNSIGNED_BYTE, // Grayscale data type
                    this.texCoordsDataType, // Texture coordinate data type
                    DataType.FLOAT, // Normal data type

                    true,      // Always has positions
                    this.hasColor,        // May have colors
                    this.hasGrayScale,    // May have grayscale
                    this.hasTexture,      // May have textures
                    false                 // No normals
            );

            int elementCount = useIndexBuffer ? indexCount : vertexCount;

            vertexBuffer.setFormat(format);

            // Upload data to GPU
            vertexBuffer.setData(getBuffer(), dataIndex);
            indexBuffer.setData(getIndexBuffer(), elementCount * Integer.BYTES); // 4 bytes per int

            // Set up VAO
            vao.setVertexBuffer(vertexBuffer);
            vao.setIndexBuffer(indexBuffer);

            // Draw the vertices
            graphics.drawPrimitives(vao, PrimitiveType.TRIANGLES, 0, elementCount);
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
        return createIndexedMesh(bufferUsage, false);
    }

    /**
     * Creates an indexed mesh from the current tesselator state
     *
     * @param bufferUsage The buffer usage hint
     * @param pooled      Whether to use pooled buffers
     * @return The created indexed mesh
     */
    public IndexedMesh createIndexedMesh(BufferUsage bufferUsage, boolean pooled) {
        ensureVAOInitialized();

        // Set up vertex format based on tesselator state
        VertexBuffer.VertexFormat format = new VertexBuffer.VertexFormat(
                this.positionDataType, // Position data type
                DataType.FLOAT, // Color data type
                DataType.UNSIGNED_BYTE, // Grayscale data type
                this.texCoordsDataType, // Texture coordinate data type
                DataType.FLOAT, // Normal data type

                true,                 // Always has positions
                hasColor(),           // May have colors
                hasGrayScale(),       // May have grayscale
                hasTexture(),         // May have texture coords
                false                 // No normals
        );

        // Create buffers
        VertexBuffer vertexBuffer;
        IndexBuffer indexBuffer = null;

        // Size calculations
        int vertexDataSizeInBytes = dataIndex;
        int indexDataSizeInBytes = indexCount * Integer.BYTES; // 4 bytes per int

        if (pooled) {
            vertexBuffer = graphics.createPooledVertexBuffer(vertexDataSizeInBytes);
            if (vertexBuffer == null) {
                System.out.println("Failed to create pooled vertex buffer, falling back to dynamic allocation");
                vertexBuffer = graphics.createVertexBuffer(bufferUsage);
            }

            if (useIndexBuffer) {
                indexBuffer = graphics.createPooledIndexBuffer(indexDataSizeInBytes);
                if (indexBuffer == null) {
                    System.out.println("Failed to create pooled index buffer, falling back to dynamic allocation");
                    indexBuffer = graphics.createIndexBuffer(bufferUsage);
                }
            }
        } else {
            vertexBuffer = graphics.createVertexBuffer(bufferUsage);
            if (useIndexBuffer) {
                indexBuffer = graphics.createIndexBuffer(bufferUsage);
            }
        }

        vertexBuffer.setFormat(format);

        // Upload data
        vertexBuffer.setData(getBuffer(), vertexDataSizeInBytes);
        if (indexBuffer != null) {
            indexBuffer.setData(getIndexBuffer(), indexDataSizeInBytes);
        }

        // Create mesh with VAO
        return new IndexedMesh(graphics, vertexBuffer, indexBuffer, vertexCount, indexCount);
    }

    /**
     * Resets the tesselator state
     */
    public void clear() {
        this.vertexCount = 0;
        this.indexCount = 0;
        this.dataIndex = 0;
    }

    /**
     * Initialize the tesselator for a new drawing sequence
     */
    public void init(DataType positionDataType, DataType texCoordsDataType, boolean useIndexBuffer) {
        this.clear();
        this.hasColor = false;
        this.hasTexture = false;
        this.disableColors = false;
        this.hasGrayScale = false;
        this.useIndexBuffer = useIndexBuffer;
        this.vertexSize = 3; // Start with just xyz

        this.positionDataType = positionDataType;
        this.texCoordsDataType = texCoordsDataType;
    }

    public void init() {
        this.init(DataType.FLOAT, DataType.FLOAT, true);
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
     * Set grayscale for the next vertex
     *
     * @param gray Grayscale value (0.0-1.0)
     */
    public void grayScale(float gray) {
        if (this.disableColors) {
            return;
        }

        if (this.hasColor) {
            throw new IllegalStateException("Cannot set grayscale when color is already set");
        }

        if (!this.hasGrayScale) {
            this.vertexSize += 1; // Add space for grayscale
        }

        this.hasGrayScale = true;
        this.grayScale = gray;
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
            switch (this.texCoordsDataType) {
                case FLOAT: {
                    MemoryUtil.memPutFloat(this.cpuVertexBuffer + currentIndex, this.textureU);
                    currentIndex += Float.BYTES;
                    MemoryUtil.memPutFloat(this.cpuVertexBuffer + currentIndex, this.textureV);
                    currentIndex += Float.BYTES;
                    break;
                }
                case HALF_FLOAT: {
                    MemoryUtil.memPutShort(this.cpuVertexBuffer + currentIndex, (short) Fp16Util.fromFloat(this.textureU));
                    currentIndex += Short.BYTES;
                    MemoryUtil.memPutShort(this.cpuVertexBuffer + currentIndex, (short) Fp16Util.fromFloat(this.textureV));
                    currentIndex += Short.BYTES;
                    break;
                }
                default: {
                    throw new IllegalArgumentException("Unsupported texture coordinate data type: " + this.texCoordsDataType);
                }
            }
        }

        // Add color if enabled
        if (this.hasColor) {
            MemoryUtil.memPutFloat(this.cpuVertexBuffer + currentIndex, this.colorR);
            currentIndex += Float.BYTES;
            MemoryUtil.memPutFloat(this.cpuVertexBuffer + currentIndex, this.colorG);
            currentIndex += Float.BYTES;
            MemoryUtil.memPutFloat(this.cpuVertexBuffer + currentIndex, this.colorB);
            currentIndex += Float.BYTES;
        }
        // Add grayscale if enabled
        else if (this.hasGrayScale) {
            MemoryUtil.memPutByte(this.cpuVertexBuffer + currentIndex, (byte) (this.grayScale * 255));
            currentIndex += Byte.BYTES;
        }

        // Add position (always present)
        switch (this.positionDataType) {
            case SHORT: {
                MemoryUtil.memPutShort(this.cpuVertexBuffer + currentIndex, (short) x);
                currentIndex += Short.BYTES;
                MemoryUtil.memPutShort(this.cpuVertexBuffer + currentIndex, (short) y);
                currentIndex += Short.BYTES;
                MemoryUtil.memPutShort(this.cpuVertexBuffer + currentIndex, (short) z);
                currentIndex += Short.BYTES;
                break;
            }
            case FLOAT: {
                MemoryUtil.memPutFloat(this.cpuVertexBuffer + currentIndex, x);
                currentIndex += Float.BYTES;
                MemoryUtil.memPutFloat(this.cpuVertexBuffer + currentIndex, y);
                currentIndex += Float.BYTES;
                MemoryUtil.memPutFloat(this.cpuVertexBuffer + currentIndex, z);
                currentIndex += Float.BYTES;
                break;
            }
            default: {
                throw new IllegalArgumentException("Unsupported position data type: " + this.positionDataType);
            }
        }

        // Update data index
        this.dataIndex = currentIndex;

        // Update vertex count
        this.vertexCount++;

        // Add indices for triangles
        if (this.useIndexBuffer && this.vertexCount % 4 == 0) {
            // For each quad, generate two triangles
            int baseIndex = this.vertexCount - 4;

            // First triangle (0, 1, 2)
            MemoryUtil.memPutInt(this.cpuIndexBuffer + (long) this.indexCount++ * Integer.BYTES, baseIndex);
            MemoryUtil.memPutInt(this.cpuIndexBuffer + (long) this.indexCount++ * Integer.BYTES, baseIndex + 1);
            MemoryUtil.memPutInt(this.cpuIndexBuffer + (long) this.indexCount++ * Integer.BYTES, baseIndex + 2);

            // Second triangle (0, 2, 3)
            MemoryUtil.memPutInt(this.cpuIndexBuffer + (long) this.indexCount++ * Integer.BYTES, baseIndex);
            MemoryUtil.memPutInt(this.cpuIndexBuffer + (long) this.indexCount++ * Integer.BYTES, baseIndex + 2);
            MemoryUtil.memPutInt(this.cpuIndexBuffer + (long) this.indexCount++ * Integer.BYTES, baseIndex + 3);
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
        // free the CPU-side buffers
        JEmalloc.nje_free(cpuVertexBuffer);
        JEmalloc.nje_free(cpuIndexBuffer);

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