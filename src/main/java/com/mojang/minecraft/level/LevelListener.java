package com.mojang.minecraft.level;

/**
 * Interface for objects that need to be notified about changes in the level.
 */
public interface LevelListener {
    /**
     * Called when a tile has changed at the specified coordinates.
     *
     * @param x The x-coordinate of the changed tile
     * @param y The y-coordinate of the changed tile
     * @param z The z-coordinate of the changed tile
     */
    void tileChanged(int x, int y, int z);

}
