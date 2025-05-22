package com.mojang.minecraft.entity;

import com.mojang.minecraft.item.Item;
import com.mojang.minecraft.item.inventory.Inventory;
import com.mojang.minecraft.level.Level;
import com.mojang.minecraft.level.chunk.Chunk;
import com.mojang.minecraft.renderer.TextureManager;
import com.mojang.minecraft.renderer.graphics.GraphicsAPI;
import com.mojang.minecraft.renderer.model.Model;
import com.mojang.minecraft.renderer.model.ModelRegistry;
import com.mojang.minecraft.renderer.model.impl.PlayerModel;
import com.mojang.minecraft.world.HitResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static java.lang.Math.abs;

/**
 * Represents the player entity in the game.
 * Handles player movement, input, and interaction with the world.
 */
public class EntityPlayer extends EntityLiving {

    // Input state
    private boolean forward = false;
    private boolean back = false;
    private boolean left = false;
    private boolean right = false;
    private boolean jump = false;
    private boolean sneak = false;
    private boolean sprinting = false;

    /**
     * State whether this player instance is the local input-controlled player.
     */
    private boolean isThePlayer = false;

    public float cameraYaw = 0.0F; // Camera yaw
    public float cameraPitch = 0.0F; // Camera pitch

    // Remaining ticks before sprint expires (600 ticks = 30s)
    private int sprintingTicksLeft = 0;

    // FOV smoothing fields
    private float fovModifier = 1.0F;
    private float prevFovModifier = 1.0F;

    // Body rotation smoothing fields
    public float bodyYaw = 0.0F;
    public float prevBodyYaw = 0.0F;

    // Eye‐height interpolation fields
    private float prevHeightOffset;

    // Distance walked fields
    public float distanceWalked = 0.0F;
    public float prevDistanceWalked = 0.0F;

    // View bobbing state
    public float bob = 0.0F;
    public float prevBob = 0.0F;

    private final Inventory inventory = new Inventory();

    // Animation constants
    public static final float MODEL_SIZE = 0.058333334F;
    public static final float MODEL_Y_OFFSET = -23.0F;
    private static final float DEGREES_TO_RADIANS = (float) (180.0F / Math.PI);

    public static final Model<EntityPlayer> PLAYER_MODEL = ModelRegistry.getInstance().getModel("player", PlayerModel::new);

    /**
     * The current hit result of the player.
     * Set externally by Minecraft.java
     */
    private HitResult currentHitResult = null;

    /**
     * Creates a new Player instance.
     *
     * @param level The level in which the player exists
     */
    public EntityPlayer(Level level, boolean isThePlayer) {
        super(level);
        this.heightOffset = 1.62F; // Eye height offset
        this.prevHeightOffset = this.heightOffset;
        this.isThePlayer = isThePlayer;
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

        if (forward && !this.isCollidedHorizontally) {
            if (!this.sprinting && sprinting) {
                // When sprinting is started, reset the 30s timer
                this.sprinting = true;
                this.sprintingTicksLeft = 600;
            }
        } else {
            this.sprinting = false;
            this.sprintingTicksLeft = 0;
        }
    }


    /**
     * Updates the player's position and movement based on keyboard input.
     * Called every game tick.
     */
    @Override
    public void tick() {
        super.tick();

        List<Entity> nearby = level.getNearbyEntitiesExcluding(
                this,
                boundingBox.grow(1, 0, 1)
        );
        for (Entity e : nearby) {
            e.onCollideWithPlayer(this);
        }

        this.pitch = this.cameraPitch;
        this.yaw = this.cameraYaw;

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
                this.yd = 0.42F; // Vertical velocity for jumping
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

        // Move based on current velocity
        this.move(this.xd, this.yd, this.zd);
        // Apply gravity
        this.yd -= 0.08F;

        // Track distance walked
        double dx = this.x - this.xo;
        double dz = this.z - this.zo;
        this.prevDistanceWalked = this.distanceWalked;
        float horizontalDelta = (float) Math.sqrt(dx * dx + dz * dz);
        this.distanceWalked += horizontalDelta * 0.6F;

        // Apply ground friction
        if (this.onGround) {
            float slipperyFactor = 0.6F;
            this.xd *= slipperyFactor * 0.91F;
            this.yd *= 0.98F;
            this.zd *= slipperyFactor * 0.91F;
        } else {
            // Apply air resistance
            this.xd *= 0.91F;
            this.yd *= 0.98F;
            this.zd *= 0.91F;
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

        // --- Body rotation smoothing ---
        this.prevBodyYaw = this.bodyYaw;
        float movementThreshold = 0.001F;
        if (abs(dx) > movementThreshold || abs(dz) > movementThreshold) {
            float movementYaw = (float) (Math.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
            float angleDiff = wrapDegrees(this.yaw - movementYaw);
            if (angleDiff > 95.0F || angleDiff < -95.0F) {
                movementYaw -= 180.0F;
            }

            this.bodyYaw += wrapDegrees(movementYaw - this.bodyYaw) * 0.3F;
        }

        // Clamp head‐to‐body angle to ±50°
        float headDiff = wrapDegrees(this.yaw - this.bodyYaw);
        if (headDiff < -50) headDiff = -50f;
        else if (headDiff > 50) headDiff = 50f;
        this.bodyYaw = this.yaw - headDiff;

        updateAnimations();

        // -- View bobbing
        this.prevBob = this.bob;
        float horizontalMotion = (float) Math.sqrt(xd * xd + zd * zd);
        float bobSpeed = onGround ? Math.min(0.1F, horizontalMotion) : 0.0F;
        this.bob += (bobSpeed - this.bob) * 0.4F;
    }


    /**
     * Attempts to pick up an item.
     * @param item The item to pick up
     * @return true if the item was picked up, false otherwise
     */
    public boolean attemptPickupItem(Item item) {
        return inventory.addItem(item, true);
    }

    /**
     * The current position of the block being broken.
     * Not invalidated after the block-breaking state changes back to false.
     * Minecraft.java will access this variable to execute the block-breaking action.
     * Minecraft.java will call {@link #resetBreakingBlockPos()} afterward.
     */
    public int breakingBlockX, breakingBlockY, breakingBlockZ;
    private boolean breakingBlock = false;
    private int blockBreakingProgress = 0;

    public void resetBreakingBlockPos() {
        this.breakingBlockX = 0;
        this.breakingBlockY = 0;
        this.breakingBlockZ = 0;
        this.breakingBlock = false;
        System.out.println("Resetting breaking block position");
    }

    public void setBreakingBlockPos(int x, int y, int z) {
        if (this.breakingBlockX == x && this.breakingBlockY == y && this.breakingBlockZ == z) {
            return; // No change in position
        }
        this.breakingBlockX = x;
        this.breakingBlockY = y;
        this.breakingBlockZ = z;
        this.breakingBlock = true;
        this.blockBreakingProgress = 0;
        System.out.println("Breaking block at " + x + ", " + y + ", " + z);
    }

    public void setCurrentHitResult(HitResult hitResult) {
        this.currentHitResult = hitResult;
    }

    private HitResult lastHitResultBlockBreaking = null;

    /**
     * Called by Minecraft.java to update the block breaking progress for the single player.
     * @return true if the current block should be broken, false otherwise
     */
    public boolean updateBlockBreaking() {
        // Update block breaking progress
        if (this.breakingBlock) {
            // abort block breaking if the hit result changes block position
            if (this.lastHitResultBlockBreaking != null && this.currentHitResult != null) {
                int lastX = this.lastHitResultBlockBreaking.x;
                int lastY = this.lastHitResultBlockBreaking.y;
                int lastZ = this.lastHitResultBlockBreaking.z;
                int currentX = this.currentHitResult.x;
                int currentY = this.currentHitResult.y;
                int currentZ = this.currentHitResult.z;
                if (lastX != currentX || lastY != currentY || lastZ != currentZ) {
                    System.out.println("Block breaking aborted: hit result changed");
                    this.breakingBlock = false;
                    this.blockBreakingProgress = 0;
                }
            }
            if (this.breakingBlock) {
                this.blockBreakingProgress++;
                if (this.blockBreakingProgress >= 10) {
                    this.blockBreakingProgress = 0;
                    this.breakingBlock = false;
                    return true;
                }
            }
        }
        this.lastHitResultBlockBreaking = this.currentHitResult;
        return false;
    }

    public boolean isBreakingBlock() {
        return breakingBlock;
    }

    /**
     * Renders the zombie entity.
     *
     * @param partialTicks Partial tick time for smooth animation
     */
    @Override
    public void render(GraphicsAPI graphics, TextureManager textureManager, float partialTicks) {
        graphics.setTexture(textureManager.charTexture);

        graphics.pushMatrix();

        // Position at interpolated location
        graphics.translate(
                this.xo + (this.x - this.xo) * partialTicks,
                this.yo + (this.y - this.yo) * partialTicks,
                this.zo + (this.z - this.zo) * partialTicks
        );

        // Apply scaling and orientation
        graphics.scale(-MODEL_SIZE, -MODEL_SIZE, -MODEL_SIZE);
        graphics.translate(0.0F, MODEL_Y_OFFSET, 0.0F);

        // Render the model
        PLAYER_MODEL.render(graphics, this, partialTicks);

        graphics.popMatrix();
    }

    private float lastGeneratedPosX = 0;
    private float lastGeneratedPosZ = 0;
    private int generateDelay = -1;
    private final Random random = new Random();

    private static class ChunkPos {
        public int x;
        public int z;

        public ChunkPos(int x, int z) {
            this.x = x;
            this.z = z;
        }
    }

    public void loadAndUnloadChunksAroundPlayer(int renderDistance) {
        if (generateDelay != -1) {
            // Check if the player has moved significantly
            if (abs(this.x - lastGeneratedPosX) < 4 || abs(this.z - lastGeneratedPosZ) < 4) {
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
        List<ChunkPos> toGenerate = new ArrayList<>();
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
                    toGenerate.add(new ChunkPos(cx, cz));
                }
            }
        }

        // Load the chunks
        if (!toGenerate.isEmpty()) {
            toGenerate.sort((c1, c2) -> {
                // compare by sq distance to the player
                int dx1 = c1.x - chunkX;
                int dz1 = c1.z - chunkZ;
                int dx2 = c2.x - chunkX;
                int dz2 = c2.z - chunkZ;
                int dist1 = dx1 * dx1 + dz1 * dz1;
                int dist2 = dx2 * dx2 + dz2 * dz2;
                return Integer.compare(dist1, dist2);
            });
            for (ChunkPos pos : toGenerate) {
                this.level.loadChunk(pos.x, pos.z);
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

    /**
     * Rotates the entity.
     *
     * @param yawRotation   Change in yaw rotation
     * @param pitchRotation Change in pitch rotation
     */
    public void turn(float yawRotation, float pitchRotation) {
        this.cameraYaw = (float) ((double) this.cameraYaw + (double) yawRotation * 0.15);
        this.cameraPitch = (float) ((double) this.cameraPitch + (double) pitchRotation * 0.15);

        // Clamp pitch to prevent camera flipping
        if (this.cameraPitch < -90.0F) {
            this.cameraPitch = -90.0F;
        }
        if (this.cameraPitch > 90.0F) {
            this.cameraPitch = 90.0F;
        }
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

    /**
     * Wraps an angle to the [-180,180) range.
     */
    private static float wrapDegrees(float angle) {
        angle %= 360.0F;
        if (angle >= 180.0F) angle -= 360.0F;
        if (angle < -180.0F) angle += 360.0F;
        return angle;
    }

    public boolean isThePlayer() {
        return isThePlayer;
    }
}