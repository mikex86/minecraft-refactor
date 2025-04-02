package com.mojang.minecraft.level;

import com.mojang.minecraft.entity.Player;
import com.mojang.minecraft.level.tile.Tile;
import com.mojang.minecraft.phys.AABB;
import com.mojang.minecraft.renderer.ChunkMesh;
import com.mojang.minecraft.renderer.Disposable;
import com.mojang.minecraft.renderer.Tesselator;

/**
 * Represents a chunk of the world that can be rendered independently.
 * Designed to support cubic chunks in the future.
 */
public class Chunk implements Disposable {

    // Bounding box for this chunk
    public AABB aabb;

    // Parent level reference
    public final Level level;

    // Chunk boundaries
    public final int x0;
    public final int y0;
    public final int z0;
    public final int x1;
    public final int y1;
    public final int z1;

    // Chunk center coordinates
    public final float x;
    public final float y;
    public final float z;

    // Rendering state
    private boolean dirty = true;
    private final ChunkMesh chunkMesh;
    public long dirtiedTime = 0L;

    // Static rendering stats
    public static int updates;
    private static long totalTime;
    private static int totalUpdates;

    static {
        updates = 0;
        totalTime = 0L;
        totalUpdates = 0;
    }

    /**
     * Creates a new chunk with the specified boundaries.
     */
    public Chunk(Level level, int x0, int y0, int z0, int x1, int y1, int z1) {
        this.level = level;
        this.x0 = x0;
        this.y0 = y0;
        this.z0 = z0;
        this.x1 = x1;
        this.y1 = y1;
        this.z1 = z1;

        // Calculate center coordinates
        this.x = (float) (x0 + x1) / 2.0F;
        this.y = (float) (y0 + y1) / 2.0F;
        this.z = (float) (z0 + z1) / 2.0F;

        // Create bounding box and meshes
        this.aabb = new AABB((float) x0, (float) y0, (float) z0, (float) x1, (float) y1, (float) z1);
        this.chunkMesh = new ChunkMesh();
    }

    /**
     * Rebuilds the chunk mesh
     */
    public void rebuild() {
        if (!this.dirty && !chunkMesh.isDirty()) {
            return;
        }

        ++updates;
        long startTime = System.nanoTime();

        Tesselator tesselator = chunkMesh.getTesselator();
        tesselator.init();

        int renderedTiles = 0;

        // Render all visible tiles in the chunk
        for (int x = this.x0; x < this.x1; ++x) {
            for (int y = this.y0; y < this.y1; ++y) {
                for (int z = this.z0; z < this.z1; ++z) {
                    int tileId = this.level.getTile(x, y, z);
                    if (tileId > 0) {
                        Tile.tiles[tileId].render(tesselator, level, x, y, z);
                        ++renderedTiles;
                    }
                }
            }
        }

        // Rebuild the mesh with the accumulated data
        chunkMesh.rebuild();

        long endTime = System.nanoTime();

        // Update rendering statistics
        if (renderedTiles > 0) {
            totalTime += endTime - startTime;
            ++totalUpdates;
        }
        this.dirty = false;
    }

    /**
     * Renders the given chunk
     */
    public void render() {
        this.chunkMesh.render();
    }

    /**
     * Marks the chunk as dirty, requiring a rebuild.
     */
    public void setDirty() {
        if (!this.dirty) {
            this.dirtiedTime = System.currentTimeMillis();
        }
        this.dirty = true;
        this.chunkMesh.setDirty();
    }

    /**
     * Returns whether the chunk needs to be rebuilt.
     */
    public boolean isDirty() {
        return this.dirty;
    }

    /**
     * Calculates the squared distance from this chunk to the player.
     */
    public float distanceToSqr(Player player) {
        float xDistance = player.x - this.x;
        float yDistance = player.y - this.y;
        float zDistance = player.z - this.z;
        return xDistance * xDistance + yDistance * yDistance + zDistance * zDistance;
    }

    /**
     * Disposes of this chunk's resources.
     * Should be called when the chunk is no longer needed.
     */
    @Override
    public void dispose() {
        chunkMesh.dispose();
    }
}
