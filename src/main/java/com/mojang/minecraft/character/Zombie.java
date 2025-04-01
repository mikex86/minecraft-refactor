package com.mojang.minecraft.character;

import com.mojang.minecraft.entity.Entity;
import com.mojang.minecraft.level.Level;
import com.mojang.minecraft.renderer.Textures;
import com.mojang.minecraft.renderer.model.Model;
import com.mojang.minecraft.renderer.model.ModelRegistry;
import com.mojang.minecraft.renderer.model.impl.ZombieModel;

import static org.lwjgl.opengl.GL11.*;

/**
 * Represents a zombie entity in the game world.
 */
public class Zombie extends Entity {
    // OpenGL constants
    private static final int GL_TEXTURE_2D = 3553;
    private static final int GL_NEAREST = 9728;

    // Movement and physics constants
    private static final float GRAVITY = 0.08F;
    private static final float AIR_DRAG = 0.91F;
    private static final float GROUND_DRAG = 0.7F;
    private static final float Y_DRAG = 0.98F;
    private static final float JUMP_CHANCE = 0.08F;
    private static final float JUMP_STRENGTH = 0.5F;
    private static final float AIR_CONTROL = 0.02F;
    private static final float GROUND_CONTROL = 0.1F;
    private static final float ROTATION_DECAY = 0.99F;
    private static final float ROTATION_RANDOM_FACTOR = 0.08F;
    private static final float Y_DEATH_THRESHOLD = -100.0F;

    // Animation constants
    private static final float SECONDS_TO_NANOS = 1.0E9F;
    private static final float MODEL_SIZE = 0.058333334F;
    private static final float MODEL_Y_OFFSET = -23.0F;
    private static final float ANIMATION_SPEED = 10.0F;
    private static final float DEGREES_TO_RADIANS = 57.29578F; // 180.0f / Math.PI

    // Entity properties
    public float rot;          // Current rotation angle
    public float timeOffs;     // Time offset for animation
    public float speed;        // Movement speed
    public float rotA;         // Rotation acceleration/velocity
    private final Textures textures; // Texture manager

    private static final Model ZOMBIE_MODEL = ModelRegistry.getInstance().getModel("zombie", ZombieModel::new);

    /**
     * Creates a new zombie entity at the specified position.
     *
     * @param level    The game level
     * @param textures The texture manager
     * @param x        X coordinate
     * @param y        Y coordinate
     * @param z        Z coordinate
     */
    public Zombie(Level level, Textures textures, float x, float y, float z) {
        super(level);
        this.textures = textures;
        this.rotA = (float) (Math.random() + 1.0F) * 0.01F;
        this.setPos(x, y, z);
        this.timeOffs = (float) Math.random() * 1239813.0F;
        this.rot = (float) (Math.random() * Math.PI * 2.0F);
        this.speed = 1.0F;
    }

    /**
     * Updates the zombie's position and animation state.
     */
    public void tick() {
        // Store previous position
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;

        float moveX = 0.0F;
        float moveZ = 0.0F;

        // Check if zombie fell out of the world
        if (this.y < Y_DEATH_THRESHOLD) {
            this.remove();
        }

        // Update rotation
        this.rot += this.rotA;
        this.rotA *= ROTATION_DECAY;
        this.rotA += (float) ((Math.random() - Math.random()) * Math.random() * Math.random() * ROTATION_RANDOM_FACTOR);

        // Calculate movement direction
        moveX = (float) Math.sin(this.rot);
        moveZ = (float) Math.cos(this.rot);

        // Random jumping
        if (this.onGround && Math.random() < JUMP_CHANCE) {
            this.yd = JUMP_STRENGTH;
        }

        // Apply movement
        this.moveRelative(moveX, moveZ, this.onGround ? GROUND_CONTROL : AIR_CONTROL);
        this.yd -= GRAVITY;
        this.move(this.xd, this.yd, this.zd);

        // Apply drag
        this.xd *= AIR_DRAG;
        this.yd *= Y_DRAG;
        this.zd *= AIR_DRAG;

        // Apply extra drag when on ground
        if (this.onGround) {
            this.xd *= GROUND_DRAG;
            this.zd *= GROUND_DRAG;
        }
    }

    /**
     * Renders the zombie entity.
     *
     * @param partialTick Partial tick time for smooth animation
     */
    public void render(float partialTick) {
        // Enable texturing
        glEnable(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, this.textures.loadTexture("/char.png", GL_NEAREST));

        glPushMatrix();

        // Calculate animation time
        double time = (double) System.nanoTime() / SECONDS_TO_NANOS * ANIMATION_SPEED * this.speed + this.timeOffs;

        // Calculate vertical bobbing
        float yOffset = (float) (-Math.abs(Math.sin(time * 0.6662)) * 5.0F + MODEL_Y_OFFSET);

        // Position at interpolated location
        glTranslatef(
                this.xo + (this.x - this.xo) * partialTick,
                this.yo + (this.y - this.yo) * partialTick,
                this.zo + (this.z - this.zo) * partialTick
        );

        // Apply scaling and orientation
        glScalef(1.0F, -1.0F, 1.0F);  // Flip model vertically
        glScalef(MODEL_SIZE, MODEL_SIZE, MODEL_SIZE);
        glTranslatef(0.0F, yOffset, 0.0F);

        // Rotate to face direction
        glRotatef(this.rot * DEGREES_TO_RADIANS + 180.0F, 0.0F, 1.0F, 0.0F);

        // Render the model
        ZOMBIE_MODEL.render((float) time);

        glPopMatrix();
        glDisable(GL_TEXTURE_2D);
    }
}
