package com.mojang.minecraft.level.save;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

import static com.mojang.minecraft.level.chunk.Chunk.CHUNK_SIZE;
import static com.mojang.minecraft.level.chunk.Chunk.SECTION_HEIGHT;
import static com.mojang.minecraft.level.chunk.Chunk.CHUNK_SECTION_COUNT;

public class RegionFile implements AutoCloseable {

    private static final int REGION_SIZE = 16;

    private final RandomAccessFile raf;

    public RegionFile(File file) throws FileNotFoundException {
        if (!file.getParentFile().exists()) {
            boolean success = file.getParentFile().mkdirs();
            if (!success) {
                throw new RuntimeException("Failed to create region directory: " + file.getParentFile().getAbsolutePath());
            }
        }
        this.raf = new RandomAccessFile(file, "rw");
    }

    public static RegionFile getRegionFile(java.io.File baseDir, int chunkX, int chunkZ, boolean createIfNotExists) throws IOException {
        int regionX = Math.floorDiv(chunkX, REGION_SIZE);
        int regionZ = Math.floorDiv(chunkZ, REGION_SIZE);
        File rf = new java.io.File(baseDir, "region/r." + regionX + "." + regionZ + ".mcr");

        if (!createIfNotExists && !rf.exists()) {
            return null;
        }
        return new RegionFile(rf);
    }

    private static final int NUM_BLOCKS_PER_SECTION = CHUNK_SIZE * CHUNK_SIZE * SECTION_HEIGHT;
    private static final int NUM_BLOCKS_PER_CHUNK = NUM_BLOCKS_PER_SECTION * CHUNK_SECTION_COUNT;

    private static int getSectionOffset(int chunkX, int chunkZ, int section) {
        int localChunkX = chunkX % REGION_SIZE;
        if (localChunkX < 0) {
            localChunkX = -localChunkX;
        }
        int localChunkZ = chunkZ % REGION_SIZE;
        if (localChunkZ < 0) {
            localChunkZ = -localChunkZ;
        }
        int chunkIndex = localChunkX + localChunkZ * REGION_SIZE;
        int chunkStart = NUM_BLOCKS_PER_CHUNK * chunkIndex;
        return chunkStart + section * NUM_BLOCKS_PER_SECTION;
    }

    public void saveSection(int chunkX, int chunkZ, int section, byte[] data) {
        try {
            int offset = getSectionOffset(chunkX, chunkZ, section);
            raf.seek(offset);
            raf.write(data);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save section", e);
        }
    }

    public byte[] readSection(int chunkX, int chunkZ, int section) {
        try {
            int offset = getSectionOffset(chunkX, chunkZ, section);
            byte[] data = new byte[NUM_BLOCKS_PER_SECTION];
            raf.seek(offset);
            raf.readFully(data);
            return data;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read section", e);
        }
    }

    @Override
    public void close() {
        try {
            raf.close();
        } catch (IOException e) {
            throw new RuntimeException("Failed to close region file", e);
        }
    }
}