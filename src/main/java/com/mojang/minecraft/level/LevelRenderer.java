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

import static com.mojang.minecraft.level.Chunk.CHUNK_SIZE;

/**
 * Handles rendering of the Minecraft level.
 */
public class LevelRenderer implements LevelListener, Disposable {
    // Constants
    public static final int MAX_REBUILDS_PER_FRAME = 128;

    // Level data
    private final Level level;

    // Graphics resources
    private final GraphicsAPI graphics;
    private final TextureManager textureManager;

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
     * Gets all chunks that need to be rebuilt.
     */
    public List<Chunk> getAllDirtyChunks() {
        ArrayList<Chunk> dirtyChunks = null;

        for (Chunk chunk : this.level.getLoadedChunks()) {
            if (chunk.isDirty()) {
                if (dirtyChunks == null) {
                    dirtyChunks = new ArrayList<>();
                }
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
        Texture texture = textureManager.loadTexture("/terrain.png", Texture.FilterMode.NEAREST);
        graphics.setTexture(texture);

        // Get the current view frustum
        Frustum frustum = Frustum.getFrustum(graphics);

        // Render all visible chunks
        for (Chunk chunk : this.level.getLoadedChunks()) {
            if (frustum.isVisible(chunk.aabb)) {
                chunk.render(graphics, frustum);
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
     * A block position as dirty and triggers all chunks that contain/border it to be rebuilt.
     */
    public void setDirty(int x, int y, int z) {
        // Calculate the chunk coordinates
        int cx = x / CHUNK_SIZE;
        int cy = y / CHUNK_SIZE;
        int cz = z / CHUNK_SIZE;

        // Mark the chunk as dirty
        Chunk chunk = this.level.getChunk(cx, cz);
        chunk.setDirtyBlock(x, y, z);

        // Mark all adjacent chunks as dirty
        for (int xx = -1; xx <= 1; ++xx) {
            for (int zz = -1; zz <= 1; ++zz) {
                // Skip the center chunk
                if (xx == 0 && zz == 0) {
                    continue;
                }

                int ax = cx + xx;
                int az = cz + zz;

                Chunk adjacentChunk = this.level.getChunk(ax, az);
                if (adjacentChunk != null && adjacentChunk != chunk) {
                    adjacentChunk.setDirtyBlock(x, y, z);
                }
            }
        }
    }

    /**
     * Called when a tile changes.
     */
    @Override
    public void tileChanged(int x, int y, int z) {
        // Mark a region around the changed tile as dirty
        this.setDirty(x, y, z);
    }

    /**
     * Called when a light column changes.
     */
    @Override
    public void lightColumnChanged(int x, int z, int y0, int y1) {
        this.setDirty(x, y0, z);
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
