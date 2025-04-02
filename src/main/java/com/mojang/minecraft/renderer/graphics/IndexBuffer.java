package com.mojang.minecraft.renderer.graphics;

import java.nio.IntBuffer;

/**
 * Interface for an index buffer resource.
 * Represents a buffer containing index data for indexed rendering.
 */
public interface IndexBuffer extends GraphicsResource {
    /**
     * Sets the index data in this buffer.
     * 
     * @param data      The buffer containing index data
     * @param sizeInBytes The size of the data in bytes
     */
    void setData(IntBuffer data, int sizeInBytes);
    
    /**
     * Updates a portion of the index data in this buffer.
     * 
     * @param data      The buffer containing index data
     * @param offsetInBytes The offset in bytes to start updating
     * @param sizeInBytes   The size of the data in bytes
     */
    void updateData(IntBuffer data, int offsetInBytes, int sizeInBytes);
    
    /**
     * Gets the size of this buffer in bytes.
     * 
     * @return The size in bytes
     */
    int getSizeInBytes();
    
    /**
     * Gets the number of indices in this buffer.
     * 
     * @return The number of indices
     */
    int getIndexCount();
} 