package com.mojang.minecraft.level;

import com.mojang.minecraft.entity.Player;
import com.mojang.minecraft.renderer.Disposable;
import com.mojang.minecraft.renderer.Frustum;
import com.mojang.minecraft.renderer.TextureManager;
import com.mojang.minecraft.renderer.graphics.GraphicsAPI;
import com.mojang.minecraft.renderer.graphics.GraphicsFactory;
import com.mojang.minecraft.renderer.graphics.Texture;
import com.mojang.minecraft.renderer.shader.ShaderRegistry;
import com.mojang.minecraft.renderer.shader.impl.FogShader;
import com.mojang.minecraft.renderer.shader.impl.WorldShader;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
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

    // Matrix buffers for shader
    private final FloatBuffer modelViewMatrix = BufferUtils.createFloatBuffer(16);
    private final FloatBuffer projectionMatrix = BufferUtils.createFloatBuffer(16);

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
                chunk.render();
            }
        }
    }

    /**
     * Updates chunks that need to be rebuilt.
     */
    public void updateDirtyChunks(Player player) {
        List<Chunk> dirtyChunks = this.getAllDirtyChunks();
        if (dirtyChunks != null && !dirtyChunks.isEmpty()) {
            dirtyChunks.sort(new DirtyChunkSorter(player, Frustum.getFrustum(graphics)));

            // Rebuild at most MAX_REBUILDS_PER_FRAME chunks per frame
            int rebuildCount = Math.min(MAX_REBUILDS_PER_FRAME, dirtyChunks.size());
            int numRebuilt = 0;
            for (Chunk dirtyChunk : dirtyChunks) {
                if (!Frustum.getFrustum(graphics).isVisible(dirtyChunk.aabb)) {
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
     * Marks a region of chunks as dirty, needing to be rebuilt.
     */
    public void setDirty(int x0, int y0, int z0, int x1, int y1, int z1) {
        // Convert block coordinates to chunk coordinates
        x0 /= CHUNK_SIZE;
        x1 /= CHUNK_SIZE;
        y0 /= CHUNK_SIZE;
        y1 /= CHUNK_SIZE;
        z0 /= CHUNK_SIZE;
        z1 /= CHUNK_SIZE;

        // Clamp to valid chunk ranges
        x0 = Math.max(0, x0);
        y0 = Math.max(0, y0);
        z0 = Math.max(0, z0);
        x1 = Math.min(x1, this.xChunks - 1);
        y1 = Math.min(y1, this.yChunks - 1);
        z1 = Math.min(z1, this.zChunks - 1);

        // Mark all chunks in the region as dirty
        for (int x = x0; x <= x1; ++x) {
            for (int y = y0; y <= y1; ++y) {
                for (int z = z0; z <= z1; ++z) {
                    int chunkIndex = (x + y * this.xChunks) * this.zChunks + z;
                    this.chunks[chunkIndex].setDirty();
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
        this.setDirty(x - 1, y - 1, z - 1, x + 1, y + 1, z + 1);
    }

    /**
     * Called when a light column changes.
     */
    @Override
    public void lightColumnChanged(int x, int z, int y0, int y1) {
        // Mark a region around the changed light column as dirty
        this.setDirty(x - 1, y0 - 1, z - 1, x + 1, y1 + 1, z + 1);
    }

    /**
     * Called when the entire level changes.
     */
    @Override
    public void allChanged() {
        // Mark all chunks as dirty
        for (Chunk chunk : this.chunks) {
            chunk.setDirty();
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
