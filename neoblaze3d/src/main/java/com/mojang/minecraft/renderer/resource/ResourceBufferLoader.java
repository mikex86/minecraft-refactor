package com.mojang.minecraft.renderer.resource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.function.IntFunction;

/**
 * Utility for loading classpath resources into caller-controlled byte buffers.
 */
public final class ResourceBufferLoader {

    private ResourceBufferLoader() {
    }

    /**
     * Loads a classpath resource into a destination buffer allocated by the caller strategy.
     *
     * @param anchor       Class used to resolve classpath resources
     * @param resourcePath Classpath resource path
     * @param dstAllocator Allocation strategy for destination buffer
     * @return Destination buffer flipped and ready for reading
     * @throws IOException If resource loading fails
     */
    public static ByteBuffer loadResourceRequired(Class<?> anchor, String resourcePath,
                                                  IntFunction<ByteBuffer> dstAllocator) throws IOException {
        byte[] bytes = readResourceBytesRequired(anchor, resourcePath);
        ByteBuffer dst = dstAllocator.apply(bytes.length);
        if (dst == null) {
            throw new IllegalArgumentException("Destination allocator returned null for resource: " + resourcePath);
        }
        return writeBytesToDestination(bytes, dst, resourcePath);
    }

    /**
     * Loads a classpath resource into the caller-provided destination buffer.
     *
     * @param anchor       Class used to resolve classpath resources
     * @param resourcePath Classpath resource path
     * @param dst          Destination buffer chosen by the caller
     * @return Destination buffer flipped and ready for reading
     * @throws IOException If resource loading fails
     */
    public static ByteBuffer loadResourceRequired(Class<?> anchor, String resourcePath, ByteBuffer dst) throws IOException {
        byte[] bytes = readResourceBytesRequired(anchor, resourcePath);
        return writeBytesToDestination(bytes, dst, resourcePath);
    }

    /**
     * Loads a classpath resource as UTF-8 text.
     *
     * @param anchor       Class used to resolve classpath resources
     * @param resourcePath Classpath resource path
     * @return Resource contents decoded as UTF-8
     * @throws IOException If resource loading fails
     */
    public static String loadUtf8ResourceRequired(Class<?> anchor, String resourcePath) throws IOException {
        byte[] bytes = readResourceBytesRequired(anchor, resourcePath);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static ByteBuffer writeBytesToDestination(byte[] bytes, ByteBuffer dst, String resourcePath) {
        dst.clear();
        if (dst.remaining() < bytes.length) {
            throw new IllegalArgumentException(
                    "Destination buffer capacity (" + dst.capacity() + ") is smaller than resource size (" + bytes.length +
                            ") for resource: " + resourcePath);
        }
        dst.put(bytes);
        dst.flip();
        return dst;
    }

    private static byte[] readResourceBytesRequired(Class<?> anchor, String resourcePath) throws IOException {
        try (InputStream in = anchor.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
            byte[] chunk = new byte[4096];
            int read;
            while ((read = in.read(chunk)) != -1) {
                out.write(chunk, 0, read);
            }
            return out.toByteArray();
        }
    }
}
