package com.mojang.minecraft.level;

import com.mojang.minecraft.entity.Player;
import com.mojang.minecraft.renderer.Disposable;
import com.mojang.minecraft.renderer.Frustum;
import com.mojang.minecraft.renderer.TextureManager;
import com.mojang.minecraft.renderer.graphics.GraphicsAPI;
import com.mojang.minecraft.renderer.graphics.GraphicsFactory;
import com.mojang.minecraft.renderer.graphics.Texture;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles rendering of the Minecraft level.
 */
public class LevelRenderer implements LevelListener, Disposable {
    // Constants
    public static final int MAX_REBUILDS_PER_FRAME = 4;

    // Level data
    private final Level level;

    // Graphics resources
    private final GraphicsAPI graphics;
    private final TextureManager textureManager;

    // Number of chunk sections draw calls issued this frame
    public static int numSectionDrawCalls = 0;

    /**
     * Creates a new GraphicsLevelRenderer for the specified level.
     */
    public LevelRenderer(Level level, TextureManager textureManager) {
        this.level = level;
        this.textureManager = textureManager;
        this.graphics = GraphicsFactory.getGraphicsAPI();

        // Register as a level listener
        level.addListener(this);
    }

    /**
     * Instance to return in {@link #getAllDirtyChunks()};
     * This makes the function not thread-safe, but this reduces allocation rate
     */
    private final ArrayList<Chunk> dirtyChunks = new ArrayList<>();

    /**
     * Gets all chunks that need to be rebuilt.
     * This function is not thread-safe and should only be called from the main thread.
     */
    public List<Chunk> getAllDirtyChunks() {
        dirtyChunks.clear();
        for (Chunk chunk : this.level.getLoadedChunks()) {
            if (chunk.isDirty()) {
                dirtyChunks.add(chunk);
            }
        }

        return dirtyChunks;
    }

    /**
     * Renders the level
     */
    public void render() {
        // Enable texturing and bind the terrain texture
        Texture texture = textureManager.terrainTexture;
        graphics.setTexture(texture);

        // Get the current view frustum
        Frustum frustum = Frustum.getFrustum(graphics);

        // Render all visible chunks
        numSectionDrawCalls = 0;
        for (Chunk chunk : this.level.getLoadedChunks()) {
            if (frustum.isVisible(chunk.aabb)) {
                numSectionDrawCalls += chunk.render(graphics, frustum);
            }
        }
    }

    /**
     * Updates chunks that need to be rebuilt.
     */
    public void updateDirtyChunks(Player player) {
        List<Chunk> dirtyChunks = this.getAllDirtyChunks();
        if (dirtyChunks != null && !dirtyChunks.isEmpty()) {
            Frustum frustum = Frustum.getFrustum(graphics);
            dirtyChunks.sort(new DirtyChunkSorter(player, frustum));

            // Rebuild at most MAX_REBUILDS_PER_FRAME chunks per frame
            int rebuildCount = Math.min(MAX_REBUILDS_PER_FRAME, dirtyChunks.size());
            int numRebuilt = 0;
            for (Chunk dirtyChunk : dirtyChunks) {
                if (!frustum.isVisible(dirtyChunk.aabb)) {
                    continue;
                }
                dirtyChunk.rebuild();
                numRebuilt++;
                if (numRebuilt >= rebuildCount) {
                    break;
                }
            }
        }
    }

    /**
     * Called when a tile changes.
     */
    @Override
    public void tileChanged(int x, int y, int z) {
    }

    /**
     * Called when a light column changes.
     */
    @Override
    public void lightColumnChanged(int x, int z, int y0, int y1) {
    }

    /**
     * Called when the entire level changes.
     */
    @Override
    public void allChanged() {
        // Mark all chunks as dirty
        for (Chunk chunk : this.level.getLoadedChunks()) {
            chunk.setFullChunkDirty();
        }
    }

    /**
     * Disposes all chunks and resources when the level is unloaded.
     * This must be called when the level is no longer needed to prevent memory leaks.
     */
    @Override
    public void dispose() {
        for (Chunk chunk : this.level.getLoadedChunks()) {
            if (chunk != null) {
                chunk.dispose();
            }
        }
    }
}
