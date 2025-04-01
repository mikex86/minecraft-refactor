package com.mojang.minecraft.world;

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
     * The exact X position of the hit
     */
    public float hitX;

    /**
     * The exact Y position of the hit
     */
    public float hitY;

    /**
     * The exact Z position of the hit
     */
    public float hitZ;

    /**
     * Distance from the ray origin to the hit point
     */
    public float distanceSq;

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
        this.hitX = x + 0.5f;
        this.hitY = y + 0.5f;
        this.hitZ = z + 0.5f;
        this.distanceSq = 0.0f;
    }

    /**
     * Creates a new hit result with exact hit coordinates.
     *
     * @param type       The type of hit (0 for miss, 1 for block, etc.)
     * @param x          X coordinate of the hit block
     * @param y          Y coordinate of the hit block
     * @param z          Z coordinate of the hit block
     * @param face       Direction of the hit
     * @param hitX       Exact X coordinate of the hit
     * @param hitY       Exact Y coordinate of the hit
     * @param hitZ       Exact Z coordinate of the hit
     * @param distanceSq Squared distance to the hit
     */
    public HitResult(int type, int x, int y, int z, int face, float hitX, float hitY, float hitZ, float distanceSq) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.z = z;
        this.face = face;
        this.hitX = hitX;
        this.hitY = hitY;
        this.hitZ = hitZ;
        this.distanceSq = distanceSq;
    }
} 