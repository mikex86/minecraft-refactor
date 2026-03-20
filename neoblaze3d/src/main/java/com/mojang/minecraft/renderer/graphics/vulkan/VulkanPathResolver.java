package com.mojang.minecraft.renderer.graphics.vulkan;

import java.io.InputStream;

final class VulkanPathResolver {
    private VulkanPathResolver() {
    }

    static String resolve(String requestedPath) {
        if (requestedPath == null) {
            throw new IllegalArgumentException("requestedPath cannot be null");
        }
        String vkPath = toVkPath(requestedPath);
        InputStream vkStream = VulkanPathResolver.class.getResourceAsStream(vkPath);
        if (vkStream != null) {
            try {
                vkStream.close();
            } catch (Exception ignored) {
            }
            return vkPath;
        }
        return requestedPath;
    }

    private static String toVkPath(String requestedPath) {
        int dot = requestedPath.lastIndexOf(".spv");
        if (dot < 0) {
            return requestedPath;
        }
        return requestedPath.substring(0, dot) + ".vk.spv";
    }
}
