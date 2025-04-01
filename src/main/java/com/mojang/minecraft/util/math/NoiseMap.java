package com.mojang.minecraft.util.math;

import java.util.Random;

/**
 * Generates procedural noise maps for terrain generation.
 * Implements a diamond-square algorithm variant for coherent noise.
 */
public class NoiseMap {
    private final Random random = new Random();
    private final int levels;
    private final int fuzz;

    /**
     * Creates a new noise map with the specified level of detail.
     *
     * @param levels The level of detail for the noise map
     */
    public NoiseMap(int levels) {
        this.levels = levels;
        this.fuzz = 16;
    }

    /**
     * Creates a new noise map with the specified level of detail and fuzz factor.
     *
     * @param levels The level of detail for the noise map
     * @param fuzz   The amount of randomness to apply
     */
    public NoiseMap(int levels, int fuzz) {
        this.levels = levels;
        this.fuzz = fuzz;
    }

    /**
     * Generates a noise map with the specified dimensions.
     * Uses a diamond-square algorithm variant for the noise generation.
     *
     * @param width  The width of the noise map
     * @param height The height of the noise map
     * @return An array containing the generated noise values
     */
    public int[] read(int width, int height) {
        int[] tempValues = new int[width * height];

        // Initial grid step size based on detail level
        int step = width >> levels;

        // Initialize corners and edges with random values
        for (int y = 0; y < height; y += step) {
            for (int x = 0; x < width; x += step) {
                tempValues[x + y * width] = (random.nextInt(256) - 128) * this.fuzz;
            }
        }

        // Refine the noise map with multiple passes of decreasing step size
        for (step = width >> levels; step > 1; step /= 2) {
            int variation = 256 * (step << levels);
            int halfStep = step / 2;

            // Diamond step - compute center from corners
            for (int y = 0; y < height; y += step) {
                for (int x = 0; x < width; x += step) {
                    // Get the four corner values
                    int topLeft = tempValues[(x) % width + (y) % height * width];
                    int topRight = tempValues[(x + step) % width + (y) % height * width];
                    int bottomLeft = tempValues[(x) % width + (y + step) % height * width];
                    int bottomRight = tempValues[(x + step) % width + (y + step) % height * width];

                    // Average the corners and add random variation
                    int center = (topLeft + topRight + bottomLeft + bottomRight) / 4
                            + random.nextInt(variation * 2) - variation;

                    // Set the center value
                    tempValues[x + halfStep + (y + halfStep) * width] = center;
                }
            }

            // Square step - compute edge points from centers and corners
            for (int y = 0; y < height; y += step) {
                for (int x = 0; x < width; x += step) {
                    int center = tempValues[x + y * width];
                    int right = tempValues[(x + step) % width + y * width];
                    int bottom = tempValues[x + (y + step) % width * width];

                    // Compute middleUp (average of center, right, and points above)
                    int middleUp = tempValues[(x + halfStep & width - 1) + (y + halfStep - step & height - 1) * width];

                    // Compute middleLeft (average of center, bottom, and points to the left)
                    int middleLeft = tempValues[(x + halfStep - step & width - 1) + (y + halfStep & height - 1) * width];

                    // Get the center of this cell
                    int middleCenter = tempValues[(x + halfStep) % width + (y + halfStep) % height * width];

                    // Calculate the top edge point
                    int top = (center + right + middleCenter + middleUp) / 4
                            + random.nextInt(variation * 2) - variation;

                    // Calculate the left edge point
                    int left = (center + bottom + middleCenter + middleLeft) / 4
                            + random.nextInt(variation * 2) - variation;

                    // Set the edge points
                    tempValues[x + halfStep + y * width] = top;
                    tempValues[x + (y + halfStep) * width] = left;
                }
            }
        }

        // Scale and normalize the final noise map
        int[] result = new int[width * height];
        for (int y = 0; y < height; ++y) {
            for (int x = 0; x < width; ++x) {
                // Scale the values to a 0-255 range
                result[x + y * width] = tempValues[x % width + y % height * width] / 512 + 128;
            }
        }

        return result;
    }

    /**
     * Sets the seed for the random number generator.
     *
     * @param seed The seed value
     */
    public void setSeed(long seed) {
        random.setSeed(seed);
    }
} 