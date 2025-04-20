package com.mojang.minecraft.level.generation.structure;

import com.mojang.minecraft.level.Chunk;

public class StructurePlacement {

    private final Structure structure;
    private final int x0, y0, z0;

    // TODO: REVISIT
    private boolean placed;

    public StructurePlacement(Structure structure, int x0, int y0, int z0) {
        this.structure = structure;
        this.x0 = x0;
        this.y0 = y0;
        this.z0 = z0;
    }

    public Structure getStructure() {
        return structure;
    }

    public int getX0() {
        return x0;
    }

    public int getY0() {
        return y0;
    }

    public int getZ0() {
        return z0;
    }

    /**
     * Checks if the two AABBs overlap
     * for this just any coordinate from (x0, y0, z0) to (x0 + width, y0 + height, z0 + depth) must fall into (chunk.x0, chunk.y0, chunk.z0) to (chunk.x1, chunk.y1, chunk.z1)
     */
    public boolean overlapsIntoChunkRange(Chunk chunk) {
        return (x0 < chunk.x1 && x0 + structure.getWidth() > chunk.x0) &&
                (y0 < chunk.y1 && y0 + structure.getHeight() > chunk.y0) &&
                (z0 < chunk.z1 && z0 + structure.getDepth() > chunk.z0);
    }

    public boolean overlaps(StructurePlacement otherPlacement) {
        return (x0 < otherPlacement.x0 + otherPlacement.structure.getWidth() && x0 + structure.getWidth() > otherPlacement.x0) &&
                (y0 < otherPlacement.y0 + otherPlacement.structure.getHeight() && y0 + structure.getHeight() > otherPlacement.y0) &&
                (z0 < otherPlacement.z0 + otherPlacement.structure.getDepth() && z0 + structure.getDepth() > otherPlacement.z0);
    }
}
