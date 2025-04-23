package com.mojang.minecraft.world;

import com.mojang.minecraft.level.block.EnumFacing;

/**
 * Represents the result of a ray intersection with an object in the world.
 */
public class HitResult {
    /**
     * Type of hit (block or entity)
     */
    public final int type;
    /**
     * X coordinate of the hit
     */
    public final int x;
    /**
     * Y coordinate of the hit
     */
    public final int y;
    /**
     * Z coordinate of the hit
     */
    public final int z;
    /**
     * Direction of the hit (face index)
     */
    public final int face;

    /**
     * The exact X position of the hit
     */
    public final float hitX;

    /**
     * The exact Y position of the hit
     */
    public final float hitY;

    /**
     * The exact Z position of the hit
     */
    public final float hitZ;

    /**
     * Distance from the ray origin to the hit point
     */
    public final float distanceSq;

    /**
     * The direction of the hit (facing direction)
     */
    public final EnumFacing facingDirection;

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
        this.facingDirection = fromFace(face);
        this.hitX = hitX;
        this.hitY = hitY;
        this.hitZ = hitZ;
        this.distanceSq = distanceSq;
    }

    private EnumFacing fromFace(int face) {
        switch (face) {
            case 0:
                return EnumFacing.DOWN;
            case 1:
                return EnumFacing.UP;
            case 2:
                return EnumFacing.SOUTH;
            case 3:
                return EnumFacing.NORTH;
            case 4:
                return EnumFacing.EAST;
            case 5:
                return EnumFacing.WEST;
            default:
                throw new IllegalArgumentException("Invalid face index: " + face);
        }
    }
} 