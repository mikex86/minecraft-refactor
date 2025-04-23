package com.mojang.minecraft.item;

import com.mojang.minecraft.level.block.Block;
import com.mojang.minecraft.level.block.Blocks;

public class Inventory {

    public static final int HOTBAR_SIZE = 9;

    private final Block[] hotbarBlocks = new Block[HOTBAR_SIZE];

    private final Block[] inventoryBlocks = new Block[27];

    {
        Block[] defaultBlocks = new Block[]{
                Blocks.grass,
                Blocks.dirt,
                Blocks.rock,
                Blocks.stoneBrick,
                Blocks.glass,
                Blocks.planks,
                Blocks.wood,
                Blocks.leaves,
        };
        System.arraycopy(defaultBlocks, 0, hotbarBlocks, 0, defaultBlocks.length);
    }

    public Block getHotbarItem(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= HOTBAR_SIZE) {
            throw new IndexOutOfBoundsException("Hotbar index " + slotIndex + " out of bounds");
        }
        return hotbarBlocks[slotIndex];
    }

}
