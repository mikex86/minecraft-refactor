package com.mojang.minecraft;

import com.mojang.minecraft.character.Zombie;
import com.mojang.minecraft.entity.Entity;
import com.mojang.minecraft.entity.Player;
import com.mojang.minecraft.level.Level;
import com.mojang.minecraft.level.LevelRenderer;
import com.mojang.minecraft.particle.ParticleEngine;
import com.mojang.minecraft.renderer.Textures;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages the game state including level, player, entities, and other game objects.
 * Centralizes game state management and updates.
 */
public class GameState {
    // Level and rendering
    private Level level;
    private LevelRenderer levelRenderer;

    // Entities
    private Player player;
    private final List<Entity> entities = new ArrayList<>();

    // Effects
    private ParticleEngine particleEngine;

    // Resources
    private final Textures textures;

    /**
     * Creates a new game state manager.
     *
     * @param textures The texture manager to use
     */
    public GameState(Textures textures) {
        this.textures = textures;
    }

    /**
     * Initializes the game state, creating the level, player, and entities.
     */
    public void initialize() {
        // Create level and renderer
        this.level = new Level(256, 256, 64);
        this.levelRenderer = new LevelRenderer(this.level, this.textures);

        // Create player
        this.player = new Player(this.level);

        // Create particle engine
        this.particleEngine = new ParticleEngine(this.level, this.textures);

        // Add initial entities (zombies)
        for (int i = 0; i < 10; ++i) {
            Zombie zombie = new Zombie(this.level, this.textures, 128.0F, 0.0F, 128.0F);
            zombie.resetPos();
            this.entities.add(zombie);
        }
    }

    /**
     * Updates the game state for one tick.
     * Updates all entities, the player, particle effects, and the level.
     */
    public void tick() {
        // Update all game entities
        for (int i = 0; i < this.entities.size(); ++i) {
            Entity entity = this.entities.get(i);
            entity.tick();
            if (entity.removed) {
                this.entities.remove(i--);
            }
        }

        // Update the player
        this.player.tick();

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
    public Player getPlayer() {
        return player;
    }

    /**
     * Gets the list of entities.
     *
     * @return The entity list
     */
    public List<Entity> getEntities() {
        return entities;
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
        
        // Clear entity lists
        this.entities.clear();
    }
} 