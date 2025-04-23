package com.mojang.minecraft.entity;

import com.mojang.minecraft.item.Inventory;
import com.mojang.minecraft.level.Chunk;
import com.mojang.minecraft.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Represents the player entity in the game.
 * Handles player movement, input, and interaction with the world.
 */
public class Player extends Entity {

    // Input state
    private boolean forward = false;
    private boolean back = false;
    private boolean left = false;
    private boolean right = false;
    private boolean jump = false;
    private boolean sneak = false;
    private boolean sprinting = false;

    // Remaining ticks before sprint expires (600 ticks = 30s)
    private int sprintingTicksLeft = 0;

    // FOV smoothing fields
    private float fovModifier = 1.0F;
    private float prevFovModifier = 1.0F;

    // Eye‐height interpolation fields
    private float prevHeightOffset;

    private final Inventory inventory = new Inventory();

    /**
     * Creates a new Player instance.
     *
     * @param level The level in which the player exists
     */
    public Player(Level level) {
        super(level);
        this.heightOffset = 1.62F; // Eye height offset
        this.prevHeightOffset = this.heightOffset;
    }

    /**
     * Sets the player's input state based on keyboard/controller input.
     *
     * @param forward   Whether the forward key is pressed
     * @param back      Whether the back key is pressed
     * @param left      Whether the left key is pressed
     * @param right     Whether the right key is pressed
     * @param jump      Whether the jump key is pressed
     * @param sneak     Whether the sneak key is pressed
     * @param sprinting Whether the sprinting key is pressed
     */
    public void setInput(boolean forward, boolean back, boolean left, boolean right, boolean jump, boolean sneak, boolean sprinting) {
        if (this.inventoryOpen) {
            return;
        }
        this.forward = forward;
        this.back = back;
        this.left = left;
        this.right = right;
        this.jump = jump;
        this.sneak = sneak;

        if (forward && sprinting && !this.sprinting) {
            // When sprinting is started, reset the 30s timer
            this.sprinting = true;
            this.sprintingTicksLeft = 600;
        }
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

        if (!this.inventoryOpen) {
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
        }

        float speed = this.onGround ? 0.1F : 0.02F;

        if (this.sneak) {
            this.sprinting = false;
            speed *= 0.3F;
        }

        if (this.sprinting) {
            speed *= 1.3F;
        }

        // Enable safe walking when sneaking
        this.safeWalk = this.sneak;

        this.moveRelative(xa, ya, speed);

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

        // FOV smoothing
        this.prevFovModifier = this.fovModifier;
        float targetFov = this.getFOVMultiplier();
        this.fovModifier += (targetFov - this.fovModifier) * 0.5F;

        // clamp to [0.1, 1.5] as in vanilla
        if (this.fovModifier < 0.1F) {
            this.fovModifier = 0.1F;
        } else if (this.fovModifier > 1.5F) {
            this.fovModifier = 1.5F;
        }

        // --- Eye‐height interpolation update ---
        this.prevHeightOffset = this.heightOffset;
        this.heightOffset = this.sneak ? 1.54F : 1.62F;

        // Auto-expire after timer runs out
        if (this.sprintingTicksLeft > 0) {
            if (--this.sprintingTicksLeft <= 0) {
                this.sprinting = false;
            }
        }

        // Stop sprinting on any of these conditions
        if (this.sprinting) {
            boolean stop = false;
            if (!this.forward) stop = true;                     // let go of forward
            if (this.back) stop = true;                         // moving backward
            if (this.isCollidedHorizontally) stop = true;       // hit a wall
            if (this.sneak) stop = true;                        // started sneaking

            if (stop) {
                this.sprinting = false;
            }
        }
    }

    private float lastGeneratedPosX = 0;
    private float lastGeneratedPosZ = 0;
    private int generateDelay = -1;
    private final Random random = new Random();

    public void loadAndUnloadChunksAroundPlayer(int renderDistance) {
        if (generateDelay != -1) {
            // Check if the player has moved significantly
            if (Math.abs(this.x - lastGeneratedPosX) < 4 || Math.abs(this.z - lastGeneratedPosZ) < 4) {
                return; // No significant movement, no need to generate chunks
            } else {
                // still delay the generation for a few more frames
                // because we don't want to hit a frame where a tick is performed.
                // if we just naively check this, this condition always turns true in a tick frame - or one after it -
                // and we add more time to an already spiky frame.
                // We don't want to do that, so we wait a random amount of frames until we actually generate the chunks.
                if (generateDelay > 0) {
                    generateDelay--;
                    if (generateDelay > 0) {
                        return;
                    }
                }
            }
        }

        int chunkX = (int) this.x >> Chunk.CHUNK_SIZE_LG2;
        int chunkZ = (int) this.z >> Chunk.CHUNK_SIZE_LG2;

        // Load chunks around the player
        for (int x = -renderDistance; x <= renderDistance; ++x) {
            for (int z = -renderDistance; z <= renderDistance; ++z) {
                int cx = chunkX + x;
                int cz = chunkZ + z;

                // check if distance is within the load radius
                if (Math.hypot(x, z) > renderDistance) {
                    continue;
                }

                // Load the chunk if it's not already loaded
                if (!this.level.isChunkLoaded(cx, cz)) {
                    this.level.loadChunk(cx, cz);
                }
            }
        }

        // Unload chunks that are too far away
        List<Chunk> toUnload = new ArrayList<>();
        for (Chunk loadedChunk : this.level.getLoadedChunks()) {
            int cx = loadedChunk.x0 >> Chunk.CHUNK_SIZE_LG2;
            int cz = loadedChunk.z0 >> Chunk.CHUNK_SIZE_LG2;

            // check if distance is within the load radius
            if (Math.hypot(cx - chunkX, cz - chunkZ) > renderDistance) {
                toUnload.add(loadedChunk);
            }
        }
        if (!toUnload.isEmpty()) {
            this.level.batchUnloadChunks(toUnload, false);
        }
        // Update the last generated position
        this.lastGeneratedPosX = this.x;
        this.lastGeneratedPosZ = this.z;
        this.generateDelay = random.nextInt(10) + 5; // Random delay for chunk generation
    }

    public float getFOVMultiplier() {
        float baseSpeed = this.onGround ? 0.1f : 0.02f;
        float currentSpeed = this.sprinting ? baseSpeed * 1.3f : baseSpeed;

        float speedRatio = currentSpeed / baseSpeed;

        return (speedRatio + 1.0f) * 0.5f;
    }

    /**
     * Returns the per-frame‐interpolated FOV multiplier.
     *
     * @param partialTicks interpolation factor between ticks [0..1)
     * @return smooth FOV factor to multiply your base FOV by
     */
    public float getInterpolatedFOV(float partialTicks) {
        // linear interpolate between last and current smoothed FOV
        return this.prevFovModifier + (this.fovModifier - this.prevFovModifier) * partialTicks;
    }

    /**
     * Returns the per‐frame interpolated eye height (in world units).
     *
     * @param partialTicks fraction of tick [0..1)
     * @return smooth eye‐height offset for the camera
     */
    public float getInterpolatedEyeHeight(float partialTicks) {
        return this.prevHeightOffset + (this.heightOffset - this.prevHeightOffset) * partialTicks;
    }

    public Inventory getInventory() {
        return inventory;
    }

    private boolean inventoryOpen = false;

    public void toggleInventory() {
        if (!this.inventoryOpen) {
            this.inventoryOpen = true;
            this.sprinting = false;
            this.forward = false;
            this.back = false;
            this.left = false;
            this.right = false;
            this.jump = false;
            this.sneak = false;
        } else {
            this.inventoryOpen = false;
        }
    }

    public boolean isInventoryOpen() {
        return inventoryOpen;
    }
}