package com.mojang.minecraft.level;

import com.mojang.minecraft.level.tile.Tile;
import com.mojang.minecraft.util.math.NoiseMap;

import java.util.Random;

/**
 * Procedurally generates a Minecraft level.
 */
public class LevelGen {
    private final int width;
    private final int height;
    private final int depth;
    private final Random random = new Random();

    /**
     * Creates a new level generator with the specified dimensions.
     */
    public LevelGen(int width, int height, int depth) {
        this.width = width;
        this.height = height;
        this.depth = depth;
    }

    /**
     * Generates a new map with terrain and caves.
     */
    public byte[] generateMap() {
        // Generate heightmaps and other terrain data
        int[] heightmap1 = new NoiseMap(0).read(width, height);
        int[] heightmap2 = new NoiseMap(0).read(width, height);
        int[] blendMap = new NoiseMap(1).read(width, height);
        int[] rockMap = new NoiseMap(1).read(width, height);

        byte[] blocks = new byte[this.width * this.height * this.depth];

        // Generate terrain using the heightmaps
        for (int x = 0; x < width; ++x) {
            for (int y = 0; y < depth; ++y) {
                for (int z = 0; z < height; ++z) {
                    int dh1 = heightmap1[x + z * this.width];
                    int dh2 = heightmap2[x + z * this.width];
                    int blendFactor = blendMap[x + z * this.width];

                    // Select the appropriate heightmap based on the blend factor
                    if (blendFactor < 128) {
                        dh2 = dh1;
                    }

                    // Use the higher of the two heightmaps
                    int finalHeight = Math.max(dh1, dh2);

                    // Scale the height to fit the world
                    finalHeight = finalHeight / 8 + depth / 3;

                    // Calculate rock layer height
                    int rockHeight = rockMap[x + z * this.width] / 8 + depth / 3;

                    // Ensure rock is below dirt
                    if (rockHeight > finalHeight - 2) {
                        rockHeight = finalHeight - 2;
                    }

                    // Calculate the index in the blocks array
                    int index = (y * this.height + z) * this.width + x;

                    // Set the appropriate tile type based on depth
                    int tileId = 0;
                    if (y == finalHeight) {
                        tileId = Tile.grass.id;
                    } else if (y < finalHeight) {
                        tileId = Tile.dirt.id;
                    } else if (y <= rockHeight) {
                        tileId = Tile.rock.id;
                    }

                    blocks[index] = (byte) tileId;
                }
            }
        }

        // Generate caves
        int caveCount = width * height * depth / 256 / 64;

        for (int i = 0; i < caveCount; ++i) {
            // Cave starting position
            float x = this.random.nextFloat() * width;
            float y = this.random.nextFloat() * depth;
            float z = this.random.nextFloat() * height;

            // Cave properties
            int caveLength = (int) (this.random.nextFloat() + this.random.nextFloat() * 150.0F);

            // Cave direction vectors and perturbations
            float horizAngle = (float) (this.random.nextFloat() * Math.PI * 2.0F);  // Horizontal angle
            float horizDelta = 0.0F;  // Horizontal angle change rate
            float vertAngle = (float) (this.random.nextFloat() * Math.PI * 2.0F);   // Vertical angle
            float vertDelta = 0.0F;   // Vertical angle change rate

            // Carve the cave
            for (int segmentIndex = 0; segmentIndex < caveLength; ++segmentIndex) {
                // Move in the current direction
                x += (float) (Math.sin(horizAngle) * Math.cos(vertAngle));
                z += (float) (Math.cos(horizAngle) * Math.cos(vertAngle));
                y += (float) Math.sin(vertAngle);

                // Gradually change the direction
                horizAngle += horizDelta * 0.2F;
                horizDelta *= 0.9F;
                horizDelta += this.random.nextFloat() - this.random.nextFloat();

                vertAngle += vertDelta * 0.5F;
                vertAngle *= 0.5F;
                vertDelta *= 0.9F;
                vertDelta += this.random.nextFloat() - this.random.nextFloat();

                // Vary the cave size with a sine wave
                float size = (float) (Math.sin((double) segmentIndex * Math.PI / (double) caveLength) * 2.5F + 1.0F);

                // Carve out blocks in a sphere around the current position
                for (int dx = (int) (x - size); dx <= (int) (x + size); ++dx) {
                    for (int dy = (int) (y - size); dy <= (int) (y + size); ++dy) {
                        for (int dz = (int) (z - size); dz <= (int) (z + size); ++dz) {
                            // Calculate distance from cave center
                            float distX = dx - x;
                            float distY = dy - y;
                            float distZ = dz - z;

                            // Vertical distance counts double (flatter caves)
                            float distSq = distX * distX + distY * distY * 2.0F + distZ * distZ;

                            // If within the cave radius and within world bounds
                            if (distSq < size * size &&
                                    dx >= 1 && dy >= 1 && dz >= 1 &&
                                    dx < this.width - 1 && dy < this.depth - 1 && dz < this.height - 1) {

                                int blockIndex = (dy * this.height + dz) * this.width + dx;

                                // Only carve out rock blocks
                                if (blocks[blockIndex] == Tile.rock.id) {
                                    blocks[blockIndex] = 0;
                                }
                            }
                        }
                    }
                }
            }
        }

        return blocks;
    }
}
