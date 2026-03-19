package com.mojang.minecraft.renderer.model.impl;

import com.mojang.minecraft.entity.EntityPlayer;
import com.mojang.minecraft.renderer.graphics.CommandBuffer;
import com.mojang.minecraft.renderer.graphics.MatrixStack;
import com.mojang.minecraft.renderer.graphics.MutableDescriptorSet;
import com.mojang.minecraft.renderer.model.Model;
import com.mojang.minecraft.renderer.shape.Cube;

import static java.lang.Math.PI;
import static java.lang.Math.cos;

/**
 * Represents the 3D model of a player model.
 * Consists of various cubes representing body parts that can be animated.
 */
public class PlayerModel implements Model<EntityPlayer> {

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
    public PlayerModel() {
        // Create the head (8x8x8 cube)
        this.head.addBox(-4.0F, -8.0F, -4.0F, 8, 8, 8);

        // Create the body (8x12x4 cube)
        this.body = new Cube(16, 16);
        this.body.addBox(-4.0F, 0.0F, -2.0F, 8, 12, 4);

        // Create the right arm (4x12x4 cube)
        this.rightArm = new Cube(40, 16);
        this.rightArm.addBox(-3.0F, -2.0F, -2.0F, 4, 12, 4);
        this.rightArm.setPosition(-5.0F, 2.0F, 0.0F);

        // Create the left arm (4x12x4 cube)
        this.leftArm = new Cube(40, 16);
        this.leftArm.addBox(-1.0F, -2.0F, -2.0F, 4, 12, 4);
        this.leftArm.setPosition(5.0F, 2.0F, 0.0F);

        // Create the right leg (4x12x4 cube)
        this.rightLeg = new Cube(0, 16);
        this.rightLeg.addBox(-2.0F, 0.0F, -2.0F, 4, 12, 4);
        this.rightLeg.setPosition(-2.0F, 12.0F, 0.0F);

        // Create the left leg (4x12x4 cube)
        this.leftLeg = new Cube(0, 16);
        this.leftLeg.addBox(-2.0F, 0.0F, -2.0F, 4, 12, 4);
        this.leftLeg.setPosition(2.0F, 12.0F, 0.0F);
    }

    /**
     * Renders the player model
     */
    @Override
    public void render(CommandBuffer commandBuffer, MatrixStack matrixStack, MutableDescriptorSet descriptorSet, EntityPlayer player, float partialTicks) {
        float limbSwingAmount = player.prevLimbSwingAmount + (player.limbSwingAmount - player.prevLimbSwingAmount) * partialTicks;
        float limbSwing = player.limbSwing + (player.limbSwing - player.prevLimbSwing) * partialTicks;

        float headYaw = player.prevYaw + (player.yaw - player.prevYaw) * partialTicks;
        float headPitch = player.prevPitch + (player.pitch - player.prevPitch) * partialTicks;

        // Interpolate the smoothed body yaw from the player
        float bodyYaw = player.prevBodyYaw
            + (player.bodyYaw - player.prevBodyYaw) * partialTicks;

        setRotationAngles(limbSwing, limbSwingAmount, headYaw, headPitch);

        // Render all body parts
        this.head.render(commandBuffer, matrixStack, descriptorSet);

        matrixStack.rotateY(bodyYaw);
        this.body.render(commandBuffer, matrixStack, descriptorSet);
        this.rightArm.render(commandBuffer, matrixStack, descriptorSet);
        this.leftArm.render(commandBuffer, matrixStack, descriptorSet);
        this.rightLeg.render(commandBuffer, matrixStack, descriptorSet);
        this.leftLeg.render(commandBuffer, matrixStack, descriptorSet);
    }

    protected void setRotationAngles(float limbSwing, float limbSwingAmount, float netHeadYaw, float headPitch) {

        // head rotations
        this.head.yRot = (float) (netHeadYaw * (PI / 180F));
        this.head.xRot = (float) (headPitch * (PI / 180F));

        // arms swing
        this.rightArm.xRot = (float) cos(limbSwing * 0.6662F + PI) * 2.0F * limbSwingAmount * 0.5F;
        this.leftArm.xRot = (float) cos(limbSwing * 0.6662F) * 2.0F * limbSwingAmount * 0.5F;
        this.rightArm.zRot = 0.0F;
        this.leftArm.zRot = 0.0F;

        // legs swing
        this.rightLeg.xRot = (float) cos(limbSwing * 0.6662F) * 1.4F * limbSwingAmount;
        this.leftLeg.xRot = (float) cos(limbSwing * 0.6662F + PI) * 1.4F * limbSwingAmount;
        this.rightLeg.yRot = 0.0F;
        this.leftLeg.yRot = 0.0F;
    }

    /**
     * Disposes of all resources used by this model.
     * Called by the ModelRegistry when the model is no longer needed.
     */
    @Override
    public void dispose() {
        this.head.dispose();
        this.body.dispose();
        this.leftArm.dispose();
        this.rightArm.dispose();
        this.leftLeg.dispose();
        this.rightLeg.dispose();
    }
}
