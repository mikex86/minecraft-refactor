package com.mojang.minecraft.item;

import com.mojang.minecraft.level.block.Block;
import com.mojang.minecraft.level.block.Blocks;

public class BlockItems {

    public static final BlockItem rock = new BlockItem(Blocks.rock);
    public static final BlockItem grass = new BlockItem(Blocks.grass);
    public static final BlockItem dirt = new BlockItem(Blocks.dirt);
    public static final BlockItem stoneBrick = new BlockItem(Blocks.stoneBrick);
    public static final BlockItem planks = new BlockItem(Blocks.planks);
    public static final BlockItem leaves = new BlockItem(Blocks.leaves);
    public static final BlockItem wood = new BlockItem(Blocks.wood);
    public static final BlockItem glass = new BlockItem(Blocks.glass);
    public static final BlockItem craftingTable = new BlockItem(Blocks.craftingTable);

    private static final BlockItem[] blockItems = new BlockItem[]{rock, grass, dirt, stoneBrick, planks, leaves, wood, glass, craftingTable};
    private static final BlockItem[] blockItemsById;

    static {
        int maxId = 0;
        for (BlockItem blockItem : blockItems) {
            maxId = Math.max(maxId, blockItem.getBlock().getId());
        }
        // initialize blockItemsById with the required size
        blockItemsById = new BlockItem[maxId + 1];

        // populate blockItemsById
        for (BlockItem blockItem : blockItems) {
            Block block = blockItem.getBlock();
            blockItemsById[block.getId()] = blockItem;
        }
    }

    public static BlockItem getBlockItemForBlockOrNull(Block block) {
        return blockItemsById[block.getId()];
    }
}
