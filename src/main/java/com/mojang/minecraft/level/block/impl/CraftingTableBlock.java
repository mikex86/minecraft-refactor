package com.mojang.minecraft.level.block.impl;

import com.mojang.minecraft.level.block.Block;
import com.mojang.minecraft.level.block.EnumFacing;

/**
 * Basic crafting table block. Non-functional beyond rendering for now.
 */
public class CraftingTableBlock extends Block {

    public CraftingTableBlock() {
        super(43);
    }

    @Override
    protected int getTexture(int face, EnumFacing facing) {
        if (face == 1) {
            return 43; // top
        }
        if (face == 0) {
            return 4; // bottom shares plank texture
        }
        if (face == 2 || face == 3) {
            return 60; // front faces
        }
        return 59; // sides
    }
}
