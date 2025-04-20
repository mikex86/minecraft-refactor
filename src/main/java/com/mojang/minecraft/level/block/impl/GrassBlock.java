package com.mojang.minecraft.level.block.impl;

import com.mojang.minecraft.level.Level;
import com.mojang.minecraft.level.block.Block;
import com.mojang.minecraft.level.block.Blocks;
import com.mojang.minecraft.level.block.EnumFacing;
import com.mojang.minecraft.level.block.state.BlockState;

import java.util.Random;

/**
 * Grass tile implementation that can spread to nearby dirt and turn to dirt in darkness.
 */
public class GrassBlock extends Block {

    /**
     * Creates a new grass tile with the specified ID.
     *
     * @param id The tile ID
     */
    public GrassBlock(int id) {
        super(3); // Side texture (grass side)
    }

    /**
     * Gets the texture for a specific face.
     * Overrides the parent method to provide different textures for top, bottom, and sides.
     *
     * @param face The face index (0-5)
     * @return The texture index
     */
    @Override
    protected int getTexture(int face, EnumFacing facing) {
        // Top face (1) uses grass top texture (0)
        if (face == 1) {
            return 0;
        } else {
            // Bottom face (0) uses dirt texture (2)
            // All side faces use grass side texture (3)
            return face == 0 ? 2 : 3;
        }
    }

    /**
     * Update method called each tick for this tile.
     * Handles grass spread and die-off based on lighting.
     *
     * @param level  The current level
     * @param x      X coordinate
     * @param y      Y coordinate
     * @param z      Z coordinate
     * @param random Random number generator
     */
    @Override
    public void tick(Level level, int x, int y, int z, Random random) {
        // If not lit, convert to dirt
        if (!level.isLit(x, y, z)) {
            level.setBlockState(x, y, z, Blocks.rock.getDefaultBlockState());
        } else {
            // Try to spread grass to nearby dirt blocks
            for (int i = 0; i < 4; ++i) {
                // Choose a random nearby block
                int xt = x + random.nextInt(3) - 1;
                int yt = y + random.nextInt(5) - 3;
                int zt = z + random.nextInt(3) - 1;

                // If it's dirt and lit, convert it to grass
                BlockState blockState = level.getBlockState(xt, yt, zt);
                if (blockState != null && blockState.block == Blocks.rock && level.isLit(xt, yt, zt)) {
                    level.setBlockState(xt, yt, zt, Blocks.grass.getDefaultBlockState());
                }
            }
        }
    }
}
