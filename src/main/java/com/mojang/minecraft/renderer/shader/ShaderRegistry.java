package com.mojang.minecraft.renderer.shader;

import com.mojang.minecraft.renderer.Disposable;
import com.mojang.minecraft.renderer.shader.impl.WorldShader;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages shader programs.
 * Provides a central place to load, cache, and retrieve shaders.
 */
public class ShaderRegistry implements Disposable {

    private static ShaderRegistry instance;

    // Cache of loaded shaders
    private final Map<String, Shader> shaders = new HashMap<>();

    // Core shader programs
    private WorldShader worldShader;

    /**
     * Gets the singleton instance of the shader manager.
     *
     * @return The shader manager instance
     */
    public static ShaderRegistry getInstance() {
        if (instance == null) {
            instance = new ShaderRegistry();
        }
        return instance;
    }

    /**
     * Private constructor to enforce singleton pattern.
     */
    private ShaderRegistry() {
    }

    /**
     * Initializes core shaders.
     * Should be called once at the start of the application.
     *
     * @throws IOException If shader loading fails
     */
    public void initialize() throws IOException {
        worldShader = new WorldShader();
    }

    /**
     * Gets the world shader.
     *
     * @return The world shader
     */
    public WorldShader getWorldShader() {
        return worldShader;
    }

    /**
     * Gets a shader by name.
     *
     * @param name The name of the shader
     * @return The shader, or null if not found
     */
    public Shader getShader(String name) {
        return shaders.get(name);
    }

    /**
     * Disposes of all shaders.
     */
    @Override
    public void dispose() {
        for (Shader shader : shaders.values()) {
            shader.dispose();
        }
        shaders.clear();
        instance = null;
    }
} 