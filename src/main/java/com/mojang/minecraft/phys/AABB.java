package com.mojang.minecraft.phys;

/**
 * Axis-Aligned Bounding Box class used for collision detection.
 * Represents a rectangular box in 3D space aligned with the coordinate axes.
 */
public class AABB {
    /** Small value used to prevent floating point precision issues in collision detection */
    private final float epsilon = 0.0F;
    
    /** Minimum x coordinate (left) */
    public float x0;
    /** Minimum y coordinate (bottom) */
    public float y0;
    /** Minimum z coordinate (back) */
    public float z0;
    /** Maximum x coordinate (right) */
    public float x1;
    /** Maximum y coordinate (top) */
    public float y1;
    /** Maximum z coordinate (front) */
    public float z1;

    /**
     * Creates a new AABB with the specified coordinates.
     *
     * @param x0 Minimum x coordinate (left)
     * @param y0 Minimum y coordinate (bottom)
     * @param z0 Minimum z coordinate (back)
     * @param x1 Maximum x coordinate (right)
     * @param y1 Maximum y coordinate (top)
     * @param z1 Maximum z coordinate (front)
     */
    public AABB(float x0, float y0, float z0, float x1, float y1, float z1) {
        this.x0 = x0;
        this.y0 = y0;
        this.z0 = z0;
        this.x1 = x1;
        this.y1 = y1;
        this.z1 = z1;
    }

    /**
     * Expands this AABB in the direction of movement.
     * Used for collision detection when moving an entity.
     *
     * @param xa X-axis movement
     * @param ya Y-axis movement
     * @param za Z-axis movement
     * @return A new AABB expanded in the direction of movement
     */
    public AABB expand(float xa, float ya, float za) {
        float newX0 = this.x0;
        float newY0 = this.y0;
        float newZ0 = this.z0;
        float newX1 = this.x1;
        float newY1 = this.y1;
        float newZ1 = this.z1;
        
        // Expand in the direction of movement
        if (xa < 0.0F) {
            newX0 += xa;
        }
        if (xa > 0.0F) {
            newX1 += xa;
        }
        if (ya < 0.0F) {
            newY0 += ya;
        }
        if (ya > 0.0F) {
            newY1 += ya;
        }
        if (za < 0.0F) {
            newZ0 += za;
        }
        if (za > 0.0F) {
            newZ1 += za;
        }

        return new AABB(newX0, newY0, newZ0, newX1, newY1, newZ1);
    }

    /**
     * Grows this AABB in all directions by the specified amount.
     * 
     * @param xa Amount to grow in the x direction (both sides)
     * @param ya Amount to grow in the y direction (both sides)
     * @param za Amount to grow in the z direction (both sides)
     * @return A new AABB grown in all directions
     */
    public AABB grow(float xa, float ya, float za) {
        float newX0 = this.x0 - xa;
        float newY0 = this.y0 - ya;
        float newZ0 = this.z0 - za;
        float newX1 = this.x1 + xa;
        float newY1 = this.y1 + ya; 
        float newZ1 = this.z1 + za;
        return new AABB(newX0, newY0, newZ0, newX1, newY1, newZ1);
    }

    /**
     * Checks for collision along the X axis and adjusts movement accordingly.
     * 
     * @param box The other AABB to check for collision with
     * @param moveX The proposed movement along X axis
     * @return Adjusted movement value (may be reduced to prevent collision)
     */
    public float clipXCollide(AABB box, float moveX) {
        // If boxes don't overlap in Y or Z axes, no collision possible
        if (box.y1 <= this.y0 || box.y0 >= this.y1) {
            return moveX;
        }
        if (box.z1 <= this.z0 || box.z0 >= this.z1) {
            return moveX;
        }
        
        // Moving right and boxes could collide
        if (moveX > 0.0F && box.x1 <= this.x0) {
            float maxMove = this.x0 - box.x1 - this.epsilon;
            if (maxMove < moveX) {
                moveX = maxMove;
            }
        }
        
        // Moving left and boxes could collide
        if (moveX < 0.0F && box.x0 >= this.x1) {
            float maxMove = this.x1 - box.x0 + this.epsilon;
            if (maxMove > moveX) {
                moveX = maxMove;
            }
        }

        return moveX;
    }

    /**
     * Checks for collision along the Y axis and adjusts movement accordingly.
     * 
     * @param box The other AABB to check for collision with
     * @param moveY The proposed movement along Y axis
     * @return Adjusted movement value (may be reduced to prevent collision)
     */
    public float clipYCollide(AABB box, float moveY) {
        // If boxes don't overlap in X or Z axes, no collision possible
        if (box.x1 <= this.x0 || box.x0 >= this.x1) {
            return moveY;
        }
        if (box.z1 <= this.z0 || box.z0 >= this.z1) {
            return moveY;
        }
        
        // Moving up and boxes could collide
        if (moveY > 0.0F && box.y1 <= this.y0) {
            float maxMove = this.y0 - box.y1 - this.epsilon;
            if (maxMove < moveY) {
                moveY = maxMove;
            }
        }
        
        // Moving down and boxes could collide
        if (moveY < 0.0F && box.y0 >= this.y1) {
            float maxMove = this.y1 - box.y0 + this.epsilon;
            if (maxMove > moveY) {
                moveY = maxMove;
            }
        }

        return moveY;
    }

    /**
     * Checks for collision along the Z axis and adjusts movement accordingly.
     * 
     * @param box The other AABB to check for collision with
     * @param moveZ The proposed movement along Z axis
     * @return Adjusted movement value (may be reduced to prevent collision)
     */
    public float clipZCollide(AABB box, float moveZ) {
        // If boxes don't overlap in X or Y axes, no collision possible
        if (box.x1 <= this.x0 || box.x0 >= this.x1) {
            return moveZ;
        }
        if (box.y1 <= this.y0 || box.y0 >= this.y1) {
            return moveZ;
        }
        
        // Moving forward and boxes could collide
        if (moveZ > 0.0F && box.z1 <= this.z0) {
            float maxMove = this.z0 - box.z1 - this.epsilon;
            if (maxMove < moveZ) {
                moveZ = maxMove;
            }
        }
        
        // Moving backward and boxes could collide
        if (moveZ < 0.0F && box.z0 >= this.z1) {
            float maxMove = this.z1 - box.z0 + this.epsilon;
            if (maxMove > moveZ) {
                moveZ = maxMove;
            }
        }

        return moveZ;
    }

    /**
     * Checks if this AABB intersects with another AABB.
     * 
     * @param box The other AABB to check for intersection
     * @return true if the boxes intersect, false otherwise
     */
    public boolean intersects(AABB box) {
        // If boxes don't overlap in any axis, they don't intersect
        if (box.x1 <= this.x0 || box.x0 >= this.x1) {
            return false;
        }
        if (box.y1 <= this.y0 || box.y0 >= this.y1) {
            return false;
        }
        return !(box.z1 <= this.z0) && !(box.z0 >= this.z1);
    }

    /**
     * Moves this AABB by the specified amounts.
     * 
     * @param xa Amount to move in x direction
     * @param ya Amount to move in y direction
     * @param za Amount to move in z direction
     */
    public void move(float xa, float ya, float za) {
        this.x0 += xa;
        this.y0 += ya;
        this.z0 += za;
        this.x1 += xa;
        this.y1 += ya;
        this.z1 += za;
    }
}
