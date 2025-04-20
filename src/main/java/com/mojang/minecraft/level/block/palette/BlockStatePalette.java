package com.mojang.minecraft.level.block.palette;

import com.mojang.minecraft.level.block.state.BlockState;
import jdk.internal.vm.annotation.ForceInline;

import java.util.*;

/**
 * A block state palette is a utility for generating ids to represent a particular set of block states.
 * Specifically, it guarantees that the ids returned by it are strictly within log2(numBlockStates) bits.
 * This allows to get ids specifically for actually occurring block states.
 */
public class BlockStatePalette {

    private final int paletteSize;
    private final BlockState[] blockStates;
    private final Map<BlockState, Integer> blockStateIds = new HashMap<>();

    public BlockStatePalette(List<BlockState> blockStates) {
        this.paletteSize = blockStates.size();
        this.blockStates = new BlockState[paletteSize];
        int blockStateId = 0;
        for (BlockState blockState : blockStates) {
            this.blockStateIds.put(blockState, blockStateId);
            this.blockStates[blockStateId] = blockState;
            blockStateId++;
        }
    }

    @ForceInline
    public int getPaletteId(BlockState state) {
        return blockStateIds.get(state);
    }

    @ForceInline
    public BlockState fromBlockStateId(int blockStateId) {
        return blockStates[blockStateId];
    }
}
