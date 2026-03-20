package com.mojang.minecraft.renderer.graphics.vulkan;

import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFWVulkan;
import org.lwjgl.system.FunctionProvider;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VK;

import java.util.LinkedHashSet;
import java.util.Set;

final class VulkanLoader {
    private static boolean initialized;
    private static boolean supported;
    private static String diagnostics = "Vulkan loader not initialized";

    private VulkanLoader() {
    }

    static synchronized boolean ensureGlfwVulkanLoaded() {
        if (initialized && supported) {
            return supported;
        }

        StringBuilder diag = new StringBuilder();
        supported = probeSupport(diag);
        diagnostics = diag.toString();
        initialized = true;
        return supported;
    }

    static synchronized String getDiagnostics() {
        return diagnostics;
    }

    private static boolean probeSupport(StringBuilder diag) {
        if (tryGlfwDirect(diag)) {
            return true;
        }

        Set<String> candidates = new LinkedHashSet<String>();

        addIfPresent(candidates, System.getProperty("neoblaze3d.vulkan.loader.path"));
        addIfPresent(candidates, System.getenv("NEOBLAZE3D_VULKAN_LOADER"));
        addIfPresent(candidates, System.getenv("VULKAN_LIBRARY"));

        String javaHome = System.getProperty("java.home");
        if (javaHome != null && !javaHome.trim().isEmpty()) {
            candidates.add(javaHome + "/lib/libvulkan.so.1");
            candidates.add(javaHome + "/lib/libvulkan.so");
        }

        // Common Linux/NixOS loader locations and names.
        candidates.add("/run/opengl-driver/lib/libvulkan.so.1");
        candidates.add("/run/opengl-driver/lib/libvulkan.so");
        candidates.add("/usr/lib/x86_64-linux-gnu/libvulkan.so.1");
        candidates.add("/usr/lib64/libvulkan.so.1");
        candidates.add("/usr/lib/libvulkan.so.1");
        candidates.add("libvulkan.so.1");
        candidates.add("libvulkan.so");

        // Prefer explicit GLFW loader path probes first. This avoids poisoning
        // org.lwjgl.vulkan.VK static initialization if default VK loading fails.
        for (String candidate : candidates) {
            if (tryGlfwPath(candidate, diag)) {
                return true;
            }
        }

        // Then try explicit VK loader initialization + glfwInitVulkanLoader.
        for (String candidate : candidates) {
            if (tryInitViaVkCreate(candidate, diag)) {
                return true;
            }
        }

        // Last resort: default VK loading path.
        if (tryInitViaVkCreate(null, diag)) {
            return true;
        }

        return false;
    }

    private static boolean tryGlfwDirect(StringBuilder diag) {
        try {
            boolean ok = hasUsableGlfwVulkanSupport();
            appendResult(diag, "glfwVulkanSupported(direct)", ok, ok ? null : new IllegalStateException("required instance extensions unavailable"));
            return ok;
        } catch (Throwable t) {
            appendResult(diag, "glfwVulkanSupported(direct)", false, t);
            return false;
        }
    }

    private static boolean tryGlfwPath(String path, StringBuilder diag) {
        try {
            GLFWVulkan.setPath(path);
            boolean ok = hasUsableGlfwVulkanSupport();
            appendResult(diag, "glfwVulkanSupported(setPath=" + path + ")", ok, ok ? null : new IllegalStateException("required instance extensions unavailable"));
            return ok;
        } catch (Throwable t) {
            appendResult(diag, "glfwVulkanSupported(setPath=" + path + ")", false, t);
            return false;
        }
    }

    private static boolean tryInitViaVkCreate(String path, StringBuilder diag) {
        try {
            safeDestroyVk();
            if (path == null) {
                VK.create();
            } else {
                VK.create(path);
            }

            FunctionProvider provider = VK.getFunctionProvider();
            if (provider == null) {
                appendResult(diag, "VK.create(" + pathLabel(path) + ")", false, new IllegalStateException("FunctionProvider is null"));
                return false;
            }

            long vkGetInstanceProcAddr = provider.getFunctionAddress("vkGetInstanceProcAddr");
            if (vkGetInstanceProcAddr == MemoryUtil.NULL) {
                appendResult(diag, "VK.create(" + pathLabel(path) + ")", false, new IllegalStateException("vkGetInstanceProcAddr is null"));
                return false;
            }

            GLFWVulkan.glfwInitVulkanLoader(vkGetInstanceProcAddr);
            boolean ok = hasUsableGlfwVulkanSupport();
            appendResult(diag, "VK.create(" + pathLabel(path) + ") + glfwInitVulkanLoader", ok, ok ? null : new IllegalStateException("required instance extensions unavailable"));
            if (!ok) {
                safeDestroyVk();
            }
            return ok;
        } catch (Throwable t) {
            appendResult(diag, "VK.create(" + pathLabel(path) + ") + glfwInitVulkanLoader", false, t);
            safeDestroyVk();
            return false;
        }
    }

    private static void addIfPresent(Set<String> candidates, String value) {
        if (value == null) {
            return;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        candidates.add(trimmed);
    }

    private static String pathLabel(String path) {
        return path == null ? "default" : path;
    }

    private static void appendResult(StringBuilder diag, String attempt, boolean ok, Throwable error) {
        if (diag.length() > 0) {
            diag.append('\n');
        }
        diag.append(attempt).append(" -> ").append(ok ? "OK" : "FAIL");
        if (error != null) {
            diag.append(" (").append(error.getClass().getSimpleName());
            if (error.getMessage() != null) {
                diag.append(": ").append(error.getMessage());
            }
            diag.append(')');
        }
    }

    private static void safeDestroyVk() {
        try {
            if (VK.getFunctionProvider() != null) {
                VK.destroy();
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean hasUsableGlfwVulkanSupport() {
        if (!GLFWVulkan.glfwVulkanSupported()) {
            return false;
        }
        PointerBuffer requiredExtensions = GLFWVulkan.glfwGetRequiredInstanceExtensions();
        return requiredExtensions != null && requiredExtensions.remaining() > 0;
    }
}
