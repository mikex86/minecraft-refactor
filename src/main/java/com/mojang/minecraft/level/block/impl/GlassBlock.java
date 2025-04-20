package com.mojang.minecraft.level.block.impl;

import com.mojang.minecraft.level.Level;
import com.mojang.minecraft.level.block.Block;
import com.mojang.minecraft.level.block.Blocks;
import com.mojang.minecraft.level.block.state.BlockState;
import jdk.internal.vm.annotation.ForceInline;

public class GlassBlock extends Block {

    public GlassBlock() {
        super(49);
    }

    @Override
    @ForceInline
    public boolean isTransparent() {
        return true;
    }

    @Override
    @ForceInline
    public boolean isLightBlocker() {
        return false;
    }

    @Override
    @ForceInline
    protected boolean shouldRenderFace(Level level, int x, int y, int z) {
        if (level == null) {
            return true;
        }
        BlockState blockState = level.getBlockState(x, y, z);
        return blockState == null || blockState.block != Blocks.glass;
    }
}
