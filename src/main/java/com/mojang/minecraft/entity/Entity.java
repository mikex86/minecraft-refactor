package com.mojang.minecraft.entity;

import com.mojang.minecraft.level.Level;
import com.mojang.minecraft.phys.AABB;
import com.mojang.minecraft.renderer.graphics.GraphicsAPI;
import com.mojang.minecraft.util.math.CollisionUtils;

import java.util.List;

/**
 * Base class for all entities in the game.
 * Handles basic physics, movement, and collision detection.
 */
public class Entity {
    /**
     * The level this entity exists in
     */
    protected Level level;

    /**
     * Previous x position for interpolation
     */
    public float xo;
    /**
     * Previous y position for interpolation
     */
    public float yo;
    /**
     * Previous z position for interpolation
     */
    public float zo;

    /**
     * Current x position
     */
    public float x;
    /**
     * Current y position
     */
    public float y;
    /**
     * Current z position
     */
    public float z;

    /**
     * Current x velocity
     */
    public float xd;
    /**
     * Current y velocity
     */
    public float yd;
    /**
     * Current z velocity
     */
    public float zd;

    /**
     * Rotation around y axis (yaw) in degrees
     */
    public float yRot;
    /**
     * Rotation around x axis (pitch) in degrees
     */
    public float xRot;

    /**
     * Collision bounding box
     */
    public AABB bb;

    /**
     * Whether the entity is on the ground
     */
    public boolean onGround = false;
    /**
     * Whether the entity has been removed
     */
    public boolean removed = false;

    /**
     * Vertical offset for camera and rendering
     */
    protected float heightOffset = 0.0F;
    /**
     * Width of bounding box
     */
    protected float bbWidth = 0.6F;
    /**
     * Height of bounding box
     */
    protected float bbHeight = 1.8F;

    /**
     * Creates a new entity in the specified level.
     *
     * @param level The level this entity belongs to
     */
    public Entity(Level level) {
        this.level = level;
        this.resetPos();
    }

    /**
     * Resets the entity's position to a random location in the level.
     */
    public void resetPos() {
        float x = (float) Math.random() * (float) this.level.width;
        float y = (float) (this.level.depth + 10);
        float z = (float) Math.random() * (float) this.level.height;
        this.setPos(x, y, z);
    }

    /**
     * Marks the entity for removal.
     */
    public void remove() {
        this.removed = true;
    }

    /**
     * Gets the entity's eye height offset.
     *
     * @return The entity's height offset
     */
    public float getHeightOffset() {
        return this.heightOffset;
    }

    /**
     * Sets the size of the entity's bounding box.
     *
     * @param width  Width of the entity
     * @param height Height of the entity
     */
    protected void setSize(float width, float height) {
        this.bbWidth = width;
        this.bbHeight = height;
    }

    /**
     * Sets the entity's position and updates its bounding box.
     *
     * @param x New x position
     * @param y New y position
     * @param z New z position
     */
    protected void setPos(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
        float halfWidth = this.bbWidth / 2.0F;
        float halfHeight = this.bbHeight / 2.0F;
        this.bb = new AABB(x - halfWidth, y - halfHeight, z - halfWidth,
                x + halfWidth, y + halfHeight, z + halfWidth);
    }

    /**
     * Rotates the entity.
     *
     * @param yawRotation   Change in yaw rotation
     * @param pitchRotation Change in pitch rotation
     */
    public void turn(float yawRotation, float pitchRotation) {
        this.yRot = (float) ((double) this.yRot + (double) yawRotation * 0.15);
        this.xRot = (float) ((double) this.xRot + (double) pitchRotation * 0.15);

        // Clamp pitch to prevent camera flipping
        if (this.xRot < -90.0F) {
            this.xRot = -90.0F;
        }
        if (this.xRot > 90.0F) {
            this.xRot = 90.0F;
        }
    }

    /**
     * Called every game tick to update the entity.
     * Override this in subclasses to add specific behavior.
     */
    public void tick() {
        // Store previous position for interpolation
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
    }

    /**
     * Moves the entity and handles collision detection.
     *
     * @param xa Movement in x direction
     * @param ya Movement in y direction
     * @param za Movement in z direction
     */
    public void move(float xa, float ya, float za) {
        float originalXa = xa;
        float originalYa = ya;
        float originalZa = za;

        // Get all potential collision boxes within the movement area
        List<AABB> collisionBoxes = this.level.getCubes(this.bb.expand(xa, ya, za));

        // Handle Y-axis collisions first
        for (AABB collisionBox : collisionBoxes) {
            ya = CollisionUtils.clipYCollide(collisionBox, this.bb, ya);
        }
        this.bb.move(0.0F, ya, 0.0F);

        // Handle X-axis collisions
        for (AABB box : collisionBoxes) {
            xa = CollisionUtils.clipXCollide(box, this.bb, xa);
        }
        this.bb.move(xa, 0.0F, 0.0F);

        // Handle Z-axis collisions
        for (AABB collisionBox : collisionBoxes) {
            za = CollisionUtils.clipZCollide(collisionBox, this.bb, za);
        }
        this.bb.move(0.0F, 0.0F, za);

        // Update onGround status (true if we hit something below us)
        this.onGround = originalYa != ya && originalYa < 0.0F;

        // Reset velocity on collision
        if (originalXa != xa) {
            this.xd = 0.0F;
        }
        if (originalYa != ya) {
            this.yd = 0.0F;
        }
        if (originalZa != za) {
            this.zd = 0.0F;
        }

        // Update position based on bounding box position
        this.x = (this.bb.x0 + this.bb.x1) / 2.0F;
        this.y = this.bb.y0;
        this.z = (this.bb.z0 + this.bb.z1) / 2.0F;
    }

    /**
     * Move the entity relative to its current rotation.
     *
     * @param xa    X movement (left/right)
     * @param za    Z movement (forward/backward)
     * @param speed Movement speed multiplier
     */
    public void moveRelative(float xa, float za, float speed) {
        float distSquared = xa * xa + za * za;
        if (distSquared < 0.01F) {
            return; // Too small movement, ignore
        }

        // Normalize and apply speed
        float speedFactor = speed / (float) Math.sqrt(distSquared);
        xa *= speedFactor;
        za *= speedFactor;

        // Convert movement to global coordinates based on rotation
        float sin = (float) Math.sin(this.yRot * Math.PI / 180.0F);
        float cos = (float) Math.cos(this.yRot * Math.PI / 180.0F);

        // Apply rotation matrix
        this.xd += xa * cos - za * sin;
        this.zd += za * cos + xa * sin;
    }

    /**
     * Checks if the entity is in a lit area (for rendering brightness).
     *
     * @return true if the entity is in a lit area
     */
    public boolean isLit() {
        int blockX = (int) this.x;
        int blockY = (int) this.y;
        int blockZ = (int) this.z;
        return this.level.isLit(blockX, blockY, blockZ);
    }

    /**
     * Default render method. Override in subclasses to provide specific rendering.
     *
     * @param graphics The graphics api
     * @param partialTick Partial tick time for smooth animation
     */
    public void render(GraphicsAPI graphics, float partialTick) {
        // Default implementation does nothing
    }
} 