package com.mojang.minecraft.particle;

import com.mojang.minecraft.entity.Entity;
import com.mojang.minecraft.level.Level;
import com.mojang.minecraft.renderer.Tesselator;

/**
 * Represents a particle in the game world.
 */
public class Particle extends Entity {
    // Velocity components
    private float xd;
    private float yd;
    private float zd;

    // Appearance properties
    public int tex;              // Texture index
    private final float uo;            // Texture U offset
    private final float vo;            // Texture V offset
    private final float size;          // Particle size

    // Lifetime properties
    private int age = 0;
    private final int lifetime;    // Total lifetime in ticks

    private static final int TEXTURE_WIDTH = 16;  // Number of pixels per block
    private static final int SUBTILE_DIVISIONS = 4;     // Each particle uses 1/4 of a tile
    private static final float TEXTURE_BLEED_OFFSET = 0.0001F; // Small offset to prevent texture bleeding
    private static final float PARTICLE_UV_SIZE = (1.0F / (TEXTURE_WIDTH * SUBTILE_DIVISIONS)) - TEXTURE_BLEED_OFFSET;

    /**
     * Creates a new particle with randomized properties.
     *
     * @param level The level the particle exists in
     * @param x     X position
     * @param y     Y position
     * @param z     Z position
     * @param xa    Initial X velocity
     * @param ya    Initial Y velocity
     * @param za    Initial Z velocity
     * @param tex   Texture index
     */
    public Particle(Level level, float x, float y, float z, float xa, float ya, float za, int tex) {
        super(level);
        this.tex = tex;
        this.setSize(0.2F, 0.2F);
        this.heightOffset = this.bbHeight / 2.0F;
        this.setPos(x, y, z);

        // Add some randomness to initial velocity
        this.xd = xa + (float) (Math.random() * 2.0F - 1.0F) * 0.4F;
        this.yd = ya + (float) (Math.random() * 2.0F - 1.0F) * 0.4F;
        this.zd = za + (float) (Math.random() * 2.0F - 1.0F) * 0.4F;

        // Normalize and scale velocity
        float speed = (float) (Math.random() + Math.random() + 1.0F) * 0.15F;
        float magnitude = (float) Math.sqrt(this.xd * this.xd + this.yd * this.yd + this.zd * this.zd);
        this.xd = this.xd / magnitude * speed * 0.4F;
        this.yd = this.yd / magnitude * speed * 0.4F + 0.1F;
        this.zd = this.zd / magnitude * speed * 0.4F;

        // Randomize texture coordinates and appearance
        this.uo = (float) Math.random() * 3.0F;
        this.vo = (float) Math.random() * 3.0F;
        this.size = (float) (Math.random() * 0.5F + 0.5F);

        // Set lifetime based on random factor
        this.lifetime = (int) (4.0F / (Math.random() * 0.9 + 0.1));
    }

    /**
     * Updates the particle position and properties.
     * Removes the particle if its lifetime has expired.
     */
    public void tick() {
        // Store previous position
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;

        // Check if lifetime expired
        if (this.age++ >= this.lifetime) {
            this.remove();
        }

        // Apply gravity and move
        this.yd = (float) (this.yd - 0.04);
        this.move(this.xd, this.yd, this.zd);

        // Apply drag
        this.xd *= 0.98F;
        this.yd *= 0.98F;
        this.zd *= 0.98F;

        // Reduce horizontal velocity when on ground
        if (this.onGround) {
            this.xd *= 0.7F;
            this.zd *= 0.7F;
        }
    }

    /**
     * Renders the particle as a textured quad.
     */
    public void render(Tesselator t, float partialTick, float xa, float ya, float za, float xa2, float za2) {
        // Calculate texture coordinates
        float u0 = ((float) (this.tex % 16) + this.uo / 4.0F) / 16.0F;
        float u1 = u0 + PARTICLE_UV_SIZE;
        float v0 = ((float) (this.tex / 16) + this.vo / 4.0F) / 16.0F;
        float v1 = v0 + PARTICLE_UV_SIZE;

        // Calculate particle size and interpolated position
        float size = 0.1F * this.size;
        float x = this.xo + (this.x - this.xo) * partialTick;
        float y = this.yo + (this.y - this.yo) * partialTick;
        float z = this.zo + (this.z - this.zo) * partialTick;

        // Render the quad
        t.vertexUV(x - xa * size - xa2 * size, y - ya * size, z - za * size - za2 * size, u0, v1);
        t.vertexUV(x - xa * size + xa2 * size, y + ya * size, z - za * size + za2 * size, u0, v0);
        t.vertexUV(x + xa * size + xa2 * size, y + ya * size, z + za * size + za2 * size, u1, v0);
        t.vertexUV(x + xa * size - xa2 * size, y - ya * size, z + za * size - za2 * size, u1, v1);
    }
}
