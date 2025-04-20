package com.mojang.minecraft.level.block.impl;

import com.mojang.minecraft.level.block.Block;

/**
 * Simple implementation of a dirt tile.
 * This tile can be transformed into grass when exposed to light.
 */
public class DirtBlock extends Block {

    /**
     * Creates a new dirt tile with the specified ID and texture.
     *
     * @param id  The tile ID
     * @param tex The texture index
     */
    public DirtBlock(int id, int tex) {
        super(tex);
    }
}
