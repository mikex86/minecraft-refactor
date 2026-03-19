package com.mojang.minecraft.renderer.graphics;

import com.mojang.minecraft.renderer.graphics.opengl.OpenGLGraphicsAPI;

/**
 * Factory for obtaining GraphicsAPI implementations.
 * This allows the game code to get the appropriate implementation
 * without knowing the specific implementation details.
 */
public class GraphicsFactory {
    private static GraphicsAPI instance;
    
    /**
     * Gets or creates the GraphicsAPI instance.
     * Currently only provides an OpenGL implementation.
     * 
     * @return The GraphicsAPI instance
     */
    public static synchronized GraphicsAPI getGraphicsAPI() {
        if (instance == null) {
            // For now, we only have an OpenGL implementation
            instance = new OpenGLGraphicsAPI();
        }
        
        return instance;
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
} 