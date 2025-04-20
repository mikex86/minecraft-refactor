package com.mojang.minecraft.util.nio;

import com.mojang.minecraft.Minecraft;
import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.ForceInline;

public class NativeByteArray {

    private static Unsafe unsafe = Unsafe.getUnsafe();

    private final long address;
    private final int size;

    private static final boolean DEBUG = Minecraft.DEBUG;
    private boolean disposed = false;

    public NativeByteArray(int size) {
        this.address = unsafe.allocateMemory(size);
        unsafe.setMemory(address, size, (byte)0);
        this.size = size;
    }

    @ForceInline
    public void setByte(int index, byte value) {
        if (DEBUG) {
            if (index < 0 || index >= size) {
                throw new IndexOutOfBoundsException("Index out of bounds: " + index);
            }
            if (disposed) {
                throw new IllegalStateException("Memory has been disposed");
            }
        }
        unsafe.putByte(address + index, value);
    }

    @ForceInline
    public byte getByte(int index) {
        if (DEBUG) {
            if (index < 0 || index >= size) {
                throw new IndexOutOfBoundsException("Index out of bounds: " + index);
            }
            if (disposed) {
                throw new IllegalStateException("Memory has been disposed");
            }
        }
        return unsafe.getByte(address + index);
    }

    public void dispose() {
        disposed = true;
        unsafe.freeMemory(address);
    }

    @ForceInline
    public void setContents(byte[] data) {
        if (data.length > this.size) {
            throw new IllegalArgumentException("Data size exceeds allocated memory");
        }
        for (int i = 0; i < data.length; i++) {
            setByte(i, data[i]);
        }
    }

    @ForceInline
    public byte[] getAsBytes() {
        byte[] data = new byte[size];
        for (int i = 0; i < size; i++) {
            data[i] = getByte(i);
        }
        return data;
    }
}
