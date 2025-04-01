package com.mojang.minecraft.character;

/**
 * Represents a 3D vector with x, y, and z components.
 */
public class Vec3 {
    public float x;
    public float y;
    public float z;

    /**
     * Creates a new 3D vector with the given coordinates.
     *
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     */
    public Vec3(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /**
     * Linearly interpolates between this vector and the target vector.
     *
     * @param target The target vector to interpolate towards
     * @param progress The interpolation factor (0.0 = this vector, 1.0 = target vector)
     * @return A new vector that is the interpolated result
     */
    public Vec3 interpolateTo(Vec3 target, float progress) {
        float interpolatedX = this.x + (target.x - this.x) * progress;
        float interpolatedY = this.y + (target.y - this.y) * progress;
        float interpolatedZ = this.z + (target.z - this.z) * progress;
        return new Vec3(interpolatedX, interpolatedY, interpolatedZ);
    }

    /**
     * Sets the components of this vector.
     *
     * @param x New X coordinate
     * @param y New Y coordinate
     * @param z New Z coordinate
     */
    public void set(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }
}
