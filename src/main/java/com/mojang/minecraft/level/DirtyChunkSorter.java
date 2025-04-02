package com.mojang.minecraft.level;

import com.mojang.minecraft.entity.Player;
import com.mojang.minecraft.renderer.Frustum;

import java.util.Comparator;

/**
 * Sorts chunks for rendering based on visibility, age of dirt state, and distance to player.
 * This prioritizes visible chunks that have been dirty for longer and are closer to the player.
 */
public class DirtyChunkSorter implements Comparator<Chunk> {

    private static final long AGE_BUCKET_SIZE = 2000L;

    private final Player player;
    private final Frustum frustum;
    private final long currentTime = System.currentTimeMillis();

    /**
     * Creates a new chunk sorter.
     *
     * @param player  The player to calculate distances from
     * @param frustum The view frustum for visibility testing
     */
    public DirtyChunkSorter(Player player, Frustum frustum) {
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

        // Second priority: age of dirt state (in time buckets)
        int ageBucket1 = (int) ((this.currentTime - chunk1.dirtiedTime) / AGE_BUCKET_SIZE);
        int ageBucket2 = (int) ((this.currentTime - chunk2.dirtiedTime) / AGE_BUCKET_SIZE);

        if (ageBucket1 < ageBucket2) {
            return -1;
        } else if (ageBucket1 > ageBucket2) {
            return 1;
        }

        // Third priority: distance to player
        float distance1 = chunk1.distanceToSqr(this.player);
        float distance2 = chunk2.distanceToSqr(this.player);
        return distance1 < distance2 ? -1 : 1;
    }
}
