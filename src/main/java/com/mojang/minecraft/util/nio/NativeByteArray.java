package com.mojang.minecraft.util.nio;

import com.mojang.minecraft.Minecraft;
import org.lwjgl.system.jemalloc.JEmalloc;

import java.nio.ByteBuffer;

public class NativeByteArray {

    private final ByteBuffer buffer;
    private final int size;

    private static final boolean DEBUG = Minecraft.DEBUG;
    private boolean disposed = false;

    public NativeByteArray(int size) {
        this.buffer = JEmalloc.je_calloc(size, 1);
        this.size = size;
    }

    public void setByte(int index, byte value) {
        if (DEBUG) {
            if (index < 0 || index >= size) {
                throw new IndexOutOfBoundsException("Index out of bounds: " + index);
            }
            if (disposed) {
                throw new IllegalStateException("Memory has been disposed");
            }
        }
        this.buffer.put(index, value);
    }

    public byte getByte(int index) {
        if (DEBUG) {
            if (index < 0 || index >= size) {
                throw new IndexOutOfBoundsException("Index out of bounds: " + index);
            }
            if (disposed) {
                throw new IllegalStateException("Memory has been disposed");
            }
        }
        return this.buffer.get(index);
    }

    public void dispose() {
        this.disposed = true;
        JEmalloc.je_free(this.buffer);
    }

    public void setContents(byte[] data) {
        if (data.length > this.size) {
            throw new IllegalArgumentException("Data size exceeds allocated memory");
        }
        for (int i = 0; i < data.length; i++) {
            setByte(i, data[i]);
        }
    }

    public byte[] getAsBytes() {
        byte[] data = new byte[size];
        for (int i = 0; i < size; i++) {
            data[i] = getByte(i);
        }
        return data;
    }
}
