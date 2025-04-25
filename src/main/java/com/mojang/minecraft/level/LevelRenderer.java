package com.mojang.minecraft.level;

import com.mojang.minecraft.crash.CrashReporter;
import com.mojang.minecraft.entity.Entity;
import com.mojang.minecraft.entity.EntityPlayer;
import com.mojang.minecraft.level.chunk.Chunk;
import com.mojang.minecraft.renderer.Disposable;
import com.mojang.minecraft.renderer.Frustum;
import com.mojang.minecraft.renderer.TextureManager;
import com.mojang.minecraft.renderer.graphics.GraphicsAPI;
import com.mojang.minecraft.renderer.graphics.GraphicsFactory;
import com.mojang.minecraft.renderer.graphics.Texture;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * Handles rendering of the Minecraft level.
 */
public class LevelRenderer implements LevelListener, Disposable {
    // Level data
    private final Level level;

    // Graphics resources
    private final GraphicsAPI graphics;
    private final TextureManager textureManager;

    // Number of chunk sections draw calls issued this frame
    public static int numSectionDrawCalls = 0;

    /**
     * Creates a new GraphicsLevelRenderer for the specified level.
     */
    public LevelRenderer(Level level, TextureManager textureManager) {
        this.level = level;
        this.textureManager = textureManager;
        this.graphics = GraphicsFactory.getGraphicsAPI();

        // Register as a level listener
        level.addListener(this);
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
    public void render(float partialTicks) {
        // Enable texturing and bind the terrain texture
        Texture texture = textureManager.terrainTexture;
        graphics.setTexture(texture);

        // Get the current view frustum
        Frustum frustum = Frustum.getFrustum(graphics);

        // Render all visible chunks
        numSectionDrawCalls = 0;
        for (Chunk chunk : this.level.getLoadedChunks()) {
            if (frustum.isVisible(chunk.aabb)) {
                numSectionDrawCalls += chunk.render(graphics, frustum);
            }
        }
    }

    public void renderEntities(float partialTicks) {
        // Get the current view frustum
        Frustum frustum = Frustum.getFrustum(graphics);

        // Render entities
        for (Entity entity : this.level.getEntities()) {
            if (entity instanceof EntityPlayer) {
                EntityPlayer player = (EntityPlayer) entity;
                if (player.isThePlayer()) {
                    continue;
                }
            }
            if (frustum.isVisible(entity.boundingBox)) {
                entity.render(graphics, textureManager, partialTicks);
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
                            try {
                                chunk.dataMutex.readLock().lock();
                                chunk.rebuild();
                            } finally {
                                chunk.dataMutex.readLock().unlock();
                            }
                            chunk.setRebuildScheduled(false);
                            uploadQueue.add(chunk);
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
    public void updateDirtyChunks(EntityPlayer player) {
        Frustum frustum = Frustum.getFrustum(graphics);
        if (rebuildQueue == null) {
            rebuildQueue = new PriorityBlockingQueue<>(100, new DirtyChunkSorter(player, frustum));
        }
        if (uploadQueue == null) {
            uploadQueue = new PriorityBlockingQueue<>(100, new DirtyChunkSorter(player, frustum));
        }

        // schedule rebuild for all dirty chunks
        {
            List<Chunk> dirtyChunks = this.getAllPendingDirtyChunks();
            if (dirtyChunks != null && !dirtyChunks.isEmpty()) {
                dirtyChunks.sort(new DirtyChunkSorter(player, frustum));
                for (Chunk dirtyChunk : dirtyChunks) {
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
            }
        }
    }

    /**
     * Called when a tile changes.
     */
    @Override
    public void tileChanged(int x, int y, int z) {
    }

    /**
     * Called when a light column changes.
     */
    @Override
    public void lightColumnChanged(int x, int z, int y0, int y1) {
    }

    /**
     * Called when the entire level changes.
     */
    @Override
    public void allChanged() {
        // Mark all chunks as dirty
        for (Chunk chunk : this.level.getLoadedChunks()) {
            chunk.setFullChunkDirty();
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
