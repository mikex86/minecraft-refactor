package com.mojang.minecraft.renderer.graphics.opengl;

import com.mojang.minecraft.renderer.graphics.Texture;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums.TextureFormat;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.opengl.GL14.GL_MIRRORED_REPEAT;
import static org.lwjgl.opengl.GL30.*;

/**
 * OpenGL implementation of the Texture interface.
 * Represents a texture in OpenGL.
 */
public class OpenGLTexture implements Texture {
    // OpenGL texture ID
    private int textureId;
    
    // Texture properties
    private final int width;
    private final int height;
    private final TextureFormat format;
    
    // State tracking
    private boolean disposed = false;
    
    /**
     * Creates a new OpenGL texture.
     * 
     * @param width  The texture width
     * @param height The texture height
     * @param format The texture format
     */
    public OpenGLTexture(int width, int height, TextureFormat format) {
        this.width = width;
        this.height = height;
        this.format = format;
        
        // Generate texture
        this.textureId = glGenTextures();
        
        // Initialize texture
        glBindTexture(GL_TEXTURE_2D, textureId);
        
        // Set default filtering
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        
        // Set default wrapping
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        
        // Allocate texture storage (without data)
        int glFormat = getGLFormat(format);
        int glInternalFormat = getGLInternalFormat(format);
        int glType = getGLType(format);
        
        glTexImage2D(GL_TEXTURE_2D, 0, glInternalFormat, width, height, 0, glFormat, glType, (ByteBuffer) null);
        
        // Unbind texture
        glBindTexture(GL_TEXTURE_2D, 0);
    }
    
    @Override
    public int getWidth() {
        return width;
    }
    
    @Override
    public int getHeight() {
        return height;
    }
    
    @Override
    public TextureFormat getFormat() {
        return format;
    }
    
    @Override
    public void update(int x, int y, int width, int height, ByteBuffer data) {
        if (isDisposed()) {
            throw new IllegalStateException("Cannot update a disposed texture");
        }
        
        // Validate parameters
        if (x < 0 || y < 0 || x + width > this.width || y + height > this.height) {
            throw new IllegalArgumentException("Update region out of bounds");
        }
        
        // Determine format details
        int glFormat = getGLFormat(format);
        int glType = getGLType(format);
        
        // Update texture
        glBindTexture(GL_TEXTURE_2D, textureId);
        glTexSubImage2D(GL_TEXTURE_2D, 0, x, y, width, height, glFormat, glType, data);
        glBindTexture(GL_TEXTURE_2D, 0);
    }
    
    @Override
    public void setFiltering(FilterMode minFilter, FilterMode magFilter) {
        if (isDisposed()) {
            throw new IllegalStateException("Cannot update a disposed texture");
        }
        
        glBindTexture(GL_TEXTURE_2D, textureId);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, translateFilterMode(minFilter));
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, translateFilterMode(magFilter));
        glBindTexture(GL_TEXTURE_2D, 0);
    }
    
    @Override
    public void setWrapping(WrapMode wrapS, WrapMode wrapT) {
        if (isDisposed()) {
            throw new IllegalStateException("Cannot update a disposed texture");
        }
        
        glBindTexture(GL_TEXTURE_2D, textureId);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, translateWrapMode(wrapS));
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, translateWrapMode(wrapT));
        glBindTexture(GL_TEXTURE_2D, 0);
    }
    
    @Override
    public void dispose() {
        if (!disposed) {
            glDeleteTextures(textureId);
            textureId = 0;
            disposed = true;
        }
    }
    
    @Override
    public boolean isDisposed() {
        return disposed;
    }
    
    /**
     * Binds this texture.
     * (Internal use by OpenGLGraphicsAPI)
     */
    void bind() {
        if (isDisposed()) {
            throw new IllegalStateException("Cannot bind a disposed texture");
        }
        
        glBindTexture(GL_TEXTURE_2D, textureId);
    }
    
    /**
     * Gets the OpenGL texture ID.
     * 
     * @return The OpenGL texture ID
     */
    int getTextureId() {
        return textureId;
    }
    
    //--------------------------------------------------
    // Helper methods
    //--------------------------------------------------
    
    private int translateFilterMode(FilterMode mode) {
        switch (mode) {
            case NEAREST:
                return GL_NEAREST;
            case LINEAR:
                return GL_LINEAR;
            case NEAREST_MIPMAP_NEAREST:
                return GL_NEAREST_MIPMAP_NEAREST;
            case LINEAR_MIPMAP_NEAREST:
                return GL_LINEAR_MIPMAP_NEAREST;
            case NEAREST_MIPMAP_LINEAR:
                return GL_NEAREST_MIPMAP_LINEAR;
            case LINEAR_MIPMAP_LINEAR:
                return GL_LINEAR_MIPMAP_LINEAR;
            default:
                return GL_NEAREST;
        }
    }
    
    private int translateWrapMode(WrapMode mode) {
        switch (mode) {
            case REPEAT:
                return GL_REPEAT;
            case MIRRORED_REPEAT:
                return GL_MIRRORED_REPEAT;
            case CLAMP_TO_EDGE:
                return GL_CLAMP_TO_EDGE;
            case CLAMP_TO_BORDER:
                return GL_CLAMP_TO_BORDER;
            default:
                return GL_REPEAT;
        }
    }
    
    private int getGLFormat(TextureFormat format) {
        switch (format) {
            case R8:
                return GL_RED;
            case RG8:
                return GL_RG;
            case RGB8:
                return GL_RGB;
            case RGBA8:
                return GL_RGBA;
            case R16F:
            case R32F:
                return GL_RED;
            case RG16F:
            case RG32F:
                return GL_RG;
            case RGB16F:
            case RGB32F:
                return GL_RGB;
            case RGBA16F:
            case RGBA32F:
                return GL_RGBA;
            case DEPTH16:
            case DEPTH24:
            case DEPTH32F:
                return GL_DEPTH_COMPONENT;
            default:
                return GL_RGBA;
        }
    }
    
    private int getGLInternalFormat(TextureFormat format) {
        switch (format) {
            case R8:
                return GL_R8;
            case RG8:
                return GL_RG8;
            case RGB8:
                return GL_RGB8;
            case RGBA8:
                return GL_RGBA8;
            case R16F:
                return GL_R16F;
            case RG16F:
                return GL_RG16F;
            case RGB16F:
                return GL_RGB16F;
            case RGBA16F:
                return GL_RGBA16F;
            case R32F:
                return GL_R32F;
            case RG32F:
                return GL_RG32F;
            case RGB32F:
                return GL_RGB32F;
            case RGBA32F:
                return GL_RGBA32F;
            case DEPTH16:
                return GL_DEPTH_COMPONENT16;
            case DEPTH24:
                return GL_DEPTH_COMPONENT24;
            case DEPTH32F:
                return GL_DEPTH_COMPONENT32F;
            default:
                return GL_RGBA8;
        }
    }
    
    private int getGLType(TextureFormat format) {
        switch (format) {
            case R8:
            case RG8:
            case RGB8:
            case RGBA8:
                return GL_UNSIGNED_BYTE;
            case R16F:
            case RG16F:
            case RGB16F:
            case RGBA16F:
                return GL_HALF_FLOAT;
            case R32F:
            case RG32F:
            case RGB32F:
            case RGBA32F:
                return GL_FLOAT;
            case DEPTH16:
                return GL_UNSIGNED_SHORT;
            case DEPTH24:
                return GL_UNSIGNED_INT;
            case DEPTH32F:
                return GL_FLOAT;
            default:
                return GL_UNSIGNED_BYTE;
        }
    }
    
    private int getBytesPerPixel(TextureFormat format) {
        switch (format) {
            case R8:
                return 1;
            case RG8:
                return 2;
            case RGB8:
                return 3;
            case RGBA8:
                return 4;
            case R16F:
                return 2;
            case RG16F:
                return 4;
            case RGB16F:
                return 6;
            case RGBA16F:
                return 8;
            case R32F:
                return 4;
            case RG32F:
                return 8;
            case RGB32F:
                return 12;
            case RGBA32F:
                return 16;
            case DEPTH16:
                return 2;
            case DEPTH24:
            case DEPTH32F:
                return 4;
            default:
                return 4;
        }
    }
} 