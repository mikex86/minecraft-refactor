package com.mojang.minecraft.level.save;

import com.mojang.minecraft.crash.CrashReporter;
import com.mojang.minecraft.level.chunk.Chunk;
import com.mojang.minecraft.util.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class LevelLoader {

    private final File levelFile;
    private final SavingLevelMutex savingLevelState;

    /**
     * Creates a new LevelLoader instance.
     *
     * @param levelFile        the file to load the level data from
     * @param savingLevelState a mutex for ensuring level saving and loading are not concurrent.
     *                         Needs to be sourced from a corresponding {@link LevelSaver} instance.
     */
    public LevelLoader(File levelFile, SavingLevelMutex savingLevelState) {
        this.levelFile = levelFile;
        this.savingLevelState = savingLevelState;
    }

    /**
     * Loads level data for the specified chunk.
     *
     * @param chunk    The chunk to load
     * @param onFinish A callback to be executed after loading the chunk
     *                 The boolean parameter of the callback will be true if the chunk was loaded successfully, false otherwise.
     *                 E.g. if the chunk does not exist in the level file, the argument will be false.
     */
    public void load(Chunk chunk, Consumer<Boolean> onFinish) {
        CompletableFuture.supplyAsync(() -> {
            savingLevelState.acquireLoading();
            boolean noneExist = true;
            try {
                int chunkIndexX = chunk.x0 >> Chunk.CHUNK_SIZE_LG2;
                int chunkIndexZ = chunk.z0 >> Chunk.CHUNK_SIZE_LG2;
                for (int section = 0; section < Chunk.CHUNK_SECTION_COUNT; section++) {
                    try (RegionFile regionFile = RegionFile.getRegionFile(levelFile, chunkIndexX, chunkIndexZ, false)) {
                        if (regionFile != null) {
                            noneExist = false;
                        } else {
                            continue;
                        }
                        byte[] data = regionFile.readSection(chunkIndexX, chunkIndexZ, section);
                        if (data == null) {
                            continue;
                        }
                        chunk.load(section, data);
                    }
                }
                return !noneExist;
            } catch (IOException e) {
                CrashReporter.logException("Failed to load level during chunk load", e);
                return false;
            } finally {
                savingLevelState.releaseLoading();
            }
        }).thenAcceptAsync(onFinish);
    }

}
