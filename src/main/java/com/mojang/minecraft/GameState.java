package com.mojang.minecraft;

import com.mojang.minecraft.entity.Entity;
import com.mojang.minecraft.entity.EntityPlayer;
import com.mojang.minecraft.level.Level;
import com.mojang.minecraft.level.LevelRenderer;
import com.mojang.minecraft.particle.ParticleEngine;
import com.mojang.minecraft.renderer.TextureManager;
import com.mojang.minecraft.renderer.model.ModelRegistry;

/**
 * Manages the game state including level, player, entities, and other game objects.
 * Centralizes game state management and updates.
 */
public class GameState {
    // Level and rendering
    private Level level;
    private LevelRenderer levelRenderer;

    // Entities
    private EntityPlayer player;

    // Effects
    private ParticleEngine particleEngine;

    // Resources
    private final TextureManager textureManager;

    // Models
    private final ModelRegistry modelRegistry = ModelRegistry.getInstance();

    /**
     * Creates a new game state manager.
     *
     * @param textureManager The texture manager to use
     */
    public GameState(TextureManager textureManager) {
        this.textureManager = textureManager;
    }

    /**
     * Initializes the game state, creating the level, player, and entities.
     */
    public void initialize() {
        // Create level and renderer
        this.level = new Level();
        this.levelRenderer = new LevelRenderer(this.level, this.textureManager);

        // Create player
        this.player = new EntityPlayer(this.level, true);
        this.player.setPos(0.0F, 256, 0.0F);
        this.level.spawnEntity(this.player);

        // Create particle engine
        this.particleEngine = new ParticleEngine(this.level, this.textureManager);

    }

    /**
     * Updates the game state for one tick.
     * Updates all entities, the player, particle effects, and the level.
     */
    public void tick() {
        // Update particle engine
        this.particleEngine.tick();

        // Update level
        this.level.tick();
    }

    /**
     * Saves the game state (currently just the level).
     */
    public void save() {
        if (this.level != null) {
            this.level.save();
        }
    }

    /**
     * Gets the current level.
     *
     * @return The level
     */
    public Level getLevel() {
        return level;
    }

    /**
     * Gets the level renderer.
     *
     * @return The level renderer
     */
    public LevelRenderer getLevelRenderer() {
        return levelRenderer;
    }

    /**
     * Gets the player.
     *
     * @return The player
     */
    public EntityPlayer getPlayer() {
        return player;
    }

    /**
     * Gets the particle engine.
     *
     * @return The particle engine
     */
    public ParticleEngine getParticleEngine() {
        return particleEngine;
    }

    /**
     * Disposes of all game state resources.
     * Should be called when the game is shutting down.
     */
    public void dispose() {
        // Save the level first
        save();

        // Clean up renderer resources
        if (this.levelRenderer != null) {
            this.levelRenderer.dispose();
        }

        this.modelRegistry.disposeAll();
    }
} 