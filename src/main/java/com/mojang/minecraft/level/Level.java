package com.mojang.minecraft.level;

import com.mojang.minecraft.entity.Entity;
import com.mojang.minecraft.level.save.LevelLoader;
import com.mojang.minecraft.level.save.LevelSaver;
import com.mojang.minecraft.level.tile.Tile;
import com.mojang.minecraft.phys.AABB;
import com.mojang.minecraft.util.math.RayCaster;
import com.mojang.minecraft.world.HitResult;

import java.io.File;
import java.util.*;

import static com.mojang.minecraft.util.math.MathUtils.ceilFloor;

/**
 * Represents the game world/level.
 */
public class Level {
    private static final int TILE_UPDATE_INTERVAL = 400;
    private static final String LEVEL_FILE_NAME = "world";

    private final List<LevelListener> levelListeners = new ArrayList<>();
    private final Random random = new Random();

    private final Map<Long, Chunk> chunkMap = new HashMap<>();
    private final List<Chunk> fullyLoadedChunks = new ArrayList<>();
    private final Object chunkLoadMutex = new Object();

    private final LevelSaver levelSaver = new LevelSaver(new File(LEVEL_FILE_NAME));
    private final LevelLoader levelLoader = new LevelLoader(new File(LEVEL_FILE_NAME), levelSaver.getSavingLevelMutex());

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

        for (Chunk chunk : chunkList) {
            synchronized (this.chunkLoadMutex) {
                this.chunkMap.remove(makeChunkKey(chunk.x, chunk.z));
                this.fullyLoadedChunks.remove(chunk);
            }
        }
    }

    /**
     * Unloads a chunk at the specified coordinates.
     *
     * @param chunkX the X coordinate of the chunk
     * @param chunkZ the Z coordinate of the chunk
     */
    public void unloadChunk(int chunkX, int chunkZ) {
        long chunkKey = makeChunkKey(chunkX, chunkZ);
        synchronized (this.chunkLoadMutex) {
            Chunk chunk = this.chunkMap.remove(chunkKey);
            if (chunk != null) {
                batchUnloadChunks(Collections.singleton(chunk), false);
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
     * Checks if a block blocks light at the specified coordinates.
     */
    public boolean isLightBlocker(int x, int y, int z) {
        int tileId = this.getTile(x, y, z);
        Tile tile = Tile.getTileById(tileId);
        return tile != null && tile.blocksLight();
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
                    int tileId = this.getTile(x, y, z);
                    Tile tile = Tile.getTileById(tileId);
                    if (tile != null) {
                        AABB tileBox = tile.getAABB(x, y, z);
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
     * Sets a tile at the specified coordinates.
     *
     * @return true if the tile was changed, false if it was already the same or out of bounds
     */
    public boolean setTile(int x, int y, int z, int tileId) {
        Chunk chunk = getChunk(x, z);
        if (chunk == null) {
            return false;
        }
        int localX = x & 15;
        int localZ = z & 15;
        if (chunk.setTile(localX, y, localZ, tileId)) {
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
        // TODO
        return true;
    }

    /**
     * Gets the tile ID at the specified coordinates.
     */
    public int getTile(int x, int y, int z) {
        Chunk chunk = getChunk(x, z);
        if (chunk == null) {
            return 0;
        }
        return chunk.getTile(x & 15, y, z & 15);
    }

    public Chunk getChunk(int x, int z) {
        int cx = x >> 4;
        int cz = z >> 4;
        long chunkKey = makeChunkKey(cx, cz);
        return this.chunkMap.get(chunkKey);
    }

    private static long makeChunkKey(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    /**
     * Checks if a tile is solid at the specified coordinates.
     */
    public boolean isSolidTile(int x, int y, int z) {
        int tileId = this.getTile(x, y, z);
        Tile tile = Tile.getTileById(tileId);
        return tile != null && tile.isSolid();
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
    private final List<Chunk> loadedChunksTmp = new ArrayList<>();

    public Iterable<Chunk> getLoadedChunks() {
        loadedChunksTmp.clear();
        synchronized (this.chunkLoadMutex) {
            // return a copy of the chunk map
            loadedChunksTmp.addAll(fullyLoadedChunks);
        }
        return loadedChunksTmp;
    }

    public boolean isChunkLoaded(int chunkX, int chunkZ) {
        long chunkKey = makeChunkKey(chunkX, chunkZ);
        return this.chunkMap.containsKey(chunkKey); // may be null, indicating loading
    }

    public void loadChunk(int chunkX, int chunkZ) {
        if (isChunkLoaded(chunkX, chunkZ)) {
            return;
        }
        Chunk chunk = new Chunk(this, chunkX, chunkZ);

        long chunkKey = makeChunkKey(chunkX, chunkZ);

        // asynchronously load the data for the chunk / generate it if it doesn't exist
        levelLoader.load(chunk, success -> {
            if (!success) { // chunk doesn't exist yet, generate it
                basicWorldGen(chunk);
            } else {
                finalizeChunkLoad(chunk);
            }
            synchronized (this.chunkLoadMutex) {
                this.chunkMap.put(chunkKey, chunk); // publish the real chunk
                this.fullyLoadedChunks.add(chunk); // add to the list of loaded chunks
            }
        });

        // we put null into the loaded chunk map to indicate that the chunk is loading
        // It will be considered "loaded", but no chunk is returned when queried at the coordinates.
        synchronized (this.chunkLoadMutex) {
            this.chunkMap.put(chunkKey, null);
        }
    }

    private void finalizeChunkLoad(Chunk chunk) {
        int chunkX = chunk.x;
        int chunkZ = chunk.z;

        // all neighboring chunks that exist need to be rebuilt
        for (int x = -1; x <= 1; ++x) {
            for (int z = -1; z <= 1; ++z) {
                if (x == 0 && z == 0) {
                    continue;
                }
                Chunk neighborChunk = getChunk(chunkX + x, chunkZ + z);
                if (neighborChunk != null) {
                    neighborChunk.setFullChunkDirty();
                }
            }
        }
    }

    private void basicWorldGen(Chunk chunk) {
        // TODO: REMOVE
        // basic chunk generation
        for (int x = 0; x < Chunk.CHUNK_SIZE; ++x) {
            for (int z = 0; z < Chunk.CHUNK_SIZE; ++z) {
                // set stone
                for (int y = 0; y < 5; ++y) {
                    chunk.setTile(x, y, z, Tile.rock.id);
                }
                // set dirt
                for (int y = 5; y < 10; ++y) {
                    chunk.setTile(x, y, z, Tile.dirt.id);
                }
                // set grass
                chunk.setTile(x, 10, z, Tile.grass.id);
            }
        }
    }
}
