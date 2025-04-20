package com.mojang.minecraft.level;

import com.mojang.minecraft.Minecraft;
import com.mojang.minecraft.entity.Player;
import com.mojang.minecraft.level.block.Blocks;
import com.mojang.minecraft.level.block.state.BlockState;
import com.mojang.minecraft.phys.AABB;
import com.mojang.minecraft.renderer.ChunkMesh;
import com.mojang.minecraft.renderer.Disposable;
import com.mojang.minecraft.renderer.Frustum;
import com.mojang.minecraft.renderer.Tesselator;
import com.mojang.minecraft.renderer.graphics.GraphicsAPI;
import com.mojang.minecraft.util.math.MathUtils;
import com.mojang.minecraft.util.nio.NativeByteArray;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a chunk of the world that can be rendered independently.
 * Uses cubic sections (16x16x16) for more efficient rendering.
 */
public class Chunk implements Disposable {

    public static final int CHUNK_SIZE = 32;
    public static final int CHUNK_HEIGHT = 128;
    public static final int SECTION_SIZE = 32;
    public static final int CHUNK_SIZE_LG2 = MathUtils.log2(CHUNK_SIZE);
    public static final int CHUNK_SIZE_MINUS_ONE = CHUNK_SIZE - 1;

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

    private final NativeByteArray blockStateIds;

    // Chunk center coordinates
    public final int centerX;
    public final int centerZ;

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

    private final NativeByteArray lightDepths;

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
        this.centerX = chunkX;
        this.centerZ = chunkZ;

        // Create bounding box
        this.aabb = new AABB((float) x0, (float) y0, (float) z0, (float) x1, (float) y1, (float) z1);

        // Initialize blocks array
        this.blockStateIds = new NativeByteArray(CHUNK_SIZE * CHUNK_SIZE * CHUNK_HEIGHT);
        this.lightDepths = new NativeByteArray(CHUNK_SIZE * CHUNK_SIZE);

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

    public BlockState getBlockState(int localX, int localY, int localZ) {
        // check if in bounds
        if (localX < 0 || localY < 0 || localZ < 0 || localX >= CHUNK_SIZE || localY >= CHUNK_HEIGHT || localZ >= CHUNK_SIZE) {
            return null;
        }
        // calculate the index in the blocks array
        int index = (localX * CHUNK_HEIGHT + localY) * CHUNK_SIZE + localZ;
        int blockStateId = blockStateIds.getByte(index);
        return Blocks.globalPalette.fromBlockStateId(blockStateId);
    }

    public boolean setBlockState(int localX, int localY, int localZ, BlockState blockState) {
        // check if in bounds
        if (localX < 0 || localY < 0 || localZ < 0 || localX >= CHUNK_SIZE || localY >= CHUNK_HEIGHT || localZ >= CHUNK_SIZE) {
            return false;
        }
        // calculate the index in the blocks array
        int index = (localX * CHUNK_HEIGHT + localY) * CHUNK_SIZE + localZ;

        int blockStateId = Blocks.globalPalette.getPaletteId(blockState);

        // TODO: REMOVE
        if (blockStateId > 255) {
            throw new IllegalArgumentException("Block state ID exceeds 255: " + blockStateId);
        }

        if (blockStateIds.getByte(index) == blockStateId) {
            return false; // no change
        }

        blockStateIds.setByte(index, (byte) blockStateId);
        return true;
    }

    /**
     * Rebuilds the chunk mesh
     */
    public void rebuild() {
        if (!this.dirty) {
            return;
        }

        ++updates;
        long startTime = System.nanoTime();

        int renderedTiles = 0;

        // Rebuild all dirty sections
        for (ChunkSection section : sections) {
            if (section.isDirty()) {
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
    public int render(GraphicsAPI graphics, Frustum frustum) {
        int numSectionDrawCalls = 0;
        for (ChunkSection section : sections) {
            if (section.hasMesh() && frustum.isVisible(section.getAABB())) {
                numSectionDrawCalls += section.render(graphics);
            }
        }
        return numSectionDrawCalls;
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

        // Rebuild light depth information
        rebuildSkylight();
    }

    /**
     * Marks a specific block position as dirty.
     * Only affects the section containing the block.
     */
    public void setDirtyBlock(int x, int y, int z) {
        for (ChunkSection section : sections) {
            if (section.containsOrAdjacent(x, y, z)) {
                section.setDirty();
            }
        }
        if (!this.dirty) {
            this.dirtiedTime = System.currentTimeMillis();
            this.dirty = true;
        }

        // Rebuild light depth information
        rebuildSkylight();
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
        float xDistance = player.x - this.centerX;
        float zDistance = player.z - this.centerZ;
        return xDistance * xDistance + zDistance * zDistance;
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
        blockStateIds.dispose();
    }

    public void load(byte[] newBlocks) {
        this.blockStateIds.setContents(newBlocks);
        setFullChunkDirty();
    }

    public byte[] getBlockStateIds() {
        return blockStateIds.getAsBytes();

    }

    public boolean isSkyLit(int localX, int y, int localZ) {
        if (localX >= 0 && y >= 0 && localZ >= 0 && localX < CHUNK_SIZE && y < CHUNK_HEIGHT && localZ < CHUNK_SIZE) {
            return y >= this.lightDepths.getByte(localX + localZ * CHUNK_SIZE);
        } else {
            return true;
        }
    }

    private void rebuildSkylight() {
        for (int x = 0; x < CHUNK_SIZE; ++x) {
            for (int z = 0; z < CHUNK_SIZE; ++z) {
                // Find the highest light-blocking block
                int y;
                y = CHUNK_HEIGHT - 1;
                while (y > 0) {
                    BlockState blockState = getBlockState(x, y, z);
                    if (blockState != null && blockState.block.isLightBlocker()) {
                        break;
                    }
                    --y;
                }
                this.lightDepths.setByte(x + z * CHUNK_SIZE, (byte) y);
            }
        }
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

                    // adjacent
                    ((x == x0 - 1 || x == x1) && (y >= y0 && y < y1)) ||
                    (y == y0 - 1 || y == y1) ||
                    ((z == z0 - 1 || z == z1) && (y >= y0 && y < y1));
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

            Tesselator tesselator = Tesselator.instance;
            tesselator.init();

            renderedTiles = 0;
            empty = true;

            // Render all visible tiles in the section
            for (int x = this.x0; x < this.x1; ++x) {
                for (int y = this.y0; y < this.y1; ++y) {
                    for (int z = this.z0; z < this.z1; ++z) {
                        BlockState blockState = level.getBlockState(x, y, z);
                        if (blockState != null) {
                            blockState.block.render(tesselator, level, x, y, z, blockState.facing);
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
        public int render(GraphicsAPI graphics) {
            if (!empty) {
                return chunkMesh.draw(graphics);
            }
            return 0;
        }

        /**
         * Returns whether this section needs to be rebuilt.
         */
        public boolean isDirty() {
            return this.dirty;
        }

        /**
         * Marks this section as dirty, requiring a rebuild.
         */
        public void setDirty() {
            this.dirty = true;
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
