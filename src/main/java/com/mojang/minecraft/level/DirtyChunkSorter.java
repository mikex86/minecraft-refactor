package com.mojang.minecraft.level;

import com.mojang.minecraft.entity.EntityPlayer;
import com.mojang.minecraft.level.chunk.Chunk;
import com.mojang.minecraft.renderer.Frustum;

import java.util.Comparator;

/**
 * Sorts chunks for rendering based on visibility, age of dirt state, and distance to player.
 * This prioritizes visible chunks that have been dirty for longer and are closer to the player.
 */
public class DirtyChunkSorter implements Comparator<Chunk> {

    private final EntityPlayer player;
    private final Frustum frustum;

    /**
     * Creates a new chunk sorter.
     *
     * @param player  The player to calculate distances from
     * @param frustum The view frustum for visibility testing
     */
    public DirtyChunkSorter(EntityPlayer player, Frustum frustum) {
        this.player = player;
        this.frustum = frustum;
    }

    /**
     * Compares two chunks for sorting, with the following priorities:
     * 1. Visible chunks come before non-visible chunks
     * 2. Older dirty chunks come before newer dirty chunks
     * 3. Closer chunks come before farther chunks
     */
    @Override
    public int compare(Chunk chunk1, Chunk chunk2) {
        boolean isVisible1 = this.frustum.isVisible(chunk1.aabb);
        boolean isVisible2 = this.frustum.isVisible(chunk2.aabb);

        // First priority: visibility
        if (isVisible1 && !isVisible2) {
            return -1;
        } else if (isVisible2 && !isVisible1) {
            return 1;
        }

        float distance1 = chunk1.distanceToSqr(this.player);
        float distance2 = chunk2.distanceToSqr(this.player);

        // Second priority: distance to player
        if (distance1 < distance2) {
            return -1;
        } else if (distance1 > distance2) {
            return 1;
        }

        // Third priority: angle from player to chunk center
        float c1centerX = chunk1.centerX;
        float c1centerZ = chunk1.centerZ;
        float c2centerX = chunk2.centerX;
        float c2centerZ = chunk2.centerZ;
        float angle1 = (float) (Math.atan2(c1centerZ - this.player.z, c1centerX - this.player.x) * (180.0 / Math.PI)) + 90.0F;
        float angle2 = (float) (Math.atan2(c2centerZ - this.player.z, c2centerX - this.player.x) * (180.0 / Math.PI)) + 90.0F;

        float playerYaw = this.player.yaw;
        playerYaw %= 360.0F;
        float angleDiff1 = Math.abs(angle1 - playerYaw);
        float angleDiff2 = Math.abs(angle2 - playerYaw);
        if (angleDiff1 > 180.0F) {
            angleDiff1 = 360.0F - angleDiff1;
        }
        if (angleDiff2 > 180.0F) {
            angleDiff2 = 360.0F - angleDiff2;
        }

        if (angleDiff1 < angleDiff2) {
            return -1;
        } else if (angleDiff1 > angleDiff2) {
            return 1;
        }

        // Fourth priority: age of dirt state
        long currentTime = System.currentTimeMillis();
        long age1 = (currentTime - chunk1.dirtiedTime);
        long age2 = (currentTime - chunk2.dirtiedTime);
        return Long.compare(age1, age2);
    }
}
