package com.mojang.minecraft.level.block.state;

import com.mojang.minecraft.level.block.Block;
import com.mojang.minecraft.level.block.EnumFacing;

public class BlockState {

    public final Block block;
    public final EnumFacing facing;

    /**
     * The variant index is the index of the block state in a block's list of valid block state.
     * Blocks must have an enumerable, finite number of block states. On initialization, each block
     * returns a list of valid block states. The variant index is the index of the block state in that list.
     * Each of those per-block lists are combined into the "global palette" of valid block states.
     */
    public final int variantIndex;

    public BlockState(Block block, EnumFacing facing, int variantIndex) {
        this.block = block;
        this.facing = facing;
        this.variantIndex = variantIndex;
    }

    @Override
    public String toString() {
        return "BlockState{" +
                "block=" + block +
                ", facing=" + facing +
                ", variantIndex=" + variantIndex +
                '}';
    }

    @Override
    public int hashCode() {
        return this.block.id * 31 + this.variantIndex;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        BlockState that = (BlockState) obj;
        return this.block.id == that.block.id &&
                this.variantIndex == that.variantIndex;
    }
}
