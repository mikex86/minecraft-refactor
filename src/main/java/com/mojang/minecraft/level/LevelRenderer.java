package com.mojang.minecraft.level;

import com.mojang.minecraft.crash.CrashReporter;
import com.mojang.minecraft.entity.Entity;
import com.mojang.minecraft.entity.EntityPlayer;
import com.mojang.minecraft.level.chunk.Chunk;
import com.mojang.minecraft.renderer.Disposable;
import com.mojang.minecraft.renderer.Frustum;
import com.mojang.minecraft.renderer.TextureManager;
import com.mojang.minecraft.renderer.graphics.CommandBuffer;
import com.mojang.minecraft.renderer.graphics.MatrixStack;
import com.mojang.minecraft.renderer.graphics.MutableDescriptorSet;
import com.mojang.minecraft.renderer.shader.PipelineRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * Handles rendering of the Minecraft level.
 */
public class LevelRenderer implements Disposable {
    // Level data
    private final Level level;

    // Graphics resources
    private final TextureManager textureManager;
    private final MutableDescriptorSet worldFogTerrainDescriptorSet;

    // Number of chunk sections draw calls issued this frame
    public static int numSectionDrawCalls = 0;

    /**
     * Creates a new GraphicsLevelRenderer for the specified level.
     */
    public LevelRenderer(Level level, TextureManager textureManager, PipelineRegistry pipelineRegistry) {
        this.level = level;
        this.textureManager = textureManager;
        this.worldFogTerrainDescriptorSet = pipelineRegistry.getDescriptorSet(textureManager.terrainTexture, PipelineRegistry.FogPreset.WORLD);
    }

    /**
     * Instance to return in {@link #getAllPendingDirtyChunks()};
     * This makes the function not thread-safe, but this reduces allocation rate
     */
    private final ArrayList<Chunk> dirtyChunks = new ArrayList<>();

    /**
     * Gets all chunks that need to be rebuilt.
     * This function is not thread-safe and should only be called from the main thread.
     */
    public List<Chunk> getAllPendingDirtyChunks() {
        dirtyChunks.clear();
        for (Chunk chunk : this.level.getLoadedChunks()) {
            if (chunk.isDirty() && !chunk.isRebuildScheduled()) {
                dirtyChunks.add(chunk);
            }
        }
        return dirtyChunks;
    }

    /**
     * Renders the level
     */
    public void render(CommandBuffer commandBuffer, MatrixStack matrixStack, float partialTicks) {
        commandBuffer.bindDescriptorSet(worldFogTerrainDescriptorSet);

        // Get the current view frustum
        Frustum frustum = Frustum.getFrustum(matrixStack);

        // Render all visible chunks
        numSectionDrawCalls = 0;
        for (Chunk chunk : this.level.getLoadedChunks()) {
            if (frustum.isVisible(chunk.aabb)) {
                numSectionDrawCalls += chunk.render(commandBuffer, frustum);
            }
        }
    }

    public void renderEntities(CommandBuffer commandBuffer, MatrixStack matrixStack, float partialTicks) {
        // Get the current view frustum
        Frustum frustum = Frustum.getFrustum(matrixStack);

        // Render entities
        for (Entity entity : this.level.getEntities()) {
            if (entity instanceof EntityPlayer) {
                EntityPlayer player = (EntityPlayer) entity;
                if (player.isThePlayer()) {
                    continue;
                }
            }
            if (frustum.isVisible(entity.boundingBox)) {
                entity.render(commandBuffer, matrixStack, textureManager, partialTicks);
            }
        }
    }

    private PriorityBlockingQueue<Chunk> rebuildQueue;
    private PriorityBlockingQueue<Chunk> uploadQueue;

    private static final int REBUILD_THREADS = 16;

    private final Thread[] rebuildThreads = new Thread[REBUILD_THREADS];

    {
        for (int i = 0; i < REBUILD_THREADS; i++) {
            rebuildThreads[i] = new Thread("ChunkRebuildThread-" + i) {
                @Override
                public void run() {
                    while (true) {
                        if (rebuildQueue == null) {
                            Thread.yield();
                            continue;
                        }
                        // process rebuild queue
                        try {
                            Chunk chunk = rebuildQueue.take();
                            boolean requiresUpload = false;
                            try {
                                chunk.dataMutex.readLock().lock();
                                requiresUpload = chunk.rebuild();
                            } finally {
                                chunk.dataMutex.readLock().unlock();
                            }
                            chunk.setRebuildScheduled(false);
                            if (requiresUpload) {
                                uploadQueue.add(chunk);
                            }
                        } catch (InterruptedException e) {
                            // Handle interruption
                            Thread.currentThread().interrupt();
                            break;
                        } catch (Exception e) {
                            CrashReporter.logException("Failed to rebuild chunk", e);
                        }
                    }
                }
            };
            rebuildThreads[i].setDaemon(true);
            rebuildThreads[i].start();
        }
    }

    /**
     * Update chunks that need to be rebuilt.
     */
    public void updateDirtyChunks(CommandBuffer commandBuffer, MatrixStack matrixStack, EntityPlayer player) {
        Frustum frustum = Frustum.getFrustum(matrixStack);
        if (rebuildQueue == null) {
            rebuildQueue = new PriorityBlockingQueue<>(512, new DirtyChunkSorter(player, frustum));
        }
        if (uploadQueue == null) {
            uploadQueue = new PriorityBlockingQueue<>(512, new DirtyChunkSorter(player, frustum));
        }

        // schedule rebuild for all dirty chunks
        {
            List<Chunk> dirtyChunks = this.getAllPendingDirtyChunks();
            if (dirtyChunks != null && !dirtyChunks.isEmpty()) {
                for (Chunk dirtyChunk : dirtyChunks) {
                    if (!frustum.isVisible(dirtyChunk.aabb)) {
                        continue;
                    }
                    rebuildQueue.add(dirtyChunk);
                    dirtyChunk.setRebuildScheduled(true);
                }
            }
        }

        // upload all pending chunks
        while (!uploadQueue.isEmpty()) {
            Chunk chunk = uploadQueue.poll();
            if (chunk != null) {
                chunk.uploadPendingMeshes();
            } else {
                break;
            }
        }
    }

    /**
     * Disposes all chunks and resources when the level is unloaded.
     * This must be called when the level is no longer needed to prevent memory leaks.
     */
    @Override
    public void dispose() {
        for (Chunk chunk : this.level.getLoadedChunks()) {
            if (chunk != null) {
                chunk.dispose();
            }
        }
    }
}
