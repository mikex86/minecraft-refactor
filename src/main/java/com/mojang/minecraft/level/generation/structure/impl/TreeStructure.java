package com.mojang.minecraft.level.generation.structure.impl;

import com.mojang.minecraft.level.block.Blocks;
import com.mojang.minecraft.level.block.state.BlockState;
import com.mojang.minecraft.level.generation.structure.Structure;

public class TreeStructure implements Structure {

    /**
     * The structure of the tree.
     * 0 = empty space
     * 1 = tree trunk
     * 2 = leaves
     */
    public static final int[][][] TREE_STRUCTURE = {
            // layer 0
            {
                    {0, 0, 0, 0, 0},
                    {0, 0, 0, 0, 0},
                    {0, 0, 1, 0, 0},
                    {0, 0, 0, 0, 0},
                    {0, 0, 0, 0, 0},
            },
            // layer 1
            {
                    {0, 0, 0, 0, 0},
                    {0, 0, 0, 0, 0},
                    {0, 0, 1, 0, 0},
                    {0, 0, 0, 0, 0},
                    {0, 0, 0, 0, 0},
            },
            // layer 2
            {
                    {2, 2, 2, 2, 2},
                    {2, 2, 2, 2, 2},
                    {2, 2, 1, 2, 2},
                    {2, 2, 2, 2, 2},
                    {2, 2, 2, 2, 2},
            },
            // layer 3
            {
                    {2, 2, 2, 2, 2},
                    {2, 2, 2, 2, 2},
                    {2, 2, 1, 2, 2},
                    {2, 2, 2, 2, 2},
                    {2, 2, 2, 2, 2},
            },
            // layer 4
            {
                    {0, 0, 0, 0, 0},
                    {0, 2, 2, 2, 0},
                    {0, 2, 2, 2, 0},
                    {0, 2, 2, 2, 0},
                    {0, 0, 0, 0, 0},
            },
            // layer 5
            {
                    {0, 0, 0, 0, 0},
                    {0, 0, 2, 0, 0},
                    {0, 2, 2, 2, 0},
                    {0, 0, 2, 0, 0},
                    {0, 0, 0, 0, 0},
            },

    };

    private static BlockState translateMaskId(int maskId) {
        switch (maskId) {
            case 0:
                return null; // Empty space
            case 1:
                return Blocks.wood.getDefaultBlockState();
            case 2:
                return Blocks.leaves.getDefaultBlockState();
            default:
                throw new IllegalArgumentException("Invalid maskId: " + maskId);
        }
    }

    @Override
    public int getWidth() {
        return TREE_STRUCTURE[0].length;
    }

    @Override
    public int getHeight() {
        return TREE_STRUCTURE.length;
    }

    @Override
    public int getDepth() {
        return TREE_STRUCTURE[0][0].length;
    }

    @Override
    public BlockState getBlockAt(int lx, int ly, int lz) {
        return translateMaskId(TREE_STRUCTURE[ly][lx][lz]);
    }
}