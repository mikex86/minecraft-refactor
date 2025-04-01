package com.mojang.minecraft.character;

/**
 * Represents the 3D model of a zombie character.
 * Consists of various cubes representing body parts that can be animated.
 */
public class ZombieModel {
    // Animation constants
    private static final float HEAD_SWING_FREQUENCY = 0.83F;
    private static final float HEAD_BOB_FREQUENCY = 1.0F;
    private static final float HEAD_BOB_AMPLITUDE = 0.8F;
    private static final float ARM_SWING_FREQUENCY = 0.6662F;
    private static final float ARM_SWING_AMPLITUDE = 2.0F;
    private static final float ARM0_ROTATE_FREQUENCY = 0.2312F;
    private static final float ARM1_ROTATE_FREQUENCY = 0.2812F;
    private static final float LEG_SWING_AMPLITUDE = 1.4F;

    // Body parts
    public Cube head = new Cube(0, 0);
    public Cube body;
    public Cube leftArm;
    public Cube rightArm;
    public Cube leftLeg;
    public Cube rightLeg;

    /**
     * Creates a new zombie model with properly positioned body parts.
     */
    public ZombieModel() {
        // Create the head (8x8x8 cube)
        this.head.addBox(-4.0F, -8.0F, -4.0F, 8, 8, 8);

        // Create the body (8x12x4 cube)
        this.body = new Cube(16, 16);
        this.body.addBox(-4.0F, 0.0F, -2.0F, 8, 12, 4);

        // Create the right arm (4x12x4 cube)
        this.rightArm = new Cube(40, 16);
        this.rightArm.addBox(-3.0F, -2.0F, -2.0F, 4, 12, 4);
        this.rightArm.setPos(-5.0F, 2.0F, 0.0F);

        // Create the left arm (4x12x4 cube)
        this.leftArm = new Cube(40, 16);
        this.leftArm.addBox(-1.0F, -2.0F, -2.0F, 4, 12, 4);
        this.leftArm.setPos(5.0F, 2.0F, 0.0F);

        // Create the right leg (4x12x4 cube)
        this.rightLeg = new Cube(0, 16);
        this.rightLeg.addBox(-2.0F, 0.0F, -2.0F, 4, 12, 4);
        this.rightLeg.setPos(-2.0F, 12.0F, 0.0F);

        // Create the left leg (4x12x4 cube)
        this.leftLeg = new Cube(0, 16);
        this.leftLeg.addBox(-2.0F, 0.0F, -2.0F, 4, 12, 4);
        this.leftLeg.setPos(2.0F, 12.0F, 0.0F);
    }

    /**
     * Renders the zombie model with animations based on the current time.
     *
     * @param time Current animation time
     */
    public void render(float time) {
        // Animate the head
        this.head.yRot = (float) Math.sin(time * HEAD_SWING_FREQUENCY);
        this.head.xRot = (float) Math.sin(time * HEAD_BOB_FREQUENCY) * HEAD_BOB_AMPLITUDE;

        // Animate the arms
        this.rightArm.xRot = (float) Math.sin(time * ARM_SWING_FREQUENCY + Math.PI) * ARM_SWING_AMPLITUDE;
        this.rightArm.zRot = (float) (Math.sin(time * ARM0_ROTATE_FREQUENCY) + 1.0F);
        this.leftArm.xRot = (float) Math.sin(time * ARM_SWING_FREQUENCY) * ARM_SWING_AMPLITUDE;
        this.leftArm.zRot = (float) (Math.sin(time * ARM1_ROTATE_FREQUENCY) - 1.0F);

        // Animate the legs
        this.rightLeg.xRot = (float) Math.sin(time * ARM_SWING_FREQUENCY) * LEG_SWING_AMPLITUDE;
        this.leftLeg.xRot = (float) Math.sin(time * ARM_SWING_FREQUENCY + Math.PI) * LEG_SWING_AMPLITUDE;

        // Render all body parts
        this.head.render();
        this.body.render();
        this.rightArm.render();
        this.leftArm.render();
        this.rightLeg.render();
        this.leftLeg.render();
    }
}
