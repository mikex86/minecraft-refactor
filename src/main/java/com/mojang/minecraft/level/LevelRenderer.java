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
    public static final int MAX_REBUILDS_PER_FRAME = 128;
    public static final int CHUNK_SIZE = 16;

    // Level data
    private final Level level;
    private final Chunk[] chunks;
    private final int xChunks;
    private final int yChunks;
    private final int zChunks;

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

        // Calculate the number of chunks in each dimension
        this.xChunks = level.width / CHUNK_SIZE;
        this.yChunks = level.depth / CHUNK_SIZE;
        this.zChunks = level.height / CHUNK_SIZE;

        // Create the chunks
        this.chunks = new Chunk[this.xChunks * this.yChunks * this.zChunks];

        // Initialize all chunks
        for (int x = 0; x < this.xChunks; ++x) {
            for (int y = 0; y < this.yChunks; ++y) {
                for (int z = 0; z < this.zChunks; ++z) {
                    // Calculate chunk boundaries
                    int x0 = x * CHUNK_SIZE;
                    int y0 = y * CHUNK_SIZE;
                    int z0 = z * CHUNK_SIZE;
                    int x1 = (x + 1) * CHUNK_SIZE;
                    int y1 = (y + 1) * CHUNK_SIZE;
                    int z1 = (z + 1) * CHUNK_SIZE;

                    // Clamp to level bounds
                    if (x1 > level.width) {
                        x1 = level.width;
                    }
                    if (y1 > level.depth) {
                        y1 = level.depth;
                    }
                    if (z1 > level.height) {
                        z1 = level.height;
                    }

                    // Create and store the chunk
                    int chunkIndex = (x + y * this.xChunks) * this.zChunks + z;
                    this.chunks[chunkIndex] = new Chunk(level, x0, y0, z0, x1, y1, z1);
                }
            }
        }
    }

    /**
     * Gets all chunks that need to be rebuilt.
     */
    public List<Chunk> getAllDirtyChunks() {
        ArrayList<Chunk> dirtyChunks = null;

        for (Chunk chunk : this.chunks) {
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
        for (Chunk chunk : this.chunks) {
            if (frustum.isVisible(chunk.aabb)) {
                chunk.render(frustum);
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
        if (cx >= 0 && cy >= 0 && cz >= 0 && cx < this.xChunks && cy < this.yChunks && cz < this.zChunks) {
            int chunkIndex = (cx + cy * this.xChunks) * this.zChunks + cz;
            Chunk chunk = this.chunks[chunkIndex];
            chunk.setDirtyBlock(x, y, z);
        }

        // Mark all adjacent chunks as dirty
        for (int xx = -1; xx <= 1; ++xx) {
            for (int yy = -1; yy <= 1; ++yy) {
                for (int zz = -1; zz <= 1; ++zz) {
                    int ax = cx + xx;
                    int ay = cy + yy;
                    int az = cz + zz;
                    if (ax >= 0 && ay >= 0 && az >= 0 && ax < this.xChunks && ay < this.yChunks && az < this.zChunks) {
                        int chunkIndex = (ax + ay * this.xChunks) * this.zChunks + az;
                        Chunk chunk = this.chunks[chunkIndex];
                        chunk.setDirtyBlock(x, y , z);
                    }
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
        for (Chunk chunk : this.chunks) {
            chunk.setFullChunkDirty();
        }
    }

    /**
     * Disposes all chunks and resources when the level is unloaded.
     * This must be called when the level is no longer needed to prevent memory leaks.
     */
    @Override
    public void dispose() {
        for (Chunk chunk : this.chunks) {
            if (chunk != null) {
                chunk.dispose();
            }
        }
    }
}
