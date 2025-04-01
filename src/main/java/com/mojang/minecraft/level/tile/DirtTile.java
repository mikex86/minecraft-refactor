package com.mojang.minecraft.level.tile;

/**
 * Simple implementation of a dirt tile.
 * This tile can be transformed into grass when exposed to light.
 */
public class DirtTile extends Tile {
    
    /**
     * Creates a new dirt tile with the specified ID and texture.
     * 
     * @param id The tile ID
     * @param tex The texture index
     */
    protected DirtTile(int id, int tex) {
        super(id, tex);
    }
}
