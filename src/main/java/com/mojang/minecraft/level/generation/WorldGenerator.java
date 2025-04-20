package com.mojang.minecraft.level.generation;

import com.mojang.minecraft.level.Chunk;
import com.mojang.minecraft.level.generation.noise.SimplexNoise;
import com.mojang.minecraft.level.generation.structure.StructurePlacer;

public class WorldGenerator {

    private static final double SCALE_FAC = 0.01;
    private static final int BASE_HEIGHT = 64;
    private static final int NOISE_HEIGHT = 16;

    private final int worldSeed;

    private final StructurePlacer placer;

    public WorldGenerator(int worldSeed) {
        this.worldSeed = worldSeed;
        this.placer = new StructurePlacer(worldSeed);
    }


    public void generate(Chunk chunk) {
        ChunkGenerator chunkGenerator = new ChunkGenerator(this.worldSeed);
        chunkGenerator.generate(chunk);
        placer.placeStructures(chunk);
    }

    private long makeChunkSeed(int chunkX, int chunkZ) {
        return (chunkX * 31L + chunkZ) * 31L + worldSeed;
    }

    public static double noiseOctaves(long seed, double x, double y) {
        return SimplexNoise.noise(x, y) * 0.5 +
                SimplexNoise.noise(x * 2, y * 2) * 0.25 +
                SimplexNoise.noise(x * 4, y * 4) * 0.125 +
                SimplexNoise.noise(x * 8, y * 8) * 0.0625;
    }

    public static int findSurface(int worldSeed, int x, int z) {
        double noise = WorldGenerator.noiseOctaves(worldSeed, x * SCALE_FAC, z * SCALE_FAC);
        return (int) (BASE_HEIGHT + (noise * NOISE_HEIGHT));
    }

}
