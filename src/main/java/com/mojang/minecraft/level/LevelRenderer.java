package com.mojang.minecraft.level;

import com.mojang.minecraft.crash.CrashReporter;
import com.mojang.minecraft.entity.Entity;
import com.mojang.minecraft.entity.EntityPlayer;
import com.mojang.minecraft.level.chunk.Chunk;
import com.mojang.minecraft.renderer.Disposable;
import com.mojang.minecraft.renderer.Frustum;
import com.mojang.minecraft.renderer.TextureManager;
import com.mojang.minecraft.renderer.graphics.CommandBuffer;
import com.mojang.minecraft.renderer.graphics.DrawBatch;
import com.mojang.minecraft.renderer.graphics.MatrixStack;
import com.mojang.minecraft.renderer.graphics.ImmutableDescriptorSet;
import com.mojang.minecraft.renderer.graphics.annotation.RenderThreadOnly;
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
    private final ImmutableDescriptorSet worldFogTerrainDescriptorSet;

    // Number of chunk sections draw calls issued this frame
    public static int numSectionDrawCalls = 0;
    private final DrawBatch chunkDrawBatch = new DrawBatch(2048);

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
        chunkDrawBatch.clear();
        numSectionDrawCalls = 0;
        for (Chunk chunk : this.level.getLoadedChunks()) {
            if (frustum.isVisible(chunk.aabb)) {
                numSectionDrawCalls += chunk.appendDraws(chunkDrawBatch, frustum);
            }
        }
        commandBuffer.drawBatch(chunkDrawBatch);
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
    private volatile boolean running = true;
    private volatile boolean disposed = false;

    private static final int REBUILD_THREADS = 16;

    private final Thread[] rebuildThreads = new Thread[REBUILD_THREADS];

    {
        for (int i = 0; i < REBUILD_THREADS; i++) {
            rebuildThreads[i] = new Thread("ChunkRebuildThread-" + i) {
                @Override
                public void run() {
                    while (running) {
                        if (rebuildQueue == null) {
                            if (!running) {
                                break;
                            }
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
                            if (!running) {
                                break;
                            }
                        } catch (Exception e) {
                            if (!running) {
                                break;
                            }
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
    @RenderThreadOnly
    public void updateDirtyChunks(CommandBuffer commandBuffer, MatrixStack matrixStack, EntityPlayer player) {
        if (!running || disposed) {
            return;
        }
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
     * Stops chunk rebuild workers and clears pending renderer-side queues.
     * Chunk data disposal is handled by level save/unload paths.
     */
    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        running = false;

        for (Thread rebuildThread : rebuildThreads) {
            if (rebuildThread != null) {
                rebuildThread.interrupt();
            }
        }
        for (Thread rebuildThread : rebuildThreads) {
            if (rebuildThread != null) {
                try {
                    rebuildThread.join();
                } catch (InterruptedException ignored) {
                }
            }
        }

        if (rebuildQueue != null) {
            rebuildQueue.clear();
        }
        if (uploadQueue != null) {
            uploadQueue.clear();
        }

        // Do not dispose chunks here: chunk disposal clears block section data,
        // which Level.save() still needs to serialize on shutdown.
        // Chunks are disposed by Level.batchUnloadChunks after save completes.
    }
}
