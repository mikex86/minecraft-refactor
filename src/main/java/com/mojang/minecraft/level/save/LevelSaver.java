package com.mojang.minecraft.level.save;

import com.mojang.minecraft.crash.CrashReporter;
import com.mojang.minecraft.level.Chunk;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.zip.GZIPOutputStream;

public class LevelSaver {

    private final SavingLevelMutex savingLevelMutex = new SavingLevelMutex();

    private final File file;

    /**
     * Creates a new LevelSaver instance.
     *
     * @param file The file to save the level data to
     */
    public LevelSaver(File file) {
        this.file = file;
        if (!file.exists()) {
            boolean success = file.mkdirs();
            if (!success) {
                throw new RuntimeException("Failed to create level directory: " + file.getAbsolutePath());
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

    private void writeChunks(List<Chunk> chunks) {
        System.out.println("Saving level...");
        for (Chunk chunk : chunks) {
            String chunkFileName = "chunk_" + chunk.x0 + "_" + chunk.z0 + ".dat";
            File chunkFile = new File(file, chunkFileName);
            try (GZIPOutputStream writer = new GZIPOutputStream(Files.newOutputStream(chunkFile.toPath()))) {
                writer.write(chunk.getBlocks());
            } catch (IOException e) {
                CrashReporter.logException("Failed to write chunk data", e);
            }
        }
        System.out.println("Level saved.");
    }

    public SavingLevelMutex getSavingLevelMutex() {
        return savingLevelMutex;
    }
}
