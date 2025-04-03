package com.mojang.minecraft.level.save;

import com.mojang.minecraft.crash.CrashReporter;
import com.mojang.minecraft.level.Chunk;
import com.mojang.minecraft.util.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;

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
            try {
                String chunkFileName = "chunk_" + chunk.x + "_" + chunk.z + ".dat";
                File chunkFile = new File(levelFile, chunkFileName);
                if (!chunkFile.exists()) {
                    return false; // Chunk file does not exist
                }
                try (GZIPInputStream dataIn = new GZIPInputStream(Files.newInputStream(chunkFile.toPath()))) {
                    byte[] data = IOUtils.readAllBytes(dataIn);
                    chunk.load(data);
                }
                return true;
            } catch (IOException e) {
                CrashReporter.logException("Failed to load level during chunk load", e);
                return false;
            } finally {
                savingLevelState.releaseLoading();
            }
        }).thenAcceptAsync(onFinish);
    }

}
