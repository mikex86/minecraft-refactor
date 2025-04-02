package com.mojang.minecraft.particle;

import com.mojang.minecraft.entity.Player;
import com.mojang.minecraft.level.Level;
import com.mojang.minecraft.renderer.Tesselator;
import com.mojang.minecraft.renderer.TextureManager;
import com.mojang.minecraft.renderer.graphics.GraphicsAPI;
import com.mojang.minecraft.renderer.graphics.Texture;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages particle creation, updates, and rendering in the game world.
 */
public class ParticleEngine {

    private static final float DEG_TO_RAD = (float) (Math.PI / 180.0);

    // Core properties
    protected Level level;
    private final List<Particle> particles = new ArrayList<>();
    private final TextureManager textureManager;

    /**
     * Creates a new particle engine for the specified level.
     *
     * @param level          The game level
     * @param textureManager The texture manager
     */
    public ParticleEngine(Level level, TextureManager textureManager) {
        this.level = level;
        this.textureManager = textureManager;
    }

    /**
     * Adds a particle to the engine.
     *
     * @param particle The particle to add
     */
    public void add(Particle particle) {
        this.particles.add(particle);
        particle.tick(); // tick once to minimize initial jitter
    }

    /**
     * Updates all particles and removes any that have expired.
     */
    public void tick() {
        for (int i = 0; i < this.particles.size(); ++i) {
            Particle particle = this.particles.get(i);
            particle.tick();

            if (particle.removed) {
                this.particles.remove(i--);
            }
        }
    }

    /**
     * Renders all particles in the specified rendering layer.
     *
     * @param graphics    The graphics API
     * @param player      The player (for camera-relative positioning)
     * @param partialTick Partial tick time
     * @param layer       Rendering layer (0 for unlit, 1 for lit)
     */
    public void render(GraphicsAPI graphics, Player player, float partialTick, int layer) {
        if (this.particles.isEmpty()) {
            return;
        }

        // Setup texture
        Texture texture = this.textureManager.loadTexture("/terrain.png", Texture.FilterMode.NEAREST);
        graphics.setTexture(texture);

        // Calculate view vectors based on player rotation
        float xa = -((float) Math.cos(player.yRot * DEG_TO_RAD));
        float za = -((float) Math.sin(player.yRot * DEG_TO_RAD));
        float xa2 = -za * (float) Math.sin(player.xRot * DEG_TO_RAD);
        float za2 = xa * (float) Math.sin(player.xRot * DEG_TO_RAD);
        float ya = (float) Math.cos(player.xRot * DEG_TO_RAD);

        // Setup rendering
        Tesselator tesselator = Tesselator.instance;
        tesselator.init();
        tesselator.color(0.8F, 0.8F, 0.8F);

        // Render each particle
        for (Particle particle : this.particles) {
            // Render particle if it matches the current lighting layer
            if (particle.isLit() ^ layer == 1) {
                particle.render(tesselator, partialTick, xa, ya, za, xa2, za2);
            }
        }

        // Finish rendering
        tesselator.flush();
    }
}
