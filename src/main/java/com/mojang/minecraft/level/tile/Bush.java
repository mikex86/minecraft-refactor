package com.mojang.minecraft.level.tile;

import com.mojang.minecraft.level.Level;
import com.mojang.minecraft.phys.AABB;
import com.mojang.minecraft.renderer.Tesselator;

import java.util.Random;

/**
 * Implementation of a bush/plant tile.
 * Bushes are non-solid, don't block light, and need dirt or grass beneath them.
 */
public class Bush extends Tile {

    /**
     * Creates a new bush tile with the specified ID.
     *
     * @param id The tile ID
     */
    protected Bush(int id) {
        super(id);
        this.tex = 15; // Bush texture
    }

    /**
     * Update method called each tick for this tile.
     * Checks if the bush has valid ground beneath it and sufficient light.
     *
     * @param level  The current level
     * @param x      X coordinate
     * @param y      Y coordinate
     * @param z      Z coordinate
     * @param random Random number generator
     */
    @Override
    public void tick(Level level, int x, int y, int z, Random random) {
        int tileBelow = level.getTile(x, y - 1, z);

        // Check if bush has valid ground below and sufficient light
        if (!level.isLit(x, y, z) || (tileBelow != Tile.dirt.id && tileBelow != Tile.grass.id)) {
            level.setTile(x, y, z, 0); // Remove bush if conditions not met
        }
    }

    /**
     * Custom rendering for bush tiles as crossed planes.
     *
     * @param t     The tesselator for rendering
     * @param level The current level
     * @param layer The render layer
     * @param x     X coordinate
     * @param y     Y coordinate
     * @param z     Z coordinate
     */
    @Override
    public void render(Tesselator t, Level level, int layer, int x, int y, int z) {
        // Only render if the lighting condition is appropriate for this layer
        if (level == null || level.isLit(x, y, z) == (layer != 1)) {
            int tex = this.getTexture(this.tex);
            float u0 = (tex % 16) / 16.0F;
            float u1 = u0 + 0.0624375F;
            float v0 = (tex / 16) / 16.0F;
            float v1 = v0 + 0.0624375F;
            int rotations = 2; // Number of crossed planes

            t.color(1.0F, 1.0F, 1.0F);

            // Render crossed planes
            for (int r = 0; r < rotations; ++r) {
                // Calculate rotation angle and offsets
                float angle = (float) (r * Math.PI / rotations + (Math.PI / 4D));
                float xa = (float) (Math.sin(angle) * 0.5F);
                float za = (float) (Math.cos(angle) * 0.5F);

                // Calculate vertex positions
                float x0 = x + 0.5F - xa;
                float x1 = x + 0.5F + xa;
                float y0 = y;
                float y1 = y + 1.0F;
                float z0 = z + 0.5F - za;
                float z1 = z + 0.5F + za;

                // First triangle of the plane
                t.vertexUV(x0, y1, z0, u1, v0);
                t.vertexUV(x1, y1, z1, u0, v0);
                t.vertexUV(x1, y0, z1, u0, v1);
                t.vertexUV(x0, y0, z0, u1, v1);

                // Second triangle (back face)
                t.vertexUV(x1, y1, z1, u0, v0);
                t.vertexUV(x0, y1, z0, u1, v0);
                t.vertexUV(x0, y0, z0, u1, v1);
                t.vertexUV(x1, y0, z1, u0, v1);
            }
        }
    }

    /**
     * Bush tiles don't have a collision box.
     *
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @return null as bushes don't have collision
     */
    @Override
    public AABB getAABB(int x, int y, int z) {
        return null;
    }

    /**
     * Bush tiles don't block light.
     *
     * @return false as bushes don't block light
     */
    @Override
    public boolean blocksLight() {
        return false;
    }

    /**
     * Bush tiles aren't solid (can be walked through).
     *
     * @return false as bushes aren't solid
     */
    @Override
    public boolean isSolid() {
        return false;
    }
}
