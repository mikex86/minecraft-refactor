package com.mojang.minecraft.renderer.graphics.opengl;

import com.mojang.minecraft.renderer.GameWindow;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferSubData;
import static org.lwjgl.opengl.GL15.glGetBufferSubData;

class OpenGLPooledAllocatorRegressionTest {
    private static GameWindow window;

    @BeforeAll
    static void initializeGraphicsContext() {
        try {
            window = new GameWindow(64, 64, "buffer-pool-test", false);
        } catch (Throwable t) {
            Assumptions.assumeTrue(false, "Skipping OpenGL pool tests. Could not initialize context: " + t.getMessage());
        }
    }

    @AfterAll
    static void destroyGraphicsContext() {
        if (window != null) {
            window.dispose();
            window = null;
        }
    }

    @Test
    void preservesRegionDataAcrossGrowth() {
        OpenGLPooledAllocator allocator = new OpenGLPooledAllocator(GL_ARRAY_BUFFER, 4096);
        try {
            OpenGLPooledAllocator.BufferRegion regionA = allocator.allocate(1536);
            OpenGLPooledAllocator.BufferRegion regionB = allocator.allocate(1536);
            OpenGLPooledAllocator.BufferRegion regionC = allocator.allocate(768);

            assertNotNull(regionA);
            assertNotNull(regionB);
            assertNotNull(regionC);

            byte[] patternA = generatePattern((int) regionA.getSize(), 0x11);
            byte[] patternB = generatePattern((int) regionB.getSize(), 0x22);
            byte[] patternC = generatePattern((int) regionC.getSize(), 0x33);

            uploadPattern(regionA, patternA);
            uploadPattern(regionB, patternB);
            uploadPattern(regionC, patternC);

            // Usage is >85%, so this allocation should trigger pool growth.
            OpenGLPooledAllocator.BufferRegion regionD = allocator.allocate(512);
            assertNotNull(regionD);

            byte[] patternD = generatePattern((int) regionD.getSize(), 0x44);
            uploadPattern(regionD, patternD);

            assertRegionMatches(regionA, patternA);
            assertRegionMatches(regionB, patternB);
            assertRegionMatches(regionC, patternC);
            assertRegionMatches(regionD, patternD);
        } finally {
            allocator.dispose();
        }
    }

    @Test
    void preservesRegionDataAcrossDefragmentation() {
        final int mib = 1024 * 1024;
        OpenGLPooledAllocator allocator = new OpenGLPooledAllocator(GL_ARRAY_BUFFER, 4L * mib);
        try {
            OpenGLPooledAllocator.BufferRegion regionA = allocator.allocate(mib);
            OpenGLPooledAllocator.BufferRegion regionB = allocator.allocate(mib);
            OpenGLPooledAllocator.BufferRegion regionC = allocator.allocate(mib);

            assertNotNull(regionA);
            assertNotNull(regionB);
            assertNotNull(regionC);

            byte[] patternA = generatePattern((int) regionA.getSize(), 0x55);
            byte[] patternB = generatePattern((int) regionB.getSize(), 0x66);
            byte[] patternC = generatePattern((int) regionC.getSize(), 0x77);

            uploadPattern(regionA, patternA);
            uploadPattern(regionB, patternB);
            uploadPattern(regionC, patternC);

            regionB.free(); // create a 1 MiB hole between two active regions

            // Total free space is enough but no contiguous 1.5 MiB block, forcing defrag/grow path.
            OpenGLPooledAllocator.BufferRegion regionD = allocator.allocate((3 * mib) / 2);
            assertNotNull(regionD);

            byte[] patternD = generatePattern((int) regionD.getSize(), 0x88);
            uploadPattern(regionD, patternD);

            assertRegionMatches(regionA, patternA);
            assertRegionMatches(regionC, patternC);
            assertRegionMatches(regionD, patternD);
        } finally {
            allocator.dispose();
        }
    }

    private static byte[] generatePattern(int size, int seed) {
        byte[] data = new byte[size];
        int state = seed;
        for (int i = 0; i < size; i++) {
            state = state * 1664525 + 1013904223;
            data[i] = (byte) (state >>> 24);
        }
        return data;
    }

    private static void uploadPattern(OpenGLPooledAllocator.BufferRegion region, byte[] data) {
        ByteBuffer buffer = BufferUtils.createByteBuffer(data.length);
        buffer.put(data).flip();

        glBindBuffer(GL_ARRAY_BUFFER, region.getBufferId());
        glBufferSubData(GL_ARRAY_BUFFER, region.getOffset(), buffer);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    private static void assertRegionMatches(OpenGLPooledAllocator.BufferRegion region, byte[] expected) {
        ByteBuffer actual = BufferUtils.createByteBuffer(expected.length);

        glBindBuffer(GL_ARRAY_BUFFER, region.getBufferId());
        glGetBufferSubData(GL_ARRAY_BUFFER, region.getOffset(), actual);
        glBindBuffer(GL_ARRAY_BUFFER, 0);

        for (int i = 0; i < expected.length; i++) {
            int actualByte = actual.get(i) & 0xFF;
            int expectedByte = expected[i] & 0xFF;
            assertEquals(actualByte, expectedByte, "Region mismatch at byte " + i + ", expected " + expectedByte + " but got " + actualByte);
        }
    }
}
