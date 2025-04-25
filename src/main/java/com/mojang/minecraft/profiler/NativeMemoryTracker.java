package com.mojang.minecraft.profiler;

import java.util.concurrent.atomic.AtomicLong;

public class NativeMemoryTracker {

    public static final AtomicLong ALLOCATED_NATIVE_MEMORY = new AtomicLong(0);

}
