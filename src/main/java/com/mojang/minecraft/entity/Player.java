package com.mojang.minecraft.entity;

import com.mojang.minecraft.level.Level;

/**
 * Represents the player entity in the game.
 * Handles player movement, input, and interaction with the world.
 */
public class Player extends Entity {

    private static final int CHUNK_LOAD_RADIUS = 8;

    // Input state
    private boolean forward = false;
    private boolean back = false;
    private boolean left = false;
    private boolean right = false;
    private boolean jump = false;
    private boolean sneak = false;

    /**
     * Creates a new Player instance.
     *
     * @param level The level in which the player exists
     */
    public Player(Level level) {
        super(level);
        this.heightOffset = 1.62F; // Eye height offset
    }

    /**
     * Sets the player's input state based on keyboard/controller input.
     *
     * @param forward Whether the forward key is pressed
     * @param back    Whether the back key is pressed
     * @param left    Whether the left key is pressed
     * @param right   Whether the right key is pressed
     * @param jump    Whether the jump key is pressed
     * @param sneak   Whether the sneak key is pressed
     */
    public void setInput(boolean forward, boolean back, boolean left, boolean right, boolean jump, boolean sneak) {
        this.forward = forward;
        this.back = back;
        this.left = left;
        this.right = right;
        this.jump = jump;
        this.sneak = sneak;
    }

    /**
     * Updates the player's position and movement based on keyboard input.
     * Called every game tick.
     */
    @Override
    public void tick() {
        // Store previous position
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;

        float xa = 0.0F; // X movement input
        float ya = 0.0F; // Z movement input (forward/backward)

        // Apply movement based on input state
        if (forward) {
            --ya;
        }

        if (back) {
            ++ya;
        }

        if (left) {
            --xa;
        }

        if (right) {
            ++xa;
        }

        // Jump 
        if (jump && this.onGround) {
            this.yd = 0.5F; // Vertical velocity for jumping
        }

        // Apply movement - different friction when in air vs on ground
        this.moveRelative(xa, ya, this.onGround ? 0.1F : 0.02F);

        // Apply gravity
        this.yd = (float) ((double) this.yd - 0.08);

        // Move based on current velocity
        this.move(this.xd, this.yd, this.zd);

        // Apply air resistance
        this.xd *= 0.91F;
        this.yd *= 0.98F;
        this.zd *= 0.91F;

        // Apply ground friction
        if (this.onGround) {
            this.xd *= 0.7F;
            this.zd *= 0.7F;
        }

        generateChunksAroundPlayer();
    }

    private void generateChunksAroundPlayer() {
        int chunkX = (int) this.x >> 4;
        int chunkZ = (int) this.z >> 4;

        // Load chunks around the player
        for (int x = -CHUNK_LOAD_RADIUS; x <= CHUNK_LOAD_RADIUS; ++x) {
            for (int z = -CHUNK_LOAD_RADIUS; z <= CHUNK_LOAD_RADIUS; ++z) {
                int cx = chunkX + x;
                int cz = chunkZ + z;

                // check if distance is within the load radius
                if (Math.hypot(x, z) > CHUNK_LOAD_RADIUS) {
                    continue;
                }

                // Load the chunk if it's not already loaded
                if (!this.level.isChunkLoaded(cx, cz)) {
                    this.level.loadChunk(cx, cz);
                }
            }
        }
    }
} 