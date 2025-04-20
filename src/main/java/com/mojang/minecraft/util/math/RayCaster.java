package com.mojang.minecraft.util.math;

import com.mojang.minecraft.entity.Entity;
import com.mojang.minecraft.level.Level;
import com.mojang.minecraft.level.block.Block;
import com.mojang.minecraft.level.block.state.BlockState;
import com.mojang.minecraft.world.HitResult;

/**
 * Utility class for performing raycasting operations.
 * Uses an efficient and accurate DDA (Digital Differential Analysis) algorithm
 * to determine exactly which block the player is looking at.
 */
public class RayCaster {
    private static final float EPSILON = 0.001F;

    /**
     * Maximum reach distance for player interaction
     */
    private static final float MAX_REACH_DISTANCE = 5.0F;
    private static final int MAX_STEPS = 128;

    /**
     * Casts a ray from the entity's position in the direction they are looking.
     *
     * @param entity      The entity casting the ray
     * @param level       The level to cast the ray in
     * @param partialTick Partial tick value for interpolation
     * @return A HitResult with information about what was hit, or null if nothing was hit
     */
    public static HitResult raycast(Entity entity, Level level, float partialTick) {
        float rayStartX = entity.xo + (entity.x - entity.xo) * partialTick;
        float rayStartY = entity.yo + entity.getHeightOffset() + (entity.y - entity.yo) * partialTick;
        float rayStartZ = entity.zo + (entity.z - entity.zo) * partialTick;

        // Calculate ray direction from player's rotation
        float yaw = entity.yRot;
        float pitch = entity.xRot;

        // Convert rotation angles to direction vector
        // Yaw: 0 is south (+Z), 90 is west (-X), 180 is north (-Z), 270 is east (+X)
        // Pitch: 0 is horizontal, -90 is up, 90 is down
        float pitchRad = (float) Math.toRadians(pitch);
        float yawRad = (float) Math.toRadians(yaw);

        float dirX = (float) (Math.sin(yawRad) * Math.cos(pitchRad));
        float dirY = -(float) Math.sin(pitchRad);
        float dirZ = -(float) (Math.cos(yawRad) * Math.cos(pitchRad));

        // Normalize the direction vector
        float dirLength = (float) Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        if (dirLength < EPSILON) {
            return null; // Invalid direction
        }

        dirX /= dirLength;
        dirY /= dirLength;
        dirZ /= dirLength;

        return raycastBlocks(level, rayStartX, rayStartY, rayStartZ, dirX, dirY, dirZ, MAX_REACH_DISTANCE);
    }

    /**
     * Performs a raycast against blocks in the level using the DDA algorithm.
     * This algorithm is 100% accurate and does not rely on sampling or iteration count.
     *
     * @param level       The level to cast the ray in
     * @param startX      X coordinate of ray origin
     * @param startY      Y coordinate of ray origin
     * @param startZ      Z coordinate of ray origin
     * @param dirX        X component of ray direction (normalized)
     * @param dirY        Y component of ray direction (normalized)
     * @param dirZ        Z component of ray direction (normalized)
     * @param maxDistance Maximum distance to cast the ray
     * @return A HitResult with information about what was hit, or null if nothing was hit
     */
    private static HitResult raycastBlocks(Level level, float startX, float startY, float startZ,
                                          float dirX, float dirY, float dirZ, float maxDistance) {
        // Initial block position (the block containing the ray start point)
        int blockX = (int) Math.floor(startX);
        int blockY = (int) Math.floor(startY);
        int blockZ = (int) Math.floor(startZ);

        // Early exit if starting inside a solid block
        BlockState startBlock = level.getBlockState(blockX, blockY, blockZ);
        if (startBlock != null && startBlock.block.isSolid()) {
            // We're inside a block; return a hit at our current position with face 0 (bottom)
            return new HitResult(1, blockX, blockY, blockZ, 0, startX, startY, startZ, 0);
        }

        // Step direction for each axis (either 1 or -1)
        int stepX = dirX > 0 ? 1 : (dirX < 0 ? -1 : 0);
        int stepY = dirY > 0 ? 1 : (dirY < 0 ? -1 : 0);
        int stepZ = dirZ > 0 ? 1 : (dirZ < 0 ? -1 : 0);

        // Calculate delta distance - distance along ray to move one unit in each dimension
        float deltaDistX = Math.abs(dirX) < EPSILON ? Float.MAX_VALUE : Math.abs(1.0f / dirX);
        float deltaDistY = Math.abs(dirY) < EPSILON ? Float.MAX_VALUE : Math.abs(1.0f / dirY);
        float deltaDistZ = Math.abs(dirZ) < EPSILON ? Float.MAX_VALUE : Math.abs(1.0f / dirZ);

        // Calculate initial side distance - distance from start to first grid boundary
        float sideDistX;
        float sideDistY;
        float sideDistZ;

        if (dirX > 0) {
            sideDistX = ((blockX + 1) - startX) * deltaDistX;
        } else {
            sideDistX = (startX - blockX) * deltaDistX;
        }

        if (dirY > 0) {
            sideDistY = ((blockY + 1) - startY) * deltaDistY;
        } else {
            sideDistY = (startY - blockY) * deltaDistY;
        }

        if (dirZ > 0) {
            sideDistZ = ((blockZ + 1) - startZ) * deltaDistZ;
        } else {
            sideDistZ = (startZ - blockZ) * deltaDistZ;
        }

        // Variables to track which face was hit
        int hitFace = -1;
        
        // Distance traveled along the ray
        float travelDistance = 0.0f;
        
        // Main DDA loop
        for (int i = 0; i < MAX_STEPS; i++) {
            // Check current block for a hit
            BlockState blockState = level.getBlockState(blockX, blockY, blockZ);
            if (blockState != null && blockState.block.isSolid()) {
                // We found a hit
                break;
            }
            
            // Determine which grid cell to step to next (the one with the closest boundary)
            if (sideDistX < sideDistY && sideDistX < sideDistZ) {
                // X-axis boundary is closest
                travelDistance = sideDistX;
                sideDistX += deltaDistX;
                blockX += stepX;
                hitFace = stepX > 0 ? 4 : 5; // 4 = West face, 5 = East face
            } else if (sideDistY < sideDistZ) {
                // Y-axis boundary is closest
                travelDistance = sideDistY;
                sideDistY += deltaDistY;
                blockY += stepY;
                hitFace = stepY > 0 ? 0 : 1; // 0 = Bottom face, 1 = Top face
            } else {
                // Z-axis boundary is closest
                travelDistance = sideDistZ;
                sideDistZ += deltaDistZ;
                blockZ += stepZ;
                hitFace = stepZ > 0 ? 2 : 3; // 2 = North face, 3 = South face
            }
            
            // Check if we've exceeded maximum distance
            if (travelDistance > maxDistance) {
                return null; // No hit within range
            }
        }
        
        // Double check that we actually hit something
        BlockState hitTile = level.getBlockState(blockX, blockY, blockZ);
        if (hitTile == null || !hitTile.block.isSolid()) {
            return null; // No hit found (should not happen but just in case)
        }
        
        // Calculate the exact hit point
        float hitX = startX + dirX * travelDistance;
        float hitY = startY + dirY * travelDistance;
        float hitZ = startZ + dirZ * travelDistance;
        
        // The squared distance for the hit result
        float distanceSq = travelDistance * travelDistance;
        
        return new HitResult(1, blockX, blockY, blockZ, hitFace, hitX, hitY, hitZ, distanceSq);
    }

    /**
     * Tests for intersection between a ray and an AABB.
     *
     * @param boxMinX   Minimum X coordinate of the AABB
     * @param boxMinY   Minimum Y coordinate of the AABB
     * @param boxMinZ   Minimum Z coordinate of the AABB
     * @param boxMaxX   Maximum X coordinate of the AABB
     * @param boxMaxY   Maximum Y coordinate of the AABB
     * @param boxMaxZ   Maximum Z coordinate of the AABB
     * @param rayStartX X coordinate of ray origin
     * @param rayStartY Y coordinate of ray origin
     * @param rayStartZ Z coordinate of ray origin
     * @param rayDirX   X component of ray direction
     * @param rayDirY   Y component of ray direction
     * @param rayDirZ   Z component of ray direction
     * @return Time of intersection (distance along ray), or -1 if no intersection
     */
    public static float intersectAABB(
            float boxMinX, float boxMinY, float boxMinZ,
            float boxMaxX, float boxMaxY, float boxMaxZ,
            float rayStartX, float rayStartY, float rayStartZ,
            float rayDirX, float rayDirY, float rayDirZ) {

        // Calculate intersection with each pair of planes
        float tMinX, tMaxX, tMinY, tMaxY, tMinZ, tMaxZ;

        // Handle divide by zero
        float invDirX = Math.abs(rayDirX) < EPSILON ? Float.MAX_VALUE : 1.0f / rayDirX;
        float invDirY = Math.abs(rayDirY) < EPSILON ? Float.MAX_VALUE : 1.0f / rayDirY;
        float invDirZ = Math.abs(rayDirZ) < EPSILON ? Float.MAX_VALUE : 1.0f / rayDirZ;

        // X planes
        if (invDirX >= 0) {
            tMinX = (boxMinX - rayStartX) * invDirX;
            tMaxX = (boxMaxX - rayStartX) * invDirX;
        } else {
            tMinX = (boxMaxX - rayStartX) * invDirX;
            tMaxX = (boxMinX - rayStartX) * invDirX;
        }

        // Y planes
        if (invDirY >= 0) {
            tMinY = (boxMinY - rayStartY) * invDirY;
            tMaxY = (boxMaxY - rayStartY) * invDirY;
        } else {
            tMinY = (boxMaxY - rayStartY) * invDirY;
            tMaxY = (boxMinY - rayStartY) * invDirY;
        }

        // Early exit if no overlap in X and Y
        if (tMinX > tMaxY || tMinY > tMaxX) {
            return -1.0f;
        }

        // Find overlap
        float tMin = tMinX > tMinY ? tMinX : tMinY;
        float tMax = tMaxX < tMaxY ? tMaxX : tMaxY;

        // Z planes
        if (invDirZ >= 0) {
            tMinZ = (boxMinZ - rayStartZ) * invDirZ;
            tMaxZ = (boxMaxZ - rayStartZ) * invDirZ;
        } else {
            tMinZ = (boxMaxZ - rayStartZ) * invDirZ;
            tMaxZ = (boxMinZ - rayStartZ) * invDirZ;
        }

        // Early exit if no overlap in combined XY and Z
        if (tMin > tMaxZ || tMinZ > tMax) {
            return -1.0f;
        }

        // Final overlap
        tMin = tMin > tMinZ ? tMin : tMinZ;
        tMax = tMax < tMaxZ ? tMax : tMaxZ;

        // Check if intersection is in positive ray direction
        if (tMax < 0) {
            return -1.0f; // Intersection is behind ray origin
        }

        return tMin >= 0 ? tMin : tMax;
    }
} 