package com.mojang.minecraft.level.generation;

import com.mojang.minecraft.level.Chunk;
import com.mojang.minecraft.level.generation.noise.SimplexNoise;
import com.mojang.minecraft.level.tile.Tile;

public class ChunkGenerator {

    private static final double SCALE_FAC = 0.01;
    private static final int BASE_HEIGHT = 64;
    private static final int NOISE_HEIGHT = 16;

    private final int worldSeed;
    private final int chunkSeed;

    public ChunkGenerator(int worldSeed, int chunkSeed) {
        this.worldSeed = worldSeed;
        this.chunkSeed = chunkSeed;
    }

    public void generate(Chunk chunk) {
        for (int x = 0; x < Chunk.CHUNK_SIZE; ++x) {
            for (int z = 0; z < Chunk.CHUNK_SIZE; ++z) {
                double noise = noiseOctaves((chunk.x0 + x) * SCALE_FAC, (chunk.z0 + z) * SCALE_FAC);
                int height = (int) (BASE_HEIGHT + (noise * NOISE_HEIGHT));
                for (int y = 0; y <= height; ++y) {
                    chunk.setTile(x, y, z, Tile.rock.id);
                }
                // Fill the top layer with dirt & grass
                for (int yo = 0; yo <= 2; yo++) {
                    if (yo == 2) {
                        chunk.setTile(x, height + yo, z, Tile.grass.id);
                    } else {
                        chunk.setTile(x, height + yo, z, Tile.dirt.id);
                    }
                }
            }
        }
    }

    private double noiseOctaves(double x, double y) {
        return SimplexNoise.noise(x, y) * 0.5 +
                SimplexNoise.noise(x * 2, y * 2) * 0.25 +
                SimplexNoise.noise(x * 4, y * 4) * 0.125 +
                SimplexNoise.noise(x * 8, y * 8) * 0.0625;
    }
}
