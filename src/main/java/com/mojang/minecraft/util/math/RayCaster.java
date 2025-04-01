package com.mojang.minecraft.util.math;

import com.mojang.minecraft.entity.Entity;
import com.mojang.minecraft.entity.Player;
import com.mojang.minecraft.level.Level;
import com.mojang.minecraft.level.tile.Tile;
import com.mojang.minecraft.world.HitResult;

/**
 * Utility class for performing raycasting operations.
 * Used to determine what block the player is looking at.
 */
public class RayCaster {
    private static final float EPSILON = 0.001F;

    /**
     * Maximum reach distance for player interaction
     */
    private static final float MAX_REACH_DISTANCE = 5.0F;

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
     * Performs a raycast against blocks in the level.
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
        // Current position along the ray
        float currentX = startX;
        float currentY = startY;
        float currentZ = startZ;

        // Current block position
        int blockX = (int) Math.floor(currentX);
        int blockY = (int) Math.floor(currentY);
        int blockZ = (int) Math.floor(currentZ);

        // Previous block position (to determine which face was hit)
        int prevBlockX = blockX;
        int prevBlockY = blockY;
        int prevBlockZ = blockZ;

        // Squared maximum distance for efficiency
        float maxDistanceSq = maxDistance * maxDistance;

        // Track closest hit so far
        HitResult closestHit = null;
        float closestHitDistanceSq = maxDistanceSq;

        // Early exit if starting inside a solid block
        Tile startTile = Tile.tiles[level.getTile(blockX, blockY, blockZ)];
        if (startTile != null && startTile.isSolid()) {
            // We're inside a block; return a hit at our current position with face 0 (bottom)
            return new HitResult(1, blockX, blockY, blockZ, 0, startX, startY, startZ, 0);
        }

        // Calculate ray step size
        float stepSize = 0.05F; // Small step for precision
        float travelDistanceSq = 0.0F;

        // Maximum iterations to prevent infinite loops
        int maxIterations = (int) (maxDistance / stepSize) + 2;

        for (int i = 0; i < maxIterations; i++) {
            // Move along the ray
            currentX += dirX * stepSize;
            currentY += dirY * stepSize;
            currentZ += dirZ * stepSize;

            // Calculate current block position
            int newBlockX = (int) Math.floor(currentX);
            int newBlockY = (int) Math.floor(currentY);
            int newBlockZ = (int) Math.floor(currentZ);

            // Check if we've moved to a new block
            boolean blockChanged = newBlockX != blockX || newBlockY != blockY || newBlockZ != blockZ;

            if (blockChanged) {
                // Update previous block position
                prevBlockX = blockX;
                prevBlockY = blockY;
                prevBlockZ = blockZ;

                // Update current block position
                blockX = newBlockX;
                blockY = newBlockY;
                blockZ = newBlockZ;

                // Check bounds
                if (blockX < 0 || blockY < 0 || blockZ < 0 ||
                        blockX >= level.width || blockY >= level.depth || blockZ >= level.height) {
                    // Out of bounds
                    continue;
                }

                // Check if the block is solid
                Tile tile = Tile.tiles[level.getTile(blockX, blockY, blockZ)];
                if (tile != null && tile.isSolid()) {
                    // We've hit a solid block, determine which face was hit
                    int face;
                    if (prevBlockX < blockX) face = 4; // Hit West face (coming from -X)
                    else if (prevBlockX > blockX) face = 5; // Hit East face (coming from +X)
                    else if (prevBlockY < blockY) face = 0; // Hit Bottom face (coming from -Y)
                    else if (prevBlockY > blockY) face = 1; // Hit Top face (coming from +Y)
                    else if (prevBlockZ < blockZ) face = 2; // Hit North face (coming from -Z)
                    else face = 3; // Hit South face (coming from +Z)

                    // Calculate squared distance from start to hit point
                    float dx = currentX - startX;
                    float dy = currentY - startY;
                    float dz = currentZ - startZ;
                    float distanceSq = dx * dx + dy * dy + dz * dz;

                    // If this hit is closer than any previous hit, store it
                    if (distanceSq < closestHitDistanceSq) {
                        closestHit = new HitResult(1, blockX, blockY, blockZ, face,
                                currentX, currentY, currentZ, distanceSq);
                        closestHitDistanceSq = distanceSq;
                    }

                    // We can continue to look for closer hits
                }
            }

            // Update travel distance and check if we've exceeded the maximum
            travelDistanceSq = (currentX - startX) * (currentX - startX) +
                    (currentY - startY) * (currentY - startY) +
                    (currentZ - startZ) * (currentZ - startZ);

            if (travelDistanceSq > maxDistanceSq) {
                break; // Exceeded maximum distance
            }
        }

        return closestHit;
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