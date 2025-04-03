package com.mojang.minecraft.level;

import com.mojang.minecraft.entity.Player;
import com.mojang.minecraft.level.tile.Tile;
import com.mojang.minecraft.phys.AABB;
import com.mojang.minecraft.renderer.ChunkMesh;
import com.mojang.minecraft.renderer.Disposable;
import com.mojang.minecraft.renderer.Frustum;
import com.mojang.minecraft.renderer.Tesselator;
import com.mojang.minecraft.renderer.graphics.GraphicsAPI;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a chunk of the world that can be rendered independently.
 * Uses cubic sections (16x16x16) for more efficient rendering.
 */
public class Chunk implements Disposable {

    public static final int CHUNK_SIZE = 16;
    public static final int CHUNK_HEIGHT = 128;
    public static final int SECTION_SIZE = 16;

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

    private final byte[] blocks;

    // Chunk center coordinates
    public final float x;
    public final float y;
    public final float z;

    // Chunk sections
    private final List<ChunkSection> sections = new ArrayList<>();

    // Status tracking
    private boolean dirty = true;
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
    public Chunk(Level level, int chunkX, int chunkZ) {
        this.level = level;
        this.x0 = chunkX * CHUNK_SIZE;
        this.y0 = 0;
        this.z0 = chunkZ * CHUNK_SIZE;
        this.x1 = this.x0 + CHUNK_SIZE;
        this.y1 = CHUNK_HEIGHT;
        this.z1 = this.z0 + CHUNK_SIZE;

        // Calculate center coordinates
        this.x = (float) (x0 + x1) / 2.0F;
        this.y = (float) (y0 + y1) / 2.0F;
        this.z = (float) (z0 + z1) / 2.0F;

        // Create bounding box
        this.aabb = new AABB((float) x0, (float) y0, (float) z0, (float) x1, (float) y1, (float) z1);

        // Initialize blocks array
        this.blocks = new byte[CHUNK_SIZE * CHUNK_HEIGHT * CHUNK_HEIGHT];

        // Initialize sections
        initSections();
    }

    /**
     * Initializes the chunk sections based on chunk dimensions.
     */
    private void initSections() {
        int xSections = (int) Math.ceil(CHUNK_SIZE / (float) SECTION_SIZE);
        int ySections = (int) Math.ceil(CHUNK_HEIGHT / (float) SECTION_SIZE);
        int zSections = (int) Math.ceil(CHUNK_SIZE / (float) SECTION_SIZE);

        for (int sx = 0; sx < xSections; sx++) {
            for (int sy = 0; sy < ySections; sy++) {
                for (int sz = 0; sz < zSections; sz++) {
                    int sectionX0 = x0 + sx * SECTION_SIZE;
                    int sectionY0 = y0 + sy * SECTION_SIZE;
                    int sectionZ0 = z0 + sz * SECTION_SIZE;
                    int sectionX1 = Math.min(sectionX0 + SECTION_SIZE, x1);
                    int sectionY1 = Math.min(sectionY0 + SECTION_SIZE, y1);
                    int sectionZ1 = Math.min(sectionZ0 + SECTION_SIZE, z1);

                    sections.add(new ChunkSection(sectionX0, sectionY0, sectionZ0, sectionX1, sectionY1, sectionZ1));
                }
            }
        }
    }

    public int getTile(int localX, int localY, int localZ) {
        // check if in bounds
        if (localX < 0 || localY < 0 || localZ < 0 || localX >= CHUNK_SIZE || localY >= CHUNK_HEIGHT || localZ >= CHUNK_SIZE) {
            return 0;
        }
        // calculate the index in the blocks array
        int index = (localX * CHUNK_HEIGHT + localY) * CHUNK_SIZE + localZ;
        return blocks[index];
    }

    public boolean setTile(int localX, int localY, int localZ, int tileId) {
        // check if in bounds
        if (localX < 0 || localY < 0 || localZ < 0 || localX >= CHUNK_SIZE || localY >= CHUNK_HEIGHT || localZ >= CHUNK_SIZE) {
            return false;
        }
        // calculate the index in the blocks array
        int index = (localX * CHUNK_HEIGHT + localY) * CHUNK_SIZE + localZ;
        if (blocks[index] == tileId) {
            return false; // no change
        }

        blocks[index] = (byte) tileId;
        return true;
    }

    /**
     * Rebuilds the chunk mesh
     */
    public void rebuild() {
        if (!this.dirty) {
            boolean anySectionDirty = false;
            for (ChunkSection section : sections) {
                if (section.isDirty()) {
                    anySectionDirty = true;
                    break;
                }
            }

            if (!anySectionDirty) {
                return;
            }
        }

        ++updates;
        long startTime = System.nanoTime();

        int renderedTiles = 0;

        // Rebuild all dirty sections
        for (ChunkSection section : sections) {
            if (this.dirty || section.isDirty()) {
                section.rebuild(level);
                renderedTiles += section.getRenderedTiles();
            }
        }

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
    public void render(GraphicsAPI graphics, Frustum frustum) {
        for (ChunkSection section : sections) {
            if (section.hasMesh() && frustum.isVisible(section.getAABB())) {
                section.render(graphics);
            }
        }
    }

    /**
     * Marks the entire chunk as dirty, requiring a rebuild.
     */
    public void setFullChunkDirty() {
        if (!this.dirty) {
            this.dirtiedTime = System.currentTimeMillis();
        }
        this.dirty = true;

        // Mark all sections as dirty
        for (ChunkSection section : sections) {
            section.setDirty();
        }
    }

    /**
     * Marks a specific block position as dirty.
     * Only affects the section containing the block.
     */
    public void setDirtyBlock(int x, int y, int z) {
        for (ChunkSection section : sections) {
            if (section.containsOrAdjacent(x, y, z)) {
                section.setDirty();
                if (!this.dirty) {
                    this.dirtiedTime = System.currentTimeMillis();
                    this.dirty = true;
                }
                return;
            }
        }
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
        for (ChunkSection section : sections) {
            section.dispose();
        }
        sections.clear();
    }

    /**
     * Represents a 16x16x16 section of a chunk that can be rendered independently.
     */
    private static class ChunkSection implements Disposable {
        // Section boundaries
        private final int x0, y0, z0;
        private final int x1, y1, z1;

        // Section bounding box for frustum culling
        private final AABB aabb;

        // Rendering state
        private boolean dirty = true;
        private final ChunkMesh chunkMesh;
        private int renderedTiles = 0;
        private boolean empty = true;

        /**
         * Creates a new chunk section with the specified boundaries.
         */
        public ChunkSection(int x0, int y0, int z0, int x1, int y1, int z1) {
            this.x0 = x0;
            this.y0 = y0;
            this.z0 = z0;
            this.x1 = x1;
            this.y1 = y1;
            this.z1 = z1;

            // Create bounding box for frustum culling
            this.aabb = new AABB((float) x0, (float) y0, (float) z0, (float) x1, (float) y1, (float) z1);

            this.chunkMesh = new ChunkMesh();
        }

        /**
         * Checks if this section contains the specified block coordinates or is adjacent to them.
         */
        public boolean containsOrAdjacent(int x, int y, int z) {
            return (x >= x0 && x < x1 &&
                    y >= y0 && y < y1 &&
                    z >= z0 && z < z1) ||
                    (x == x0 - 1 || x == x1) ||
                    (y == y0 - 1 || y == y1) ||
                    (z == z0 - 1 || z == z1);
        }

        /**
         * Gets the section's axis-aligned bounding box.
         */
        public AABB getAABB() {
            return aabb;
        }

        /**
         * Rebuilds the section mesh
         */
        public void rebuild(Level level) {
            if (!this.dirty) {
                return;
            }

            Tesselator tesselator = chunkMesh.getTesselator();
            tesselator.init();

            renderedTiles = 0;
            empty = true;

            // Render all visible tiles in the section
            for (int x = this.x0; x < this.x1; ++x) {
                for (int y = this.y0; y < this.y1; ++y) {
                    for (int z = this.z0; z < this.z1; ++z) {
                        int tileId = level.getTile(x, y, z);
                        if (tileId > 0) {
                            Tile.tiles[tileId].render(tesselator, level, x, y, z);
                            ++renderedTiles;
                            empty = false;
                        }
                    }
                }
            }

            // Only rebuild the mesh if there are actual tiles in this section
            if (!empty) {
                chunkMesh.rebuild();
            }

            this.dirty = false;
        }

        /**
         * Renders this section
         *
         * @param graphics the graphics API
         */
        public void render(GraphicsAPI graphics) {
            if (!empty) {
                chunkMesh.draw(graphics);
            }
        }

        /**
         * Returns whether this section needs to be rebuilt.
         */
        public boolean isDirty() {
            return this.dirty || chunkMesh.isDirty();
        }

        /**
         * Marks this section as dirty, requiring a rebuild.
         */
        public void setDirty() {
            this.dirty = true;
            this.chunkMesh.setDirty();
        }

        /**
         * Returns the number of tiles rendered in this section.
         */
        public int getRenderedTiles() {
            return renderedTiles;
        }

        /**
         * Returns whether this section has a mesh (is not empty).
         */
        public boolean hasMesh() {
            return !empty;
        }

        /**
         * Disposes of this section's resources.
         */
        @Override
        public void dispose() {
            chunkMesh.dispose();
        }
    }
}
