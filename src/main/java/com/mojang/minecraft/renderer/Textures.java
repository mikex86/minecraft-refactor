package com.mojang.minecraft.renderer;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.imageio.ImageIO;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

import static org.lwjgl.opengl.GL11.*;

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
            IntBuffer textureIdBuffer = BufferUtils.createIntBuffer(1);
            textureIdBuffer.clear();
            glGenTextures(textureIdBuffer);
            int textureId = textureIdBuffer.get(0);
            
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
            
            // Convert from ARGB to ABGR (required by OpenGL)
            for (int i = 0; i < pixels.length; i++) {
                int argb = pixels[i];
                int a = (argb >> 24) & 0xFF;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                
                // Reorder to ABGR format
                pixels[i] = (a << 24) | (b << 16) | (g << 8) | r;
            }
            
            // Put pixels into the buffer
            pixelBuffer.asIntBuffer().put(pixels);
            
            // Generate mipmaps and upload texture to GPU
            GLU.gluBuild2DMipmaps(GL_TEXTURE_2D, GL_RGBA, width, height, GL_RGBA, GL_UNSIGNED_BYTE, pixelBuffer);
            
            return textureId;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load texture: " + resourceName, e);
        }
    }
}
