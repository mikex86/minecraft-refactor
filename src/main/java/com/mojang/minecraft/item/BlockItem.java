package com.mojang.minecraft.item;

import com.mojang.minecraft.level.block.Block;

public class BlockItem extends Item {

    private final Block block;

    BlockItem(Block block) {
        super(block.name, 64);
        this.block = block;
    }

    public Block getBlock() {
        return block;
    }

    @Override
    public String toString() {
        return "BlockItem{" +
                "block=" + block +
                ", maxStackSize=" + getMaxStackSize() +
                '}';
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof BlockItem)) return false;
        BlockItem that = (BlockItem) obj;
        return block.equals(that.block);
    }
}
