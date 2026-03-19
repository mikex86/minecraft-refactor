package com.mojang.minecraft.renderer.graphics;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Interface for a vertex buffer resource.
 * Represents a buffer containing vertex data for rendering.
 */
public interface VertexBuffer extends GraphicsResource {
    /**
     * Sets the vertex data in this buffer.
     *
     * @param data        The buffer containing vertex data
     * @param sizeInBytes The size of the data in bytes
     */
    void setData(ByteBuffer data, int sizeInBytes);

    /**
     * Updates a portion of the vertex data in this buffer.
     *
     * @param data          The buffer containing vertex data
     * @param offsetInBytes The offset in bytes to start updating
     * @param sizeInBytes   The size of the data in bytes
     */
    void updateData(ByteBuffer data, int offsetInBytes, int sizeInBytes);

    /**
     * Gets the size of this buffer in bytes.
     *
     * @return The size in bytes
     */
    long getSizeInBytes();

    /**
     * A format descriptor for vertex data.
     * Describes the layout of a single vertex in a vertex buffer.
     */
    class VertexFormat {
        private final boolean hasPositions;
        private final boolean hasColors;
        private final boolean hasGrayScale;
        private final boolean hasTexCoords;
        private final boolean hasNormals;
        private final int stride;

        private final DataType positionDataType;
        private final DataType colorDataType;
        private final DataType grayScaleDataType;
        private final DataType texCoordDataType;
        private final DataType normalDataType;

        /**
         * Creates a new vertex format.
         *
         * @param positionDataType  Data type for position data (may be null if not used)
         * @param colorDataType     Data type for color data (may be null if not used)
         * @param grayScaleDataType Data type for grayscale data (may be null if not used)
         * @param texCoordDataType  Data type for texture coordinate data (may be null if not used)
         * @param normalDataType    Data type for normal vector data (may be null if not used)
         * @param hasPositions      Whether vertices have positions
         * @param hasColors         Whether vertices have colors
         * @param hasGrayScale      Whether vertices have grayscale colors
         * @param hasTexCoords      Whether vertices have texture coordinates
         * @param hasNormals        Whether vertices have normal vectors
         */
        public VertexFormat(
                DataType positionDataType, DataType colorDataType, DataType grayScaleDataType, DataType texCoordDataType, DataType normalDataType,
                boolean hasPositions, boolean hasColors, boolean hasGrayScale, boolean hasTexCoords, boolean hasNormals) {
            this.positionDataType = positionDataType;
            this.colorDataType = colorDataType;
            this.grayScaleDataType = grayScaleDataType;
            this.texCoordDataType = texCoordDataType;
            this.normalDataType = normalDataType;

            this.hasPositions = hasPositions;
            this.hasColors = hasColors;
            this.hasGrayScale = hasGrayScale;
            this.hasTexCoords = hasTexCoords;
            this.hasNormals = hasNormals;

            // Calculate stride (in floats)
            int stride = 0;

            if (hasPositions) {
                Objects.requireNonNull(positionDataType, "Position data type cannot be null if positions are enabled");
                stride += 3 * positionDataType.getSize(); // XYZ
            }

            if (hasColors) {
                Objects.requireNonNull(colorDataType, "Color data type cannot be null if colors are enabled");
                stride += 3 * colorDataType.getSize(); // RGB
            }

            if (hasGrayScale) {
                Objects.requireNonNull(grayScaleDataType, "Grayscale data type cannot be null if grayscale is enabled");
                stride += grayScaleDataType.getSize(); // Grayscale
            }

            if (hasTexCoords) {
                Objects.requireNonNull(texCoordDataType, "Texture coordinate data type cannot be null if texture coordinates are enabled");
                stride += 2 * texCoordDataType.getSize(); // UV
            }

            if (hasNormals) {
                Objects.requireNonNull(normalDataType, "Normal data type cannot be null if normals are enabled");
                stride += 3 * normalDataType.getSize(); // Normal
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
         * Gets whether this format includes grayscale colors.
         *
         * @return true if this format includes grayscale colors
         */
        public boolean hasGrayScale() {
            return hasGrayScale;
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
         * Gets the stride of this format in bytes.
         *
         * @return The stride in bytes
         */
        public int getStrideInBytes() {
            return stride;
        }

        public DataType getColorDataType() {
            return colorDataType;
        }

        public DataType getGrayScaleDataType() {
            return grayScaleDataType;
        }

        public DataType getNormalDataType() {
            return normalDataType;
        }

        public DataType getPositionDataType() {
            return positionDataType;
        }

        public DataType getTexCoordDataType() {
            return texCoordDataType;
        }
    }
} 
