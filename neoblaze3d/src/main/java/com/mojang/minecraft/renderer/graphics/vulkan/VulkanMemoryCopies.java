package com.mojang.minecraft.renderer.graphics.vulkan;

import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

final class VulkanMemoryCopies {
    private VulkanMemoryCopies() {
    }

    static void copyByteBuffer(ByteBuffer src, long dstAddress, int sizeInBytes) {
        if (src == null) {
            throw new IllegalArgumentException("src cannot be null");
        }
        ByteBuffer source = src.duplicate();
        if (source.remaining() < sizeInBytes) {
            throw new IllegalArgumentException("Source byte buffer has fewer bytes than requested copy size");
        }

        if (source.isDirect()) {
            long srcAddress = MemoryUtil.memAddress(source);
            MemoryUtil.memCopy(srcAddress, dstAddress, sizeInBytes);
            return;
        }

        ByteBuffer dst = MemoryUtil.memByteBuffer(dstAddress, sizeInBytes);
        int srcPos = source.position();
        for (int i = 0; i < sizeInBytes; i++) {
            dst.put(i, source.get(srcPos + i));
        }
    }

    static void copyIntBuffer(IntBuffer src, long dstAddress, int sizeInBytes) {
        if (src == null) {
            throw new IllegalArgumentException("src cannot be null");
        }
        IntBuffer source = src.duplicate();
        int requiredInts = sizeInBytes / 4;
        if (source.remaining() < requiredInts) {
            throw new IllegalArgumentException("Source int buffer has fewer elements than requested copy size");
        }

        if (source.isDirect()) {
            long srcAddress = MemoryUtil.memAddress(source);
            MemoryUtil.memCopy(srcAddress, dstAddress, sizeInBytes);
            return;
        }

        ByteBuffer dst = MemoryUtil.memByteBuffer(dstAddress, sizeInBytes);
        int srcPos = source.position();
        for (int i = 0; i < requiredInts; i++) {
            dst.putInt(i * 4, source.get(srcPos + i));
        }
    }
}
