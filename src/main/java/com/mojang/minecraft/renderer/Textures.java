package com.mojang.minecraft.renderer;

import static org.lwjgl.opengl.GL11.*;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.imageio.ImageIO;

import org.lwjgl.BufferUtils;

/**
 * Manages texture loading and caching for OpenGL rendering
 */
public class Textures {
    
    private final Map<String, Integer> textureCache = new HashMap<>();

    /**
     * Loads a texture from resources and applies filtering
     * 
     * @param resourceName Path to the texture resource
     * @param filterMode OpenGL filter mode (GL_NEAREST, GL_LINEAR, etc.)
     * @return OpenGL texture ID
     */
    public int loadTexture(String resourceName, int filterMode) {
        try {
            // Check if texture is already loaded
            if (this.textureCache.containsKey(resourceName)) {
                return this.textureCache.get(resourceName);
            }
            
            // Generate a new texture ID
            int textureId = glGenTextures();
            
            // Cache the new texture ID
            this.textureCache.put(resourceName, textureId);
            System.out.println("Loading texture: " + resourceName + " -> ID: " + textureId);
            
            // Bind the texture and set filtering options
            glBindTexture(GL_TEXTURE_2D, textureId);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filterMode);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filterMode);
            
            // Load the image from resources
            BufferedImage image = ImageIO.read(Objects.requireNonNull(Textures.class.getResourceAsStream(resourceName)));
            int width = image.getWidth();
            int height = image.getHeight();
            
            // Prepare buffer for pixel data
            ByteBuffer pixelBuffer = BufferUtils.createByteBuffer(width * height * 4); // 4 bytes per pixel (RGBA)
            int[] pixels = new int[width * height];
            
            // Get the RGB data from the image
            image.getRGB(0, 0, width, height, pixels, 0, width);
            
            // Convert from ARGB to RGBA (required by OpenGL)
            for (int i = 0; i < pixels.length; i++) {
                int argb = pixels[i];
                int a = (argb >> 24) & 0xFF;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                
                // Add to the ByteBuffer in RGBA order
                pixelBuffer.put((byte) r);
                pixelBuffer.put((byte) g);
                pixelBuffer.put((byte) b);
                pixelBuffer.put((byte) a);
            }
            
            // Flip the buffer to prepare for OpenGL
            pixelBuffer.flip();

            // Generate mipmaps and upload texture to GPU
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixelBuffer);
            return textureId;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load texture: " + resourceName, e);
        }
    }
    
    /**
     * Clean up all texture resources
     */
    public void dispose() {
        for (int textureId : this.textureCache.values()) {
            glDeleteTextures(textureId);
        }
        this.textureCache.clear();
    }
}
