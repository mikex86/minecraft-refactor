package com.mojang.minecraft.particle;

import com.mojang.minecraft.entity.Player;
import com.mojang.minecraft.level.Level;
import com.mojang.minecraft.renderer.Tesselator;
import com.mojang.minecraft.renderer.Textures;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;

/**
 * Manages particle creation, updates, and rendering in the game world.
 */
public class ParticleEngine {

    private static final float DEG_TO_RAD = (float) (Math.PI / 180.0);

    // Core properties
    protected Level level;
    private final List<Particle> particles = new ArrayList<>();
    private final Textures textures;

    /**
     * Creates a new particle engine for the specified level.
     *
     * @param level    The game level
     * @param textures The texture manager
     */
    public ParticleEngine(Level level, Textures textures) {
        this.level = level;
        this.textures = textures;
    }

    /**
     * Adds a particle to the engine.
     *
     * @param particle The particle to add
     */
    public void add(Particle particle) {
        this.particles.add(particle);
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
     * Renders all particles relative to the player's view.
     *
     * @param player The player viewing the particles
     * @param a      Interpolation factor
     * @param layer  Rendering layer (0 for unlit, 1 for lit)
     */
    public void render(Player player, float a, int layer) {
        if (this.particles.isEmpty()) {
            return;
        }

        // Setup texture
        glEnable(GL_TEXTURE_2D);
        int textureId = this.textures.loadTexture("/terrain.png", GL_NEAREST);
        glBindTexture(GL_TEXTURE_2D, textureId);

        // Calculate view vectors based on player rotation
        float xa = -((float) Math.cos(player.yRot * DEG_TO_RAD));
        float za = -((float) Math.sin(player.yRot * DEG_TO_RAD));
        float xa2 = -za * (float) Math.sin(player.xRot * DEG_TO_RAD);
        float za2 = xa * (float) Math.sin(player.xRot * DEG_TO_RAD);
        float ya = (float) Math.cos(player.xRot * DEG_TO_RAD);

        // Setup rendering
        Tesselator tesselator = Tesselator.instance;
        glColor4f(0.8F, 0.8F, 0.8F, 1.0F);
        tesselator.init();

        // Render each particle
        for (Particle particle : this.particles) {
            // Render particle if it matches the current lighting layer
            if (particle.isLit() ^ layer == 1) {
                particle.render(tesselator, a, xa, ya, za, xa2, za2);
            }
        }

        // Finish rendering
        tesselator.flush();
        glDisable(GL_TEXTURE_2D);
    }
}
