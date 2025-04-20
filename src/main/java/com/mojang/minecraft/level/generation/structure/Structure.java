package com.mojang.minecraft.level.generation.structure;

import com.mojang.minecraft.level.Chunk;
import com.mojang.minecraft.level.block.state.BlockState;
import com.mojang.minecraft.util.math.MathUtils;

public interface Structure {

    /**
     * Places the structure at the specified position.
     * This places the part of the structure into the chunk that falls into the responsibility of said chunk.
     * The specified values are absolute position values. We find the subset of the structure's grid which falls
     * into the supplied chunk's responsibility and "render" it into the data of the chunk.
     *
     * @param chunk the chunk to populate with data
     * @param x0    the x0 coordinate where to place the structure
     * @param y0    the y0 coordinate where to place the structure
     * @param z0    the z0 coordinate where to place the structure
     */
    @SuppressWarnings("UnnecessaryLocalVariable")
    default void place(Chunk chunk, int x0, int y0, int z0) {
        int chunkX0 = chunk.x0;
        int chunkY0 = chunk.y0;
        int chunkZ0 = chunk.z0;

        int chunkX1 = chunk.x1;
        int chunkZ1 = chunk.z1;

        int x1 = x0 + getWidth();
        int y1 = y0 + getHeight();
        int z1 = z0 + getDepth();

        // we have absolute coordinates in world space where the structure should go
        // (x0, y0, z0) to (x1, y1, z1)

        // Now, we clamp these coordinates into what is the responsibility of this chunk.
        // We are still in world space
        int cx0 = MathUtils.clamp(x0, chunkX0, chunkX1);
        int cy0 = y0;
        int cz0 = MathUtils.clamp(z0, chunkZ0, chunkZ1);

        int cx1 = MathUtils.clamp(x1, chunkX0, chunkX1);
        int cy1 = y1;
        int cz1 = MathUtils.clamp(z1, chunkZ0, chunkZ1);

        for (int gx = cx0; gx < cx1; gx++) {
            for (int gy = cy0; gy < cy1; gy++) {
                for (int gz = cz0; gz < cz1; gz++) {

                    // convert to structure-local space
                    int lx = gx - x0;
                    int ly = gy - y0;
                    int lz = gz - z0;

                    // convert to chunk-local space
                    int cx = gx - chunkX0;
                    int cy = gy - chunkY0;
                    int cz = gz - chunkZ0;

                    BlockState blockState = getBlockAt(lx, ly, lz);
                    if (blockState != null) {
                        chunk.setBlockState(cx, cy, cz, blockState);
                    }
                }
            }
        }
    }

    /**
     * @return the width of the structure, its size on the x-axis
     */
    int getWidth();

    /**
     * @return the height of the structure, its size on the y-axis
     */
    int getHeight();

    /**
     * @return the depth of the structure, its size on the z-axis
     */
    int getDepth();

    /**
     * Returns the structure's block at the specific local position.
     *
     * @param lx the specified position in structure-local space; Range [0; width[
     * @param ly the specified position in structure-local space; Range [0; height[
     * @param lz the specified position in structure-local space; Range [0; depth[
     * @return the structure's block at the specified local position
     */
    BlockState getBlockAt(int lx, int ly, int lz);
}
