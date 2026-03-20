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
import java.util.Optional;

import static org.lwjgl.stb.STBImage.*;

/**
 * Manages texture loading and caching for OpenGL rendering using STBImage
 */
public class TextureManager implements Disposable {
    private final Map<String, Texture> textureCache = new HashMap<>();
    private final Map<Texture, RetainedTextureData> retainedTextureData = new HashMap<>();

    public Texture charTexture;
    public Texture terrainTexture;
    public Texture itemsTexture;
    public Texture fontTexture;
    public Texture guiTexture;
    public Texture inventoryTexture;
    public Texture craftingTexture;

    public void loadTextures() {
        charTexture = loadTexture("/char.png", Texture.FilterMode.NEAREST, false);
        terrainTexture = loadTexture("/terrain.png", Texture.FilterMode.NEAREST, false);
        itemsTexture = loadTexture("/items.png", Texture.FilterMode.NEAREST, true);
        fontTexture = loadTexture("/default.gif", Texture.FilterMode.NEAREST, false);
        guiTexture = loadTexture("/gui.png", Texture.FilterMode.NEAREST, false);
        inventoryTexture = loadTexture("/inventory.png", Texture.FilterMode.NEAREST, false);
        craftingTexture = loadTexture("/crafting.png", Texture.FilterMode.NEAREST, false);
    }

    public Optional<ByteBuffer> getRetainedTextureData(Texture texture) {
        RetainedTextureData retainedData = retainedTextureData.get(texture);
        if (retainedData == null) {
            return Optional.empty();
        }
        return Optional.of(retainedData.readOnlyView());
    }

    private Texture loadTexture(String resourcePath, Texture.FilterMode filterMode, boolean retainTextureDataCopy) {
        GraphicsAPI graphics = GraphicsFactory.getGraphicsAPI();
        Objects.requireNonNull(graphics, "GraphicsAPI not initialized");

        Texture cachedTexture = textureCache.get(resourcePath);
        if (cachedTexture != null) {
            if (retainTextureDataCopy && !retainedTextureData.containsKey(cachedTexture)) {
                throw new IllegalStateException("Texture '" + resourcePath + "' was requested with retained CPU data, but no retained copy exists.");
            }
            return cachedTexture;
        }

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

                    Texture texture = graphics.createTexture(
                            width,
                            height,
                            GraphicsEnums.TextureFormat.RGBA8,
                            decoded
                    );
                    texture.setFiltering(filterMode, filterMode);

                    if (retainTextureDataCopy) {
                        retainedTextureData.put(texture, copyRetainedTextureData(decoded));
                    }

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
        for (RetainedTextureData data : retainedTextureData.values()) {
            data.dispose();
        }
        retainedTextureData.clear();
        for (Texture texture : textureCache.values()) {
            texture.dispose();
        }
        textureCache.clear();
    }

    private static RetainedTextureData copyRetainedTextureData(ByteBuffer srcDecoded) {
        ByteBuffer src = srcDecoded.duplicate();
        src.clear();
        int byteSize = src.remaining();
        long ptr = JEmalloc.nje_calloc(byteSize, 1);
        if (ptr == 0L) {
            throw new OutOfMemoryError("Failed to allocate retained texture copy of " + byteSize + " bytes");
        }
        ByteBuffer dst = org.lwjgl.system.MemoryUtil.memByteBuffer(ptr, byteSize);
        dst.clear();
        dst.put(src);
        dst.clear();
        return new RetainedTextureData(dst);
    }

    private static final class RetainedTextureData {
        private ByteBuffer buffer;

        private RetainedTextureData(ByteBuffer buffer) {
            this.buffer = buffer;
        }

        private ByteBuffer readOnlyView() {
            ByteBuffer view = buffer.asReadOnlyBuffer();
            view.clear();
            return view;
        }

        private void dispose() {
            if (buffer != null) {
                JEmalloc.je_free(buffer);
                buffer = null;
            }
        }
    }
}
