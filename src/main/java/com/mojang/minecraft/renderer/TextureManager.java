package com.mojang.minecraft.renderer;

import com.mojang.minecraft.renderer.graphics.GraphicsAPI;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums;
import com.mojang.minecraft.renderer.graphics.GraphicsFactory;
import com.mojang.minecraft.renderer.graphics.Texture;
import org.lwjgl.BufferUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages texture loading and caching for OpenGL rendering
 */
public class TextureManager implements Disposable {

    // Graphics context
    private final GraphicsAPI graphics;

    // Texture cache
    private final Map<String, Texture> textureCache = new HashMap<>();

    /**
     * Creates a new texture manager.
     */
    public TextureManager() {
        this.graphics = GraphicsFactory.getGraphicsAPI();
    }

    /**
     * Loads a texture from a resource path.
     *
     * @param resourcePath The path to the texture resource
     * @return The texture ID
     */
    public Texture loadTexture(String resourcePath, Texture.FilterMode filterMode) {
        // If already loaded, return its ID
        if (textureCache.containsKey(resourcePath)) {
            return textureCache.get(resourcePath);
        }

        // Load image
        BufferedImage image = null;
        try {
            image = loadImage(resourcePath);
        } catch (IOException e) {
            // TODO: FIX THIS BY REMOVING THE DYNAMIC LOAD SYSTEM
            throw new RuntimeException(e);
        }

        // Convert image to byte buffer
        ByteBuffer buffer = imageToBuffer(image);

        // Create texture
        Texture texture = graphics.createTexture(
                image.getWidth(),
                image.getHeight(),
                GraphicsEnums.TextureFormat.RGBA8,
                buffer
        );

        // Set filtering
        texture.setFiltering(filterMode, filterMode);

        // Store in cache
        textureCache.put(resourcePath, texture);

        // For debugging
        System.out.println("Loaded texture: " + resourcePath +
                " (" + image.getWidth() + "x" + image.getHeight() + ")");

        return texture;
    }

    /**
     * Loads an image from a resource path.
     *
     * @param resourcePath The path to the image resource
     * @return The loaded image
     * @throws IOException If the image couldn't be loaded
     */
    private BufferedImage loadImage(String resourcePath) throws IOException {
        try (InputStream is = TextureManager.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            return ImageIO.read(is);
        }
    }

    /**
     * Converts a BufferedImage to a ByteBuffer.
     *
     * @param image The image to convert
     * @return The image data as a ByteBuffer
     */
    private ByteBuffer imageToBuffer(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();

        // Get pixel data
        int[] pixels = new int[width * height];
        image.getRGB(0, 0, width, height, pixels, 0, width);

        // Convert to RGBA format
        byte[] buffer = new byte[width * height * 4];

        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            int alpha = (pixel >> 24) & 0xFF;
            int red = (pixel >> 16) & 0xFF;
            int green = (pixel >> 8) & 0xFF;
            int blue = pixel & 0xFF;

            // RGBA format (byte order)
            buffer[i * 4] = (byte) red;
            buffer[i * 4 + 1] = (byte) green;
            buffer[i * 4 + 2] = (byte) blue;
            buffer[i * 4 + 3] = (byte) alpha;
        }

        // Convert to ByteBuffer
        ByteBuffer byteBuffer = BufferUtils.createByteBuffer(buffer.length);
        byteBuffer.put(buffer);
        byteBuffer.flip();

        return byteBuffer;
    }

    /**
     * Disposes of all textures.
     */
    @Override
    public void dispose() {
        for (Texture texture : textureCache.values()) {
            texture.dispose();
        }
        textureCache.clear();
    }
}
