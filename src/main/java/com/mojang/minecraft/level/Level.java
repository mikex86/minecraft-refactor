package com.mojang.minecraft.level;

import com.mojang.minecraft.crash.CrashReporter;
import com.mojang.minecraft.entity.Entity;
import com.mojang.minecraft.level.block.state.BlockState;
import com.mojang.minecraft.level.generation.WorldGenerator;
import com.mojang.minecraft.level.save.LevelLoader;
import com.mojang.minecraft.level.save.LevelSaver;
import com.mojang.minecraft.phys.AABB;
import com.mojang.minecraft.util.LongHashMap;
import com.mojang.minecraft.util.math.RayCaster;
import com.mojang.minecraft.world.HitResult;
import jdk.internal.vm.annotation.ForceInline;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.mojang.minecraft.util.math.MathUtils.ceilFloor;

/**
 * Represents the game world/level.
 */
public class Level {
    private static final int TILE_UPDATE_INTERVAL = 400;
    private static final String LEVEL_FILE_NAME = "world";

    private final List<LevelListener> levelListeners = new ArrayList<>();
    private final Random random = new Random();

    private final LongHashMap<Chunk> chunkMap = new LongHashMap<>();
    private final List<Chunk> fullyLoadedChunks = new ArrayList<>();
    private final Object chunkLoadMutex = new Object();

    private final LevelSaver levelSaver = new LevelSaver(new File(LEVEL_FILE_NAME));
    private final LevelLoader levelLoader = new LevelLoader(new File(LEVEL_FILE_NAME), levelSaver.getSavingLevelMutex());

    private final int seed = 42; // TODO: Make seeds configurable

    /**
     * Creates a new level with the specified dimensions.
     */
    public Level() {
    }

    /**
     * Saves the level to disk.
     */
    public void save() {
        batchUnloadChunks(this.fullyLoadedChunks, true);
    }


    /**
     * Unloads the specified chunks and saves them to disk.
     *
     * @param chunks   The chunks to unload
     * @param blocking Whether to block until the chunks are saved
     */
    public void batchUnloadChunks(Iterable<Chunk> chunks, boolean blocking) {
        // create a copy of chunks
        List<Chunk> chunkList = new ArrayList<>();
        for (Chunk chunk : chunks) {
            chunkList.add(chunk);
        }
        if (chunkList.isEmpty()) {
            return;
        }
        levelSaver.saveChunks(chunkList, blocking);

        synchronized (this.chunkLoadMutex) {
            for (Chunk chunk : chunkList) {
                this.chunkMap.remove(makeChunkKey(chunk.x0 >> Chunk.CHUNK_SIZE_LG2, chunk.z0 >> Chunk.CHUNK_SIZE_LG2));
                this.fullyLoadedChunks.remove(chunk);
            }
        }
    }

    /**
     * Adds a level listener.
     */
    public void addListener(LevelListener levelListener) {
        this.levelListeners.add(levelListener);
    }

    /**
     * Removes a level listener.
     */
    public void removeListener(LevelListener levelListener) {
        this.levelListeners.remove(levelListener);
    }

    /**
     * Gets all the AABBs inside the specified AABB.
     */
    public List<AABB> getCubes(AABB box) {
        List<AABB> cubes = new ArrayList<>();

        int x0 = ceilFloor(box.x0);
        int x1 = ceilFloor(box.x1 + 1.0F);
        int y0 = ceilFloor(box.y0);
        int y1 = ceilFloor(box.y1 + 1.0F);
        int z0 = ceilFloor(box.z0);
        int z1 = ceilFloor(box.z1 + 1.0F);

        // Check all blocks in the AABB
        for (int x = x0; x < x1; ++x) {
            for (int y = y0; y < y1; ++y) {
                for (int z = z0; z < z1; ++z) {
                    BlockState blockState = this.getBlockState(x, y, z);
                    if (blockState != null) {
                        AABB tileBox = blockState.block.getAABB(x, y, z);
                        if (tileBox != null) {
                            cubes.add(tileBox);
                        }
                    }
                }
            }
        }
        return cubes;
    }

    /**
     * Sets a block state at the specified coordinates.
     *
     * @return true if the block state was changed, false if it was already the same or out of bounds
     */
    public boolean setBlockState(int x, int y, int z, BlockState blockState) {
        Chunk chunk = getChunk(x, z);
        if (chunk == null) {
            return false;
        }
        int localX = x & (Chunk.CHUNK_SIZE - 1);
        int localZ = z & (Chunk.CHUNK_SIZE - 1);
        if (chunk.setBlockState(localX, y, localZ, blockState)) {
            List<Chunk> toRebuild = new ArrayList<>();

            // determine chunks to rebuild
            {
                toRebuild.add(chunk);
                if (localX == 0) {
                    Chunk westChunk = getChunk(x - 1, z);
                    if (westChunk != null) {
                        toRebuild.add(westChunk);
                    }
                }
                if (localX == Chunk.CHUNK_SIZE - 1) {
                    Chunk eastChunk = getChunk(x + 1, z);
                    if (eastChunk != null) {
                        toRebuild.add(eastChunk);
                    }
                }
                if (localZ == 0) {
                    Chunk northChunk = getChunk(x, z - 1);
                    if (northChunk != null) {
                        toRebuild.add(northChunk);
                    }
                }
                if (localZ == Chunk.CHUNK_SIZE - 1) {
                    Chunk southChunk = getChunk(x, z + 1);
                    if (southChunk != null) {
                        toRebuild.add(southChunk);
                    }
                }
                for (Chunk c : toRebuild) {
                    c.setDirtyBlock(x, y, z);
                }
            }

            for (LevelListener listener : this.levelListeners) {
                listener.tileChanged(x, y, z);
            }
            return true;
        }
        return false;
    }

    /**
     * Checks if a position is lit (above the highest light-blocking block).
     */
    public boolean isLit(int x, int y, int z) {
        Chunk chunk = getChunk(x, z);
        if (chunk == null) {
            return false;
        }
        return chunk.isSkyLit(x & Chunk.CHUNK_SIZE_MINUS_ONE, y, z & Chunk.CHUNK_SIZE_MINUS_ONE);
    }

    /**
     * Gets the block at the specified coordinates.
     */
    @ForceInline
    public BlockState getBlockState(int x, int y, int z) {
        Chunk chunk = getChunk(x, z);
        if (chunk == null) {
            return null;
        }
        return chunk.getBlockState(x & Chunk.CHUNK_SIZE_MINUS_ONE, y, z & Chunk.CHUNK_SIZE_MINUS_ONE);
    }

    private long lastChunkKey = 0;
    private Chunk lastChunk = null;

    @ForceInline
    public Chunk getChunk(int x, int z) {
        int cx = x >> Chunk.CHUNK_SIZE_LG2;
        int cz = z >> Chunk.CHUNK_SIZE_LG2;
        long chunkKey = makeChunkKey(cx, cz);
        if (chunkKey == lastChunkKey) {
            return lastChunk;
        }
        synchronized (this.chunkLoadMutex) {
            Chunk chunk = this.chunkMap.get(chunkKey);
            lastChunk = chunk;
            lastChunkKey = chunkKey;
            return chunk;
        }
    }

    private static long makeChunkKey(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    /**
     * Checks if a tile is solid at the specified coordinates.
     */
    @ForceInline
    public boolean isSolidTile(int x, int y, int z) {
        BlockState blockState = this.getBlockState(x, y, z);
        return blockState != null && blockState.block.isSolid() && !blockState.block.isTransparent();
    }

    /**
     * Updates the level, ticking random tiles.
     */
    public void tick() {
        // TODO
    }

    public HitResult raycast(Entity entity, float partialTick) {
        return RayCaster.raycast(entity, this, partialTick);
    }

    /**
     * Temporary variable returned by {@link #getLoadedChunks()} to avoid allocating a new list each time.
     * Serves as a thread-safe copy of {@link #fullyLoadedChunks} for iteration.
     */
    private final ArrayList<Chunk> loadedChunksTmp = new ArrayList<>();

    public Iterable<Chunk> getLoadedChunks() {
        loadedChunksTmp.clear();
        loadedChunksTmp.ensureCapacity(fullyLoadedChunks.size());
        synchronized (this.chunkLoadMutex) {
            // we don't use addAll here because it allocates too much memory
            for (Chunk fullyLoadedChunk : fullyLoadedChunks) {
                //noinspection UseBulkOperation
                loadedChunksTmp.add(fullyLoadedChunk);
            }
        }
        // return a copy of fullyLoadedChunks
        return loadedChunksTmp;
    }

    public boolean isChunkLoaded(int chunkX, int chunkZ) {
        long chunkKey = makeChunkKey(chunkX, chunkZ);
        synchronized (this.chunkLoadMutex) {
            return this.chunkMap.containsKey(chunkKey); // may be null, indicating loading
        }
    }

    public void loadChunk(int chunkX, int chunkZ) {
        if (isChunkLoaded(chunkX, chunkZ)) {
            return;
        }
        Chunk chunk = new Chunk(this, chunkX, chunkZ);

        long chunkKey = makeChunkKey(chunkX, chunkZ);

        // we put null into the loaded chunk map to indicate that the chunk is loading
        // It will be considered "loaded", but no chunk is returned when queried at the coordinates.
        synchronized (this.chunkLoadMutex) {
            this.chunkMap.put(chunkKey, null);
        }

        // asynchronously load the data for the chunk / generate it if it doesn't exist
        levelLoader.load(chunk, loaded -> {
            if (!loaded) {
                try {
                    generateChunk(chunk);
                } catch (Exception e) {
                    CrashReporter.logException("Failed to generate chunk", e);
                }
            }
            synchronized (this.chunkLoadMutex) {
                this.chunkMap.put(chunkKey, chunk); // publish the real chunk
                this.fullyLoadedChunks.add(chunk); // add to the list of loaded chunks
            }
            finalizeChunkLoad(chunk);
        });
    }

    private void finalizeChunkLoad(Chunk chunk) {
        List<Chunk> toRebuild = new ArrayList<>();
        int x = chunk.x0;
        int z = chunk.z0;
        {
            toRebuild.add(chunk);
            Chunk westChunk = getChunk(x - Chunk.CHUNK_SIZE, z);
            if (westChunk != null) {
                toRebuild.add(westChunk);
            }

            Chunk eastChunk = getChunk(x + Chunk.CHUNK_SIZE, z);
            if (eastChunk != null) {
                toRebuild.add(eastChunk);
            }

            Chunk northChunk = getChunk(x, z - Chunk.CHUNK_SIZE);
            if (northChunk != null) {
                toRebuild.add(northChunk);
            }

            Chunk southChunk = getChunk(x, z + Chunk.CHUNK_SIZE);
            if (southChunk != null) {
                toRebuild.add(southChunk);
            }

            for (Chunk c : toRebuild) {
                c.setFullChunkDirty();
            }
        }
    }

    private final WorldGenerator worldGenerator = new WorldGenerator(seed);

    private void generateChunk(Chunk chunk) {
        worldGenerator.generate(chunk);
    }
}
