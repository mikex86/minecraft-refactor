package com.mojang.minecraft.util.math;

import com.mojang.minecraft.phys.AABB;

/**
 * Utility methods for collision detection and resolution.
 * Provides static methods for common collision operations.
 */
public class CollisionUtils {
    /**
     * Small epsilon value to prevent floating point precision issues
     */
    private static final float EPSILON = 0.001F;

    /**
     * Checks if two AABBs intersect.
     *
     * @param box1 The first AABB
     * @param box2 The second AABB
     * @return true if the boxes intersect, false otherwise
     */
    public static boolean intersects(AABB box1, AABB box2) {
        // If boxes don't overlap in any axis, they don't intersect
        if (box2.x1 <= box1.x0 || box2.x0 >= box1.x1) {
            return false;
        }
        if (box2.y1 <= box1.y0 || box2.y0 >= box1.y1) {
            return false;
        }
        return !(box2.z1 <= box1.z0) && !(box2.z0 >= box1.z1);
    }

    /**
     * Checks for collision along the X axis and adjusts movement accordingly.
     *
     * @param staticBox The static AABB to check for collision with
     * @param movingBox The moving AABB
     * @param moveX     The proposed movement along X axis
     * @return Adjusted movement value (may be reduced to prevent collision)
     */
    public static float clipXCollide(AABB staticBox, AABB movingBox, float moveX) {
        // If boxes don't overlap in Y or Z axes, no collision possible
        if (movingBox.y1 <= staticBox.y0 || movingBox.y0 >= staticBox.y1) {
            return moveX;
        }
        if (movingBox.z1 <= staticBox.z0 || movingBox.z0 >= staticBox.z1) {
            return moveX;
        }

        // Moving right and boxes could collide
        if (moveX > 0.0F && movingBox.x1 <= staticBox.x0) {
            float maxMove = staticBox.x0 - movingBox.x1 - EPSILON;
            if (maxMove < moveX) {
                moveX = maxMove;
            }
        }

        // Moving left and boxes could collide
        if (moveX < 0.0F && movingBox.x0 >= staticBox.x1) {
            float maxMove = staticBox.x1 - movingBox.x0 + EPSILON;
            if (maxMove > moveX) {
                moveX = maxMove;
            }
        }

        return moveX;
    }

    /**
     * Checks for collision along the Y axis and adjusts movement accordingly.
     *
     * @param staticBox The static AABB to check for collision with
     * @param movingBox The moving AABB
     * @param moveY     The proposed movement along Y axis
     * @return Adjusted movement value (may be reduced to prevent collision)
     */
    public static float clipYCollide(AABB staticBox, AABB movingBox, float moveY) {
        // If boxes don't overlap in X or Z axes, no collision possible
        if (movingBox.x1 <= staticBox.x0 || movingBox.x0 >= staticBox.x1) {
            return moveY;
        }
        if (movingBox.z1 <= staticBox.z0 || movingBox.z0 >= staticBox.z1) {
            return moveY;
        }

        // Moving up and boxes could collide
        if (moveY > 0.0F && movingBox.y1 <= staticBox.y0) {
            float maxMove = staticBox.y0 - movingBox.y1 - EPSILON;
            if (maxMove < moveY) {
                moveY = maxMove;
            }
        }

        // Moving down and boxes could collide
        if (moveY < 0.0F && movingBox.y0 >= staticBox.y1) {
            float maxMove = staticBox.y1 - movingBox.y0 + EPSILON;
            if (maxMove > moveY) {
                moveY = maxMove;
            }
        }

        return moveY;
    }

    /**
     * Checks for collision along the Z axis and adjusts movement accordingly.
     *
     * @param staticBox The static AABB to check for collision with
     * @param movingBox The moving AABB
     * @param moveZ     The proposed movement along Z axis
     * @return Adjusted movement value (may be reduced to prevent collision)
     */
    public static float clipZCollide(AABB staticBox, AABB movingBox, float moveZ) {
        // If boxes don't overlap in X or Y axes, no collision possible
        if (movingBox.x1 <= staticBox.x0 || movingBox.x0 >= staticBox.x1) {
            return moveZ;
        }
        if (movingBox.y1 <= staticBox.y0 || movingBox.y0 >= staticBox.y1) {
            return moveZ;
        }

        // Moving forward and boxes could collide
        if (moveZ > 0.0F && movingBox.z1 <= staticBox.z0) {
            float maxMove = staticBox.z0 - movingBox.z1 - EPSILON;
            if (maxMove < moveZ) {
                moveZ = maxMove;
            }
        }

        // Moving backward and boxes could collide
        if (moveZ < 0.0F && movingBox.z0 >= staticBox.z1) {
            float maxMove = staticBox.z1 - movingBox.z0 + EPSILON;
            if (maxMove > moveZ) {
                moveZ = maxMove;
            }
        }

        return moveZ;
    }
} 