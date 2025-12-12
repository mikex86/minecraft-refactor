package com.mojang.minecraft.optim.pools;

import com.mojang.minecraft.level.block.state.BlockState;
import com.mojang.minecraft.util.LongHashMap;

import java.util.ArrayDeque;

/**
 * Note: this pool is "semi-thread-safe".
 * In the cases where we race because of multiple threads, we just accept the fact that we might be "leaking memory",
 * which the GC will eventually clean up.
 * The goal of this class is to minimize allocations, not to never ever hit jvm alloc.
 */
public class BlockStateArrayPool {

    /**
     * Maps the thread id to a long hash map mapping sizes to pool candidates.
     */
    private static final LongHashMap<LongHashMap<ArrayDeque<BlockState[]>>> pooled = new LongHashMap<>();

    private static LongHashMap<ArrayDeque<BlockState[]>> getPooled(long threadId) {
        LongHashMap<ArrayDeque<BlockState[]>> map = pooled.get(threadId);
        if (map == null) {
            map = new LongHashMap<>();
            pooled.put(threadId, map);
        }
        return map;
    }

    public static BlockState[] alloc(int n) {
        Thread thread = Thread.currentThread();
        long threadId = thread.getId();

        LongHashMap<ArrayDeque<BlockState[]>> pooled = getPooled(threadId);
        ArrayDeque<BlockState[]> list = pooled.get(n);
        if (list == null) {
            list = new ArrayDeque<>();
            pooled.put(n, list);
        }
        if (!list.isEmpty()) {
            return list.removeFirst();
        }
        return new BlockState[n];
    }

    public static void release(BlockState[] array) {
        Thread thread = Thread.currentThread();
        long threadId = thread.getId();

        LongHashMap<ArrayDeque<BlockState[]>> pooled = getPooled(threadId);
        ArrayDeque<BlockState[]> list = pooled.get(array.length);
        if (list == null) {
            list = new ArrayDeque<>();
            pooled.put(array.length, list);
        }
        list.addLast(array);
    }
}
