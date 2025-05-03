package com.mojang.minecraft.util.threading;

public final class FastThreadLocal<T> {
    private final Object[] values = new Object[128];

    public FastThreadLocal() {
    }

    public void set(T value) {
        int threadId = (int) Thread.currentThread().getId();
        values[threadId] = value;
    }

    @SuppressWarnings("unchecked")
    public T get() {
        int threadId = (int) Thread.currentThread().getId();
        assert threadId < values.length : "Thread ID exceeds array bounds: " + threadId;
        T v = (T) values[threadId];
        return v;
    }
}