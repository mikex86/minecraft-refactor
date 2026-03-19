package com.mojang.minecraft.renderer;

import com.mojang.minecraft.renderer.graphics.GraphicsAPI;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums;
import com.mojang.minecraft.renderer.graphics.GraphicsFactory;
import com.mojang.minecraft.renderer.graphics.Texture;
import com.mojang.minecraft.renderer.resource.ResourceBufferLoader;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.jemalloc.JEmalloc;

import java.io.IOException;
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
    public Texture itemsTexture;
    public Texture fontTexture;
    public Texture guiTexture;
    public Texture inventoryTexture;
    public Texture craftingTexture;

    public void loadTextures() {
        charTexture = loadTexture("/char.png", Texture.FilterMode.NEAREST);
        terrainTexture = loadTexture("/terrain.png", Texture.FilterMode.NEAREST);
        itemsTexture = loadTexture("/items.png", Texture.FilterMode.NEAREST);
        fontTexture = loadTexture("/default.gif", Texture.FilterMode.NEAREST);
        guiTexture = loadTexture("/gui.png", Texture.FilterMode.NEAREST);
        inventoryTexture = loadTexture("/inventory.png", Texture.FilterMode.NEAREST);
        craftingTexture = loadTexture("/crafting.png", Texture.FilterMode.NEAREST);
    }

    private Texture loadTexture(String resourcePath, Texture.FilterMode filterMode) {
        Objects.requireNonNull(graphics, "GraphicsAPI not initialized");

        if (textureCache.containsKey(resourcePath))
            return textureCache.get(resourcePath);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer comp = stack.mallocInt(1);

            // Flip vertically if your textures expect bottom‐up origin:
            stbi_set_flip_vertically_on_load(false);

            ByteBuffer imageBuffer = null;
            try {
                imageBuffer = ResourceBufferLoader.loadResourceRequired(
                        getClass(),
                        resourcePath,
                        JEmalloc::je_malloc
                );
                ByteBuffer decoded = stbi_load_from_memory(imageBuffer, w, h, comp, 4);
                if (decoded == null) {
                    throw new RuntimeException("STBImage failed to load: " + stbi_failure_reason());
                }

                try {
                    int width = w.get(0);
                    int height = h.get(0);

                    Texture texture = graphics.createTextureHostAccessible(
                            width,
                            height,
                            GraphicsEnums.TextureFormat.RGBA8,
                            decoded
                    );
                    texture.setFiltering(filterMode, filterMode);

                    textureCache.put(resourcePath, texture);
                    System.out.println("Loaded texture: " + resourcePath +
                            " (" + width + "x" + height + ")");
                    return texture;
                } finally {
                    stbi_image_free(decoded);
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to load texture: " + resourcePath, e);
            } finally {
                if (imageBuffer != null) {
                    JEmalloc.je_free(imageBuffer);
                }
            }
        }
    }

    @Override
    public void dispose() {
        textureCache.values().forEach(Texture::dispose);
        textureCache.clear();
    }
}
