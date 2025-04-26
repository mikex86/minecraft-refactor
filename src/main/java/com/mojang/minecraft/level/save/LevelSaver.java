package com.mojang.minecraft.level.save;

import com.mojang.minecraft.crash.CrashReporter;
import com.mojang.minecraft.level.chunk.Chunk;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class LevelSaver {

    private final SavingLevelMutex savingLevelMutex = new SavingLevelMutex();

    private final File levelFile;

    /**
     * Creates a new LevelSaver instance.
     *
     * @param levelFile The file to save the level data to
     */
    public LevelSaver(File levelFile) {
        this.levelFile = levelFile;
        if (!levelFile.exists()) {
            boolean success = levelFile.mkdirs();
            if (!success) {
                throw new RuntimeException("Failed to create level directory: " + levelFile.getAbsolutePath());
            }
        }
    }

    /**
     * Task for saving the level to disk.
     * May be null if no save is in progress.
     */
    private CompletableFuture<Void> saveTask = null;

    /**
     * Saves the specified chunks to disk.
     *
     * @param chunks   List of chunks to save
     * @param blocking Whether to block until the save is complete
     */
    public void saveChunks(List<Chunk> chunks, boolean blocking) {
        System.out.println("Saving chunks...");
        if (saveTask != null) {
            saveTask.join();
            saveTask = null;
        }
        saveTask = CompletableFuture.runAsync(() -> {
            savingLevelMutex.acquireSaving();
            try {
                writeChunks(chunks);
            } catch (Throwable e) {
                CrashReporter.logException("Failed to save level", e);
            } finally {
                savingLevelMutex.releaseSaving();
            }
        });
        if (blocking) {
            saveTask.join();
            saveTask = null;
        }
    }

    private void writeChunks(List<Chunk> chunks) throws IOException {
        System.out.println("Saving level...");
        for (Chunk chunk : chunks) {
            int chunkIndexX = chunk.x0 >> Chunk.CHUNK_SIZE_LG2;
            int chunkIndexZ = chunk.z0 >> Chunk.CHUNK_SIZE_LG2;
            try (RegionFile regionFile = RegionFile.getRegionFile(levelFile, chunkIndexX, chunkIndexZ, true)) {
                Objects.requireNonNull(regionFile, "Region file should not be null when createIfNotExists is true");
                for (int section = 0; section < Chunk.CHUNK_SECTION_COUNT; section++) {
                    byte[] blockStateIds = chunk.getBlockStateIds(section);
                    if (blockStateIds == null) {
                        continue;
                    }
                    regionFile.saveSection(chunkIndexX, chunkIndexZ, section, blockStateIds);
                }
            }
        }
        System.out.println("Level saved.");
    }

    public SavingLevelMutex getSavingLevelMutex() {
        return savingLevelMutex;
    }
}
