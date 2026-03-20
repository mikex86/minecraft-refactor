package com.mojang.minecraft.renderer.graphics;

import com.mojang.minecraft.renderer.graphics.opengl.OpenGLGraphicsAPI;
import com.mojang.minecraft.renderer.graphics.vulkan.VulkanGraphicsAPI;

import java.util.Locale;

/**
 * Factory for obtaining GraphicsAPI implementations.
 * This allows the game code to get the appropriate implementation
 * without knowing the specific implementation details.
 */
public class GraphicsFactory {
    private static final String BACKEND_SYSTEM_PROPERTY = "neoblaze3d.backend";
    private static GraphicsAPI instance;
    private static Boolean vulkanSupportHint;
    private static long windowHandleHint;
    private static boolean debugModeHint;
    
    /**
     * Gets or creates the GraphicsAPI instance.
     * Backend choice is controlled by {@code -Dneoblaze3d.backend=AUTO|OPENGL|VULKAN}.
     *
     * @return The GraphicsAPI instance
     */
    public static synchronized GraphicsAPI getGraphicsAPI() {
        if (instance == null) {
            instance = createGraphicsAPI(selectBackendPreference());
        }
        
        return instance;
    }

    /**
     * Returns the backend that would currently be selected by factory policy,
     * without creating a GraphicsAPI instance.
     */
    public static synchronized GraphicsAPI.Backend getPreferredBackend() {
        return selectBackendPreference();
    }

    public static synchronized void setVulkanSupportHint(boolean supported) {
        vulkanSupportHint = supported;
    }

    /**
     * Supplies the GLFW window handle to backend implementations that need it (e.g. Vulkan).
     */
    public static synchronized void setWindowHandleHint(long windowHandle) {
        windowHandleHint = windowHandle;
    }

    /**
     * Returns the most recent window handle hint.
     */
    public static synchronized long getWindowHandleHint() {
        return windowHandleHint;
    }

    /**
     * Supplies whether the game is currently running in debug mode.
     */
    public static synchronized void setDebugModeHint(boolean debugMode) {
        debugModeHint = debugMode;
    }

    /**
     * Returns whether debug mode has been enabled by the game.
     */
    public static synchronized boolean isDebugModeHintEnabled() {
        return debugModeHint;
    }
    
    /**
     * Resets the GraphicsAPI instance.
     * This should be called when the graphics context is destroyed.
     */
    public static synchronized void reset() {
        if (instance != null) {
            instance.shutdown();
            instance = null;
        }
    }
    
    // Prevent instantiation
    private GraphicsFactory() {
    }

    private static GraphicsAPI createGraphicsAPI(GraphicsAPI.Backend backend) {
        if (backend == GraphicsAPI.Backend.VULKAN) {
            return new VulkanGraphicsAPI();
        }
        return new OpenGLGraphicsAPI();
    }

    private static GraphicsAPI.Backend selectBackendPreference() {
        String rawPreference = System.getProperty(BACKEND_SYSTEM_PROPERTY, "AUTO");
        String preference = rawPreference == null ? "AUTO" : rawPreference.trim().toUpperCase(Locale.ROOT);

        if ("OPENGL".equals(preference)) {
            return GraphicsAPI.Backend.OPENGL;
        }
        if ("VULKAN".equals(preference)) {
            // Explicit backend request: do not silently fall back.
            // If Vulkan is unavailable, backend initialization should fail loudly.
            return GraphicsAPI.Backend.VULKAN;
        }

        // AUTO
        if (isVulkanSupported()) {
            return GraphicsAPI.Backend.VULKAN;
        }
        return GraphicsAPI.Backend.OPENGL;
    }

    private static boolean isVulkanSupported() {
        if (vulkanSupportHint != null) {
            return vulkanSupportHint;
        }
        return VulkanGraphicsAPI.isRuntimeSupported();
    }
} 
