package com.mojang.minecraft.renderer;

import com.mojang.minecraft.renderer.graphics.GraphicsAPI;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums;
import com.mojang.minecraft.renderer.graphics.GraphicsFactory;
import com.mojang.minecraft.renderer.graphics.Texture;
import com.mojang.minecraft.util.io.IOUtils;
import org.lwjgl.BufferUtils;
import org.lwjgl.system.MemoryStack;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static org.lwjgl.stb.STBImage.*;

/**
 * Manages texture loading and caching for OpenGL rendering using STBImage
 */
public class TextureManager implements Disposable {
    private final GraphicsAPI graphics = GraphicsFactory.getGraphicsAPI();
    private final Map<String, Texture> textureCache = new HashMap<>();

    public Texture charTexture;
    public Texture terrainTexture;
    public Texture fontTexture;

    public void loadTextures() {
        charTexture = loadTexture("/char.png", Texture.FilterMode.NEAREST);
        terrainTexture = loadTexture("/terrain.png", Texture.FilterMode.NEAREST);
        fontTexture = loadTexture("/default.gif", Texture.FilterMode.NEAREST);
    }

    private Texture loadTexture(String resourcePath, Texture.FilterMode filterMode) {
        Objects.requireNonNull(graphics, "GraphicsAPI not initialized");

        if (textureCache.containsKey(resourcePath))
            return textureCache.get(resourcePath);

        byte[] bytes;
        try {
            bytes = IOUtils.readAllBytes(Objects.requireNonNull(getClass().getResourceAsStream(resourcePath)));
        } catch (IOException e) {
            throw new RuntimeException("Failed to load texture: " + resourcePath, e);
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer comp = stack.mallocInt(1);

            // Flip vertically if your textures expect bottom‐up origin:
            stbi_set_flip_vertically_on_load(false);

            ByteBuffer imageBuffer = ByteBuffer.allocateDirect(bytes.length);
            imageBuffer.put(bytes);
            imageBuffer.flip();
            ByteBuffer decoded = stbi_load_from_memory(imageBuffer, w, h, comp, 4);
            if (decoded == null) {
                throw new RuntimeException("STBImage failed to load: " + stbi_failure_reason());
            }

            int width = w.get(0);
            int height = h.get(0);

            Texture texture = graphics.createTexture(
                    width,
                    height,
                    GraphicsEnums.TextureFormat.RGBA8,
                    decoded
            );
            texture.setFiltering(filterMode, filterMode);


            textureCache.put(resourcePath, texture);
            System.out.println("Loaded texture: " + resourcePath +
                    " (" + width + "x" + height + ")");
            stbi_image_free(decoded);
            return texture;
        }
    }

    @Override
    public void dispose() {
        textureCache.values().forEach(Texture::dispose);
        textureCache.clear();
    }
}
