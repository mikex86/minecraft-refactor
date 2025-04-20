package com.mojang.minecraft.level.block.impl;


import com.mojang.minecraft.level.block.Block;
import com.mojang.minecraft.level.block.EnumFacing;
import com.mojang.minecraft.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WoodBlock extends Block {

    /**
     * Creates a new wood tile with the specified ID.
     *
     */
    public WoodBlock() {
        super(21);
    }

    @Override
    protected List<BlockState> getValidBlockStates() {
        BlockState[] states = new BlockState[]{
                new BlockState(this, EnumFacing.UP, 0),
                new BlockState(this, EnumFacing.DOWN, 1),
                new BlockState(this, EnumFacing.NORTH, 2),
                new BlockState(this, EnumFacing.SOUTH, 3),
                new BlockState(this, EnumFacing.EAST, 4),
                new BlockState(this, EnumFacing.WEST, 5)
        };
        List<BlockState> blockStates = new ArrayList<>();
        Collections.addAll(blockStates, states);
        return blockStates;
    }

    /**
     * Gets the texture for a specific face.
     * Overrides the parent method to provide different textures for top, bottom, and sides.
     *
     * @param face   The face index (0-5)
     * @param facing the facing direction of the tile in the world
     * @return The texture index
     */
    @Override
    protected int getTexture(int face, EnumFacing facing) {
        switch (facing) {
            case UP:
            case DOWN:
                return (face != 0 && face != 1) ? 20 : 21;
            case NORTH:
            case SOUTH:
                return (face != 2 && face != 3) ? 20 : 21;
            case EAST:
            case WEST:
                return (face != 4 & face != 5) ? 20 : 21;
            default:
                throw new IllegalArgumentException("Invalid enum facing");
        }
    }

    @Override
    protected int getRotation(int face, EnumFacing facing) {
        switch (facing) {
            case UP:
                return 0;
            case DOWN:
                // top and bottom are not rotated; other faces are flipped 180 deg
                return (face == 0 || face == 1) ? 0 : 2;
            case NORTH:
                return (face == 0 || face == 1) ? 2 : face == 2 ? 0 : face == 5 ? 3 : 1;

            case SOUTH:
                // top and bottom faces are not rotated; other faces are rotated
                return (face == 0 || face == 1) ? 0 : face == 4 ? 3 : 1;

            case EAST:
                return (face == 1 || face == 3) ? 3 : 1;

            case WEST:
                return (face == 1 || face == 3) ? 1 : 3;

            default:
                throw new IllegalArgumentException("Invalid enum facing");
        }
    }
}
