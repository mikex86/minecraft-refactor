package com.mojang.minecraft.level.chunk;

import com.mojang.minecraft.entity.EntityPlayer;
import com.mojang.minecraft.level.Level;
import com.mojang.minecraft.level.block.Blocks;
import com.mojang.minecraft.level.block.state.BlockState;
import com.mojang.minecraft.optim.pools.ChunkBuildTesselatorPool;
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
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Represents a chunk of the world that can be rendered independently.
 * Uses cubic sections (16x16x16) for more efficient rendering.
 */
public class Chunk implements Disposable {

    public static final int CHUNK_SIZE = 32;
    public static final int CHUNK_HEIGHT = 128;
    public static final int SECTION_HEIGHT = 32;
    public static final int CHUNK_SIZE_LG2 = MathUtils.log2(CHUNK_SIZE);
    public static final int CHUNK_SIZE_MINUS_ONE = CHUNK_SIZE - 1;
    public static final int CHUNK_SECTION_COUNT = CHUNK_HEIGHT / SECTION_HEIGHT;

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
    public final int centerX;
    public final int centerZ;

    // Chunk sections
    private final List<ChunkSection> sections = new ArrayList<>();

    // Mutex for thread-safe access to chunk data
    public final ReadWriteLock dataMutex = new ReentrantReadWriteLock();

    // Status tracking
    private boolean dirty = true;
    private boolean rebuildScheduled = false;
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

    private final NativeByteArray skyLightDepths;

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

        this.skyLightDepths = new NativeByteArray(CHUNK_SIZE * CHUNK_SIZE);

        // Initialize sections
        initSections();
    }

    /**
     * Initializes the chunk sections based on chunk dimensions.
     */
    private void initSections() {
        int ySections = (int) Math.ceil(CHUNK_HEIGHT / (float) SECTION_HEIGHT);

        for (int sy = 0; sy < ySections; sy++) {
            int sectionY0 = y0 + sy * SECTION_HEIGHT;
            int sectionY1 = Math.min(sectionY0 + SECTION_HEIGHT, y1);
            sections.add(new ChunkSection(x0, sectionY0, z0, x1, sectionY1, z1));
        }
    }

    public BlockState getBlockState(int localX, int localY, int localZ) {
        // check if in bounds
        if (localX < 0 || localY < 0 || localZ < 0 || localX >= CHUNK_SIZE || localY >= CHUNK_HEIGHT || localZ >= CHUNK_SIZE) {
            return null;
        }
        // get the section for this block
        int sectionIndex = localY / SECTION_HEIGHT;
        ChunkSection section = sections.get(sectionIndex);
        localY = localY % SECTION_HEIGHT;
        int blockStateId = section.getBlockStateId(localX, localY, localZ);
        return Blocks.globalPalette.fromBlockStateId(blockStateId);
    }

    public boolean setBlockState(int localX, int localY, int localZ, BlockState blockState) {
        // check if in bounds
        if (localX < 0 || localY < 0 || localZ < 0 || localX >= CHUNK_SIZE || localY >= CHUNK_HEIGHT || localZ >= CHUNK_SIZE) {
            return false;
        }

        int blockStateId = Blocks.globalPalette.getPaletteId(blockState);

        // TODO: REMOVE
        if (blockStateId > 255) {
            throw new IllegalArgumentException("Block state ID exceeds 255: " + blockStateId);
        }

        // get the section for this block
        int sectionIndex = localY / SECTION_HEIGHT;

        ChunkSection section = sections.get(sectionIndex);
        localY = localY % SECTION_HEIGHT;

        if (section.getBlockStateId(localX, localY, localZ) == blockStateId) {
            return false; // no change
        }

        // set the block state ID
        section.setBlockStateId(localX, localY, localZ, blockStateId);
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
    @SuppressWarnings("ForLoopReplaceableByForEach")
    public int render(GraphicsAPI graphics, Frustum frustum) {
        int numSectionDrawCalls = 0;
        for (int i = 0, sectionsSize = sections.size(); i < sectionsSize; i++) {
            ChunkSection section = sections.get(i);
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

        // Rebuild light depth information
        rebuildSkylight();

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
            }
        }
        if (!this.dirty) {
            this.dirtiedTime = System.currentTimeMillis();
            this.dirty = true;
        }

        // Rebuild light depth information
        if (rebuildSkylight()) {
            setFullChunkDirty();
        }
    }

    /**
     * Returns whether the chunk needs to be rebuilt.
     */
    public boolean isDirty() {
        return this.dirty;
    }

    public boolean isRebuildScheduled() {
        return rebuildScheduled;
    }

    public void setRebuildScheduled(boolean rebuildScheduled) {
        this.rebuildScheduled = rebuildScheduled;
    }

    /**
     * Calculates the squared distance from this chunk to the player.
     */
    public float distanceToSqr(EntityPlayer player) {
        float xDistance = player.x - this.centerX;
        float zDistance = player.z - this.centerZ;
        return (xDistance * xDistance) + (zDistance * zDistance);
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

    public void load(int section, byte[] newBlocks) {
        try {
            dataMutex.writeLock().lock();
            sections.get(section).setContents(newBlocks);
            setFullChunkDirty();
        } finally {
            dataMutex.writeLock().unlock();
        }
    }

    public byte[] getBlockStateIds(int section) {
        byte[] blockStateIdsCopy;
        try {
            dataMutex.readLock().lock();
            byte[] bytes = sections.get(section).getAsBytes();
            if (bytes == null) {
                return null;
            }
            blockStateIdsCopy = new byte[bytes.length];
            System.arraycopy(bytes, 0, blockStateIdsCopy, 0, bytes.length);
        } finally {
            dataMutex.readLock().unlock();
        }
        return blockStateIdsCopy;
    }

    public boolean isSkyLit(int localX, int y, int localZ) {
        if (localX >= 0 && y >= 0 && localZ >= 0 && localX < CHUNK_SIZE && y < CHUNK_HEIGHT && localZ < CHUNK_SIZE) {
            return y >= this.skyLightDepths.getByte(localX + localZ * CHUNK_SIZE);
        } else {
            return true;
        }
    }

    private boolean rebuildSkylight() {
        boolean changed = false;
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
                int oldDepth = this.skyLightDepths.getByte(x + z * CHUNK_SIZE);
                if (oldDepth != y) {
                    changed = true;
                }
                this.skyLightDepths.setByte(x + z * CHUNK_SIZE, (byte) y);
            }
        }
        return changed;
    }

    public void uploadPendingMeshes() {
        for (ChunkSection section : sections) {
            section.uploadPendingSection();
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
        private volatile boolean pendingUpload = false;
        private final ChunkMesh chunkMesh;
        private int renderedTiles = 0;
        private boolean empty = true;


        private NativeByteArray blockStateIds;

        private final Object uploadMutex = new Object();


        /**
         * Current tesselator in use for chunk rebuilding.
         * This is only non-null if there is a pending upload.
         * This will be cleared once the mesh has been uploaded.
         * The tesselator stores the mesh data until it is uploaded.
         */
        private Tesselator currentTesselator = null;

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

            // Don't allocate the array until we need it
            this.blockStateIds = null;

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
         * Gets the block state ID at the specified local coordinates.
         * NOTE: This does not perform bounds checking.
         * @param localX the local x coordinate
         * @param localY the local y coordinate
         * @param localZ the local z coordinate
         * @return the block state ID
         */
        int getBlockStateId(int localX, int localY, int localZ) {
            if (blockStateIds == null) {
                return 0; // empty section
            }
            int index = localX + (localY * CHUNK_SIZE) + (localZ * CHUNK_SIZE * CHUNK_SIZE);
            return blockStateIds.getByte(index) & 0xFF;
        }

        private void ensureBlockStatesAllocated() {
            if (this.blockStateIds != null) {
                return;
            }
            this.blockStateIds = new NativeByteArray(CHUNK_SIZE * CHUNK_SIZE * SECTION_HEIGHT);
        }

        /**
         * Sets the block state ID at the specified local coordinates.
         * NOTE: This does not perform bounds checking.
         * @param localX the local x coordinate
         * @param localY the local y coordinate
         * @param localZ the local z coordinate
         * @param blockStateId the block state ID
         */
        void setBlockStateId(int localX, int localY, int localZ, int blockStateId) {
            ensureBlockStatesAllocated();
            int index = localX + (localY * CHUNK_SIZE) + (localZ * CHUNK_SIZE * CHUNK_SIZE);
            this.blockStateIds.setByte(index, (byte) blockStateId);
            this.empty = false;
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
            if (this.empty) {
                return; // no need to rebuild empty sections
            }

            this.currentTesselator = ChunkBuildTesselatorPool.obtain();
            this.currentTesselator.init();

            this.renderedTiles = 0;

            // Render all visible tiles in the section
            for (int x = this.x0; x < this.x1; ++x) {
                for (int y = this.y0; y < this.y1; ++y) {
                    for (int z = this.z0; z < this.z1; ++z) {
                        BlockState blockState = level.getBlockState(x, y, z);
                        if (blockState != null) {
                            blockState.block.render(this.currentTesselator, level, x, y, z, blockState.facing);
                            ++this.renderedTiles;
                        }
                    }
                }
            }

            this.pendingUpload = true;
            this.dirty = false;
        }

        public void uploadPendingSection() {
            assert this.currentTesselator != null;
            if (this.pendingUpload) {
                synchronized (this.uploadMutex) {
                    this.chunkMesh.upload(this.currentTesselator);
                    this.pendingUpload = false;
                }
            }
            if (this.currentTesselator != null) {
                ChunkBuildTesselatorPool.release(this.currentTesselator);
                this.currentTesselator = null;
            }
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
            if (currentTesselator != null) {
                ChunkBuildTesselatorPool.release(currentTesselator);
                currentTesselator = null;
            }
            chunkMesh.dispose();
        }

        public void setContents(byte[] newBlocks) {
            if (blockStateIds == null) {
                ensureBlockStatesAllocated();
            }
            blockStateIds.setContents(newBlocks);
            empty = false;
            setDirty();
        }

        public byte[] getAsBytes() {
            if (blockStateIds != null) {
                return blockStateIds.getAsBytes();
            }
            return null;
        }
    }
}
