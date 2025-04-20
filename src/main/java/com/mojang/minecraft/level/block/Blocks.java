package com.mojang.minecraft.level.block;

import com.mojang.minecraft.level.block.impl.*;
import com.mojang.minecraft.level.block.palette.BlockStatePalette;
import com.mojang.minecraft.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public class Blocks {

    private static final List<Block> BLOCKS = new ArrayList<>();

    public static BlockStatePalette globalPalette;

    /**
     * Represents an empty tile
     */
    public static final Block empty = null;

    /**
     * Rock/stone tile
     */
    public static final Block rock = new Block(1);

    /**
     * Grass tile
     */
    public static final Block grass = new GrassBlock(2);

    /**
     * Dirt tile
     */
    public static final Block dirt = new DirtBlock(3, 2);

    /**
     * Stone brick tile
     */
    public static final Block stoneBrick = new Block(16);

    /**
     * Wooden planks tile
     */
    public static final Block planks = new Block(4);

    /**
     * Leaves
     */
    public static final LeavesBlock leaves = new LeavesBlock();

    /**
     * Wood tile
     */
    public static final Block wood = new WoodBlock();

    /**
     * Glass
     */
    public static final GlassBlock glass = new GlassBlock();

    static {
        globalPalette = new BlockStatePalette(getValidBlockStates());
    }

    /**
     * @return all valid block states in the game
     */
    protected static List<BlockState> getValidBlockStates() {
        List<BlockState> list = new ArrayList<>();
        list.add(null); // null = air is a valid block state
        for (Block block : BLOCKS) {
            if (block != null) {
                List<BlockState> blockStates = block.getValidBlockStates();
                list.addAll(blockStates);
            }
        }
        return list;
    }

    public static void registerBlock(Block block) {
        BLOCKS.add(block);
    }
}
