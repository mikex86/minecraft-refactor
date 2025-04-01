package com.mojang.minecraft;

/**
 * Represents the result of a ray intersection with an object in the world.
 */
public class HitResult {
    /**
     * Type of hit (block or entity)
     */
    public int type;
    /**
     * X coordinate of the hit
     */
    public int x;
    /**
     * Y coordinate of the hit
     */
    public int y;
    /**
     * Z coordinate of the hit
     */
    public int z;
    /**
     * Direction of the hit (face index)
     */
    public int face;

    /**
     * Creates a new hit result.
     *
     * @param type The type of hit (0 for miss, 1 for block, etc.)
     * @param x    X coordinate of the hit
     * @param y    Y coordinate of the hit
     * @param z    Z coordinate of the hit
     * @param face Direction of the hit
     */
    public HitResult(int type, int x, int y, int z, int face) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.z = z;
        this.face = face;
    }
}
