package com.mojang.minecraft.optim.pools;

import com.mojang.minecraft.renderer.Tesselator;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ChunkBuildTesselatorPool {

    private static final int MAX_POOL_SIZE = 16;
    private static final Queue<Tesselator> POOL = new ConcurrentLinkedQueue<>();

    public static Tesselator obtain() {
        Tesselator tesselator = POOL.poll();
        if (tesselator == null) {
            tesselator = new Tesselator();
        }
        return tesselator;
    }

    public static void release(Tesselator tesselator) {
        if (POOL.size() >= MAX_POOL_SIZE) {
            tesselator.dispose();
            return;
        }
        tesselator.clear();
        POOL.offer(tesselator);
    }

}
