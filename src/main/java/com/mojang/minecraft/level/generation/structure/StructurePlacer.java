package com.mojang.minecraft.level.generation.structure;

import com.mojang.minecraft.level.Chunk;
import com.mojang.minecraft.level.generation.WorldGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class StructurePlacer {

    private final int worldSeed;

    private int maxSpookyPlacementDistance = 0;

    public StructurePlacer(int worldSeed) {
        this.worldSeed = worldSeed;

        for (Structure structure : Structures.STRUCTURES) {
            int width = structure.getWidth();
            int height = structure.getHeight();
            int depth = structure.getDepth();
            int max = Math.max(width, Math.max(height, depth));
            maxSpookyPlacementDistance = Math.max(maxSpookyPlacementDistance, max);
        }
    }

    public void placeStructures(Chunk chunk) {
        List<StructurePlacement> placements = getSurroundingPlacements(chunk.x0, chunk.z0);
        renderPlacements(chunk, placements);
    }

    /**
     * @return all structures placed in the surrounding chunks that could affect this chunk.
     */
    private List<StructurePlacement> getSurroundingPlacements(int cx0, int cz0) {
        int maxChunkDist = (maxSpookyPlacementDistance + Chunk.CHUNK_SIZE - 1) / Chunk.CHUNK_SIZE;
        List<StructurePlacement> placements = new ArrayList<>();
        for (int x = -maxChunkDist; x <= maxChunkDist; x++) {
            for (int z = -maxChunkDist; z <= maxChunkDist; z++) {
                int cx = cx0 + x * Chunk.CHUNK_SIZE;
                int cz = cz0 + z * Chunk.CHUNK_SIZE;
                placements.addAll(
                        getPlacementsForChunk(cx, cz) // get placements for the surrounding chunks
                );
            }
        }
        removeOverlappingPlacements(placements); // remove overlapping placements
        return placements;
    }

    private void removeOverlappingPlacements(List<StructurePlacement> placements) {
        List<StructurePlacement> toRemove = new ArrayList<>();
        for (StructurePlacement placement : placements) {
            List<StructurePlacement> overlappingPlacements = new ArrayList<>();
            overlappingPlacements.add(placement);
            for (StructurePlacement otherPlacement : placements) {
                if (placement != otherPlacement && placement.overlaps(otherPlacement)) {
                    overlappingPlacements.add(otherPlacement);
                }
            }
            // keep only one with lowest x, y, z
            if (!overlappingPlacements.isEmpty()) {
                StructurePlacement lowest = overlappingPlacements.get(0);
                for (StructurePlacement otherPlacement : overlappingPlacements) {
                    if (otherPlacement.getX0() < lowest.getX0() ||
                            otherPlacement.getY0() < lowest.getY0() ||
                            otherPlacement.getZ0() < lowest.getZ0()) {
                        lowest = otherPlacement;
                    }
                }
                overlappingPlacements.remove(lowest);
                toRemove.addAll(overlappingPlacements);
            }
        }
        placements.removeAll(toRemove);
    }

    private List<StructurePlacement> getPlacementsForChunk(int cx0, int cz0) {
        long chunkSeed = makeChunkSeed(cx0, cz0);

        List<StructurePlacement> placements = new ArrayList<>();

        Random random = new Random(chunkSeed);
        placeTrees(cx0, cz0, random, placements);
        return placements;
    }

    private void renderPlacements(Chunk chunk, List<StructurePlacement> placements) {
        for (StructurePlacement placement : placements) {
            if (!placement.overlapsIntoChunkRange(chunk)) {
                continue; // skip placements that don't overlap with the chunk
            }
            placeStructure(chunk, placement);
        }
    }

    private void placeStructure(Chunk chunk, StructurePlacement placement) {
        placement.getStructure().place(chunk, placement.getX0(), placement.getY0(), placement.getZ0());
    }

    private void placeTrees(int cx, int cz, Random random, List<StructurePlacement> placements) {
        while (random.nextFloat() < 0.5f) {
            placeTree(cx, cz, random, placements);
        }
    }

    private void placeTree(int cx0, int cz0, Random random, List<StructurePlacement> placements) {
        int localX = random.nextInt(Chunk.CHUNK_SIZE);
        int localZ = random.nextInt(Chunk.CHUNK_SIZE);

        Structure structure = Structures.TREE_STRUCTURE;

        int structureWidth = structure.getWidth();
        int structureDepth = structure.getDepth();

        int localY = WorldGenerator.findSurface(this.worldSeed, cx0 + localX + structureWidth / 2, cz0 + localZ + structureDepth / 2);
        StructurePlacement placement = new StructurePlacement(structure, cx0 + localX, localY, localZ + cz0);
        placements.add(placement);
    }

    private long makeChunkSeed(int chunkX, int chunkZ) {
        return (chunkX * 31L + chunkZ) * 31L + worldSeed;
    }

}
