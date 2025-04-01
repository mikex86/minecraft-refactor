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

    /**
     * Called when the light column has changed in a vertical range.
     *
     * @param x    The x-coordinate of the light column
     * @param z    The z-coordinate of the light column
     * @param oldY The old height value
     * @param newY The new height value
     */
    void lightColumnChanged(int x, int z, int oldY, int newY);

    /**
     * Called when the entire level has changed and needs to be redrawn.
     */
    void allChanged();
}
