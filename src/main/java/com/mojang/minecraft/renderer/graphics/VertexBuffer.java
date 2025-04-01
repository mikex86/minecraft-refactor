package com.mojang.minecraft.renderer.graphics;

import java.nio.FloatBuffer;

/**
 * Interface for a vertex buffer resource.
 * Represents a buffer containing vertex data for rendering.
 */
public interface VertexBuffer extends GraphicsResource {
    /**
     * Sets the vertex data in this buffer.
     * 
     * @param data      The buffer containing vertex data
     * @param sizeInBytes The size of the data in bytes
     */
    void setData(FloatBuffer data, int sizeInBytes);
    
    /**
     * Updates a portion of the vertex data in this buffer.
     * 
     * @param data      The buffer containing vertex data
     * @param offsetInBytes The offset in bytes to start updating
     * @param sizeInBytes   The size of the data in bytes
     */
    void updateData(FloatBuffer data, int offsetInBytes, int sizeInBytes);
    
    /**
     * Gets the vertex format of this buffer.
     * 
     * @return The vertex format
     */
    VertexFormat getFormat();
    
    /**
     * Sets the vertex format of this buffer.
     * 
     * @param format The vertex format
     */
    void setFormat(VertexFormat format);
    
    /**
     * Gets the size of this buffer in bytes.
     * 
     * @return The size in bytes
     */
    int getSizeInBytes();
    
    /**
     * Gets the number of vertices in this buffer.
     * 
     * @return The number of vertices
     */
    int getVertexCount();
    
    /**
     * A format descriptor for vertex data.
     * Describes the layout of a single vertex in a vertex buffer.
     */
    class VertexFormat {
        private final boolean hasPositions;
        private final boolean hasColors;
        private final boolean hasTexCoords;
        private final boolean hasNormals;
        private final int stride;
        
        /**
         * Creates a new vertex format.
         * 
         * @param hasPositions  Whether vertices have positions
         * @param hasColors     Whether vertices have colors
         * @param hasTexCoords  Whether vertices have texture coordinates
         * @param hasNormals    Whether vertices have normal vectors
         */
        public VertexFormat(boolean hasPositions, boolean hasColors, boolean hasTexCoords, boolean hasNormals) {
            this.hasPositions = hasPositions;
            this.hasColors = hasColors;
            this.hasTexCoords = hasTexCoords;
            this.hasNormals = hasNormals;
            
            // Calculate stride (in floats)
            int stride = 0;
            
            if (hasPositions) {
                stride += 3; // XYZ
            }
            
            if (hasColors) {
                stride += 3; // RGB
            }
            
            if (hasTexCoords) {
                stride += 2; // UV
            }
            
            if (hasNormals) {
                stride += 3; // XYZ
            }
            
            this.stride = stride;
        }
        
        /**
         * Gets whether this format includes positions.
         * 
         * @return true if this format includes positions
         */
        public boolean hasPositions() {
            return hasPositions;
        }
        
        /**
         * Gets whether this format includes colors.
         * 
         * @return true if this format includes colors
         */
        public boolean hasColors() {
            return hasColors;
        }
        
        /**
         * Gets whether this format includes texture coordinates.
         * 
         * @return true if this format includes texture coordinates
         */
        public boolean hasTexCoords() {
            return hasTexCoords;
        }
        
        /**
         * Gets whether this format includes normal vectors.
         * 
         * @return true if this format includes normal vectors
         */
        public boolean hasNormals() {
            return hasNormals;
        }
        
        /**
         * Gets the stride of this format.
         * The stride is the number of floats per vertex.
         * 
         * @return The stride
         */
        public int getStride() {
            return stride;
        }
        
        /**
         * Gets the stride of this format in bytes.
         * 
         * @return The stride in bytes
         */
        public int getStrideInBytes() {
            return stride * 4; // 4 bytes per float
        }
    }
} 