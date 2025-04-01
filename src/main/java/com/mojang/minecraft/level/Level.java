package com.mojang.minecraft.level;

import com.mojang.minecraft.crash.CrashReporter;
import com.mojang.minecraft.entity.Entity;
import com.mojang.minecraft.level.tile.Tile;
import com.mojang.minecraft.phys.AABB;
import com.mojang.minecraft.util.math.RayCaster;
import com.mojang.minecraft.world.HitResult;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Represents the game world/level.
 */
public class Level {
    private static final int TILE_UPDATE_INTERVAL = 400;
    private static final String LEVEL_FILE_NAME = "level.dat";

    public final int width;
    public final int height;
    public final int depth;

    private byte[] blocks;
    private final int[] lightDepths;
    private final List<LevelListener> levelListeners = new ArrayList<>();
    private final Random random = new Random();
    int unprocessed = 0;

    /**
     * Creates a new level with the specified dimensions.
     */
    public Level(int width, int height, int depth) {
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.blocks = new byte[width * height * depth];
        this.lightDepths = new int[width * height];

        boolean mapLoaded = this.load();
        if (!mapLoaded) {
            this.blocks = new LevelGen(width, height, depth).generateMap();
        }

        this.calcLightDepths(0, 0, width, height);
    }

    /**
     * Attempts to load the level from disk.
     *
     * @return true if the level was loaded successfully, false otherwise
     */
    public boolean load() {
        try {
            DataInputStream input = new DataInputStream(
                    new GZIPInputStream(
                            Files.newInputStream(new File(LEVEL_FILE_NAME).toPath())));

            input.readFully(this.blocks);
            this.calcLightDepths(0, 0, this.width, this.height);

            for (LevelListener listener : this.levelListeners) {
                listener.allChanged();
            }

            input.close();
            return true;
        } catch (Exception e) {
            CrashReporter.handleError("Failed to load level from " + LEVEL_FILE_NAME, e);
            return false;
        }
    }

    /**
     * Saves the level to disk.
     */
    public void save() {
        try {
            DataOutputStream output = new DataOutputStream(
                    new GZIPOutputStream(
                            Files.newOutputStream(new File(LEVEL_FILE_NAME).toPath())));

            output.write(this.blocks);
            output.close();
        } catch (Exception e) {
            CrashReporter.handleError("Failed to save level to " + LEVEL_FILE_NAME, e);
        }
    }

    /**
     * Calculates the light depths for the specified region.
     */
    public void calcLightDepths(int x0, int z0, int width, int height) {
        for (int x = x0; x < x0 + width; ++x) {
            for (int z = z0; z < z0 + height; ++z) {
                int oldDepth = this.lightDepths[x + z * this.width];

                // Find the highest light-blocking block
                int y;
                for (y = this.depth - 1; y > 0 && !this.isLightBlocker(x, y, z); --y) {
                }

                this.lightDepths[x + z * this.width] = y;

                // Notify listeners if the light column changed
                if (oldDepth != y) {
                    int minY = Math.min(oldDepth, y);
                    int maxY = Math.max(oldDepth, y);

                    for (LevelListener listener : this.levelListeners) {
                        listener.lightColumnChanged(x, z, minY, maxY);
                    }
                }
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
        Tile tile = Tile.tiles[this.getTile(x, y, z)];
        return tile != null && tile.blocksLight();
    }

    /**
     * Gets all the AABBs inside the specified AABB.
     */
    public ArrayList<AABB> getCubes(AABB box) {
        ArrayList<AABB> cubes = new ArrayList<>();

        int x0 = (int) box.x0;
        int x1 = (int) (box.x1 + 1.0F);
        int y0 = (int) box.y0;
        int y1 = (int) (box.y1 + 1.0F);
        int z0 = (int) box.z0;
        int z1 = (int) (box.z1 + 1.0F);

        // Clamp to level bounds
        x0 = Math.max(0, x0);
        y0 = Math.max(0, y0);
        z0 = Math.max(0, z0);
        x1 = Math.min(this.width, x1);
        y1 = Math.min(this.depth, y1);
        z1 = Math.min(this.height, z1);

        // Check all blocks in the AABB
        for (int x = x0; x < x1; ++x) {
            for (int y = y0; y < y1; ++y) {
                for (int z = z0; z < z1; ++z) {
                    Tile tile = Tile.tiles[this.getTile(x, y, z)];
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
        if (x >= 0 && y >= 0 && z >= 0 && x < this.width && y < this.depth && z < this.height) {
            if (tileId == this.blocks[(y * this.height + z) * this.width + x]) {
                return false;
            } else {
                this.blocks[(y * this.height + z) * this.width + x] = (byte) tileId;
                this.calcLightDepths(x, z, 1, 1);

                for (LevelListener listener : this.levelListeners) {
                    listener.tileChanged(x, y, z);
                }

                return true;
            }
        } else {
            return false;
        }
    }

    /**
     * Checks if a position is lit (above the highest light-blocking block).
     */
    public boolean isLit(int x, int y, int z) {
        if (x >= 0 && y >= 0 && z >= 0 && x < this.width && y < this.depth && z < this.height) {
            return y >= this.lightDepths[x + z * this.width];
        } else {
            return true;
        }
    }

    /**
     * Gets the tile ID at the specified coordinates.
     */
    public int getTile(int x, int y, int z) {
        return x >= 0 && y >= 0 && z >= 0 && x < this.width && y < this.depth && z < this.height
                ? this.blocks[(y * this.height + z) * this.width + x] & 0xFF
                : 0;
    }

    /**
     * Checks if a tile is solid at the specified coordinates.
     */
    public boolean isSolidTile(int x, int y, int z) {
        Tile tile = Tile.tiles[this.getTile(x, y, z)];
        return tile != null && tile.isSolid();
    }

    /**
     * Updates the level, ticking random tiles.
     */
    public void tick() {
        this.unprocessed += this.width * this.height * this.depth;
        int ticks = this.unprocessed / TILE_UPDATE_INTERVAL;
        this.unprocessed -= ticks * TILE_UPDATE_INTERVAL;

        for (int i = 0; i < ticks; ++i) {
            int x = this.random.nextInt(this.width);
            int y = this.random.nextInt(this.depth);
            int z = this.random.nextInt(this.height);

            Tile tile = Tile.tiles[this.getTile(x, y, z)];
            if (tile != null) {
                tile.tick(this, x, y, z, this.random);
            }
        }
    }

    public HitResult raycast(Entity entity, float partialTick) {
        return RayCaster.raycast(entity, this, partialTick);
    }
}
