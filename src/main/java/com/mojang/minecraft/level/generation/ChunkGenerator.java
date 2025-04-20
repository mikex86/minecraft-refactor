package com.mojang.minecraft.level.generation;

import com.mojang.minecraft.level.Chunk;
import com.mojang.minecraft.level.block.Blocks;

public class ChunkGenerator {


    private final int worldSeed;

    public ChunkGenerator(int worldSeed) {
        this.worldSeed = worldSeed;
    }

    public void generate(Chunk chunk) {
        for (int x = 0; x < Chunk.CHUNK_SIZE; ++x) {
            for (int z = 0; z < Chunk.CHUNK_SIZE; ++z) {
                int height = WorldGenerator.findSurface(worldSeed, chunk.x0 + x, chunk.z0 + z);
                height -= 3;
                for (int y = 0; y <= height; ++y) {
                    chunk.setBlockState(x, y, z, Blocks.rock.getDefaultBlockState());
                }
                // Fill the top layer with dirt & grass
                for (int yo = 0; yo <= 2; yo++) {
                    if (yo == 2) {
                        chunk.setBlockState(x, height + yo, z, Blocks.grass.getDefaultBlockState());
                    } else {
                        chunk.setBlockState(x, height + yo, z, Blocks.dirt.getDefaultBlockState());
                    }
                }
            }
        }
    }

}
