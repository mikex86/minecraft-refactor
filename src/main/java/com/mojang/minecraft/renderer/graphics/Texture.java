package com.mojang.minecraft.renderer.graphics;

import com.mojang.minecraft.renderer.graphics.GraphicsEnums.TextureFormat;

import java.nio.ByteBuffer;

/**
 * Interface for a texture resource.
 * Represents a texture that can be used for rendering.
 */
public interface Texture extends GraphicsResource {
    /**
     * Gets the width of this texture.
     * 
     * @return The width in pixels
     */
    int getWidth();
    
    /**
     * Gets the height of this texture.
     * 
     * @return The height in pixels
     */
    int getHeight();
    
    /**
     * Gets the format of this texture.
     * 
     * @return The texture format
     */
    TextureFormat getFormat();
    
    /**
     * Updates a region of this texture with new data.
     * 
     * @param x      The x coordinate to start updating
     * @param y      The y coordinate to start updating
     * @param width  The width of the region to update
     * @param height The height of the region to update
     * @param data   The new data
     */
    void update(int x, int y, int width, int height, ByteBuffer data);
    
    /**
     * Sets texture filtering mode.
     * 
     * @param minFilter The minification filter
     * @param magFilter The magnification filter
     */
    void setFiltering(FilterMode minFilter, FilterMode magFilter);
    
    /**
     * Sets texture wrapping mode.
     * 
     * @param wrapS The horizontal wrapping mode
     * @param wrapT The vertical wrapping mode
     */
    void setWrapping(WrapMode wrapS, WrapMode wrapT);
    
    /**
     * Filtering modes for textures.
     */
    enum FilterMode {
        NEAREST,
        LINEAR,
        NEAREST_MIPMAP_NEAREST,
        LINEAR_MIPMAP_NEAREST,
        NEAREST_MIPMAP_LINEAR,
        LINEAR_MIPMAP_LINEAR
    }
    
    /**
     * Wrapping modes for textures.
     */
    enum WrapMode {
        REPEAT,
        MIRRORED_REPEAT,
        CLAMP_TO_EDGE,
        CLAMP_TO_BORDER
    }
} 