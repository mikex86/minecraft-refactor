package com.mojang.minecraft.level.block.impl;

import com.mojang.minecraft.level.block.Block;

public class LeavesBlock extends Block {

    public LeavesBlock() {
        super(52);
    }

    @Override
    public boolean isTransparent() {
        return true;
    }
}
